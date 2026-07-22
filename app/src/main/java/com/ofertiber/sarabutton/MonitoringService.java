package com.ofertiber.sarabutton;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.util.List;

public final class MonitoringService extends Service {
    private static final String LOG_TAG = "SaraButton";
    private static final String CHANNEL_ID = "sara_button_monitoring";
    private static final int NOTIFICATION_ID = 4104;
    private static final long RETRY_DELAY_MILLIS = 10_000L;
    // Some Android Bluetooth stacks reduce long-running low-latency scans after
    // about five minutes. Refresh both scan paths before that common timeout.
    private static final long SCAN_REFRESH_MILLIS = 4L * 60L * 1_000L;
    private static final long WAKE_LOCK_TIMEOUT_MILLIS = SCAN_REFRESH_MILLIS + 60_000L;
    private static final String ACTION_START =
            "com.ofertiber.sarabutton.action.START_MONITORING";
    private static final String ACTION_STOP =
            "com.ofertiber.sarabutton.action.STOP_MONITORING";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private boolean bluetoothReceiverRegistered;
    private PowerManager.WakeLock wakeLock;

    private final Runnable retryScan = this::startScanning;
    private final Runnable refreshScans = this::refreshScans;

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ButtonEventProcessor.processResult(
                    MonitoringService.this,
                    AppPreferences.get(MonitoringService.this),
                    result
            );
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            SharedPreferences preferences = AppPreferences.get(MonitoringService.this);
            for (ScanResult result : results) {
                ButtonEventProcessor.processResult(
                        MonitoringService.this,
                        preferences,
                        result
                );
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            AppPreferences.get(MonitoringService.this).edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedString(
                                    R.string.monitoring_retry_after_error,
                                    errorCode
                            ))
                    .apply();
            Log.w(LOG_TAG, "Foreground BLE scan failed: " + errorCode);
            scheduleRetry();
        }
    };

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            int state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
            );
            if (state == BluetoothAdapter.STATE_ON) {
                startBackgroundScan();
                startScanning();
            } else if (state == BluetoothAdapter.STATE_OFF) {
                stopScanning();
                AppPreferences.get(MonitoringService.this).edit()
                        .putString(AppPreferences.KEY_STATUS,
                                localizedString(R.string.monitoring_waiting_bluetooth))
                        .apply();
            }
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    static void start(Context context) {
        Intent intent = new Intent(context, MonitoringService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, MonitoringService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bluetoothStateReceiver, filter);
        }
        bluetoothReceiverRegistered = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopScanning();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                //noinspection deprecation
                stopForeground(true);
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        SharedPreferences preferences = AppPreferences.get(this);
        if (!preferences.getBoolean(AppPreferences.KEY_MONITORING, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        promoteToForeground();
        acquireWakeLock();
        // On Android 8.0 and newer, keep both paths active. The PendingIntent
        // scan can wake the app if a vendor pauses callbacks with the screen off.
        // Packet fingerprinting prevents the same advertisement from calling twice.
        if (!scanning && BleScanManager.supportsPersistentScan()) {
            // A PendingIntent registration can survive an app update. Reset it
            // so monitoring does not inherit a reduced scan duty cycle.
            BleScanManager.stopPersistentScan(this);
        }
        startBackgroundScan();
        startScanning();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(retryScan);
        handler.removeCallbacks(refreshScans);
        stopScanning();
        releaseWakeLock();
        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver);
            bluetoothReceiverRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void promoteToForeground() {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
        Notification notification = builder.setSmallIcon(R.drawable.ic_app)
                .setContentTitle(localizedString(R.string.monitoring_notification_title))
                .setContentText(localizedString(R.string.monitoring_notification_text))
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int serviceType = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    serviceType
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                localizedString(R.string.monitoring_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(localizedString(R.string.monitoring_notification_text));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @SuppressLint("MissingPermission")
    private void startScanning() {
        handler.removeCallbacks(retryScan);
        SharedPreferences preferences = AppPreferences.get(this);
        if (!preferences.getBoolean(AppPreferences.KEY_MONITORING, false)) {
            return;
        }
        if (scanning) {
            startBackgroundScan();
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedString(R.string.monitoring_active_background))
                    .apply();
            return;
        }
        if (!BleScanManager.hasScanPermission(this)) {
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedString(R.string.bluetooth_permission_missing))
                    .apply();
            return;
        }
        if (!BleScanManager.hasBackgroundScanPermission(this)) {
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedString(R.string.monitoring_background_permission_required))
                    .apply();
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedString(R.string.monitoring_waiting_bluetooth))
                    .apply();
            return;
        }
        acquireWakeLock();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedString(R.string.monitoring_retry_bluetooth_unavailable))
                    .apply();
            scheduleRetry();
            return;
        }
        try {
            scanner.startScan(
                    BleScanManager.persistentFilters(this),
                    BleScanManager.settings(),
                    scanCallback
            );
            scanning = true;
            scheduleScanRefresh();
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedString(R.string.monitoring_active_background))
                    .apply();
            Log.i(LOG_TAG, "Foreground BLE monitoring started");
        } catch (SecurityException | IllegalStateException exception) {
            scanning = false;
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedString(R.string.monitoring_retry_bluetooth_interruption))
                    .apply();
            Log.w(LOG_TAG, "Could not start foreground BLE scan", exception);
            scheduleRetry();
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScanning() {
        handler.removeCallbacks(retryScan);
        handler.removeCallbacks(refreshScans);
        if (scanner != null && scanning && BleScanManager.hasScanPermission(this)) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException | IllegalStateException ignored) {
                // Bluetooth may be shutting down at the same time.
            }
        }
        scanning = false;
        scanner = null;
    }

    private void scheduleRetry() {
        handler.removeCallbacks(retryScan);
        handler.removeCallbacks(refreshScans);
        handler.postDelayed(retryScan, RETRY_DELAY_MILLIS);
    }

    private void scheduleScanRefresh() {
        handler.removeCallbacks(refreshScans);
        handler.postDelayed(refreshScans, SCAN_REFRESH_MILLIS);
    }

    private void refreshScans() {
        if (!AppPreferences.get(this)
                .getBoolean(AppPreferences.KEY_MONITORING, false)) {
            return;
        }
        Log.i(LOG_TAG, "Refreshing BLE scans before the platform duty-cycle timeout");
        renewWakeLock();
        stopScanning();
        BleScanManager.stopPersistentScan(this);
        startBackgroundScan();
        startScanning();
    }

    private void startBackgroundScan() {
        if (!BleScanManager.supportsPersistentScan()) {
            return;
        }
        int result = BleScanManager.startPersistentScan(this);
        if (result != BleScanManager.START_OK) {
            Log.w(LOG_TAG, "PendingIntent BLE scan did not start: "
                    + BleScanManager.describeStartError(this, result));
        }
    }

    private String localizedString(int resource, Object... arguments) {
        Context context = AppLanguage.wrap(this);
        return arguments.length == 0
                ? context.getString(resource)
                : context.getString(resource, arguments);
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager manager = getSystemService(PowerManager.class);
        if (manager == null) {
            return;
        }
        wakeLock = manager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SaraButton:Monitoring"
        );
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS);
    }

    private void renewWakeLock() {
        releaseWakeLock();
        acquireWakeLock();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }
}
