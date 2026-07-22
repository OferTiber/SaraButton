package com.ofertiber.sarabutton;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;

import java.util.Collections;
import java.util.List;

final class BleScanManager {
    static final String ACTION_SCAN_RESULT = "com.ofertiber.sarabutton.action.BLE_SCAN_RESULT";
    static final ParcelUuid BTHOME_UUID = ParcelUuid.fromString("0000fcd2-0000-1000-8000-00805f9b34fb");
    static final int START_OK = 0;
    static final int ERROR_NO_PERMISSION = -100;
    static final int ERROR_BLUETOOTH_OFF = -101;
    static final int ERROR_NO_SCANNER = -102;
    static final int ERROR_SECURITY = -103;
    static final int ERROR_FEATURE_UNSUPPORTED = -104;

    private static final int SCAN_REQUEST_CODE = 4104;

    private BleScanManager() {
    }

    static boolean hasScanPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        String locationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? Manifest.permission.ACCESS_FINE_LOCATION
                : Manifest.permission.ACCESS_COARSE_LOCATION;
        return context.checkSelfPermission(locationPermission) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasBackgroundScanPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean supportsPersistentScan() {
        return supportsPersistentScan(Build.VERSION.SDK_INT);
    }

    static boolean supportsPersistentScan(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.O;
    }

    static boolean hasCallPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    static boolean isBluetoothEnabled(Context context) {
        if (!hasScanPermission(context)) {
            return false;
        }
        BluetoothAdapter adapter = getAdapter(context);
        try {
            return adapter != null && adapter.isEnabled();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.O)
    static int startPersistentScan(Context context) {
        if (!supportsPersistentScan()) {
            return ERROR_FEATURE_UNSUPPORTED;
        }
        if (!hasScanPermission(context)) {
            return ERROR_NO_PERMISSION;
        }
        BluetoothAdapter adapter = getAdapter(context);
        if (adapter == null || !adapter.isEnabled()) {
            return ERROR_BLUETOOTH_OFF;
        }
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            return ERROR_NO_SCANNER;
        }
        try {
            int result = scanner.startScan(
                    persistentFilters(context),
                    persistentSettings(),
                    scanPendingIntent(context)
            );
            // Re-registering the same PendingIntent is harmless and means monitoring is active.
            return result == ScanCallback.SCAN_FAILED_ALREADY_STARTED ? START_OK : result;
        } catch (SecurityException exception) {
            return ERROR_SECURITY;
        }
    }

    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.O)
    static void stopPersistentScan(Context context) {
        if (!supportsPersistentScan()) {
            return;
        }
        if (!hasScanPermission(context)) {
            return;
        }
        BluetoothAdapter adapter = getAdapter(context);
        if (adapter == null) {
            return;
        }
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            return;
        }
        try {
            scanner.stopScan(scanPendingIntent(context));
        } catch (SecurityException ignored) {
            // Permission may have been revoked while monitoring was active.
        }
    }

    static List<ScanFilter> persistentFilters(Context context) {
        String address = AppPreferences.get(context)
                .getString(AppPreferences.KEY_REMOTE_ADDRESS, "");
        if (address != null && BluetoothAdapter.checkBluetoothAddress(address)) {
            // The original RC Button 4 uses a public, stable BLE address. Filtering
            // on it avoids vendor-specific differences in how service data is exposed.
            return Collections.singletonList(
                    new ScanFilter.Builder().setDeviceAddress(address).build()
            );
        }
        return Collections.singletonList(
                new ScanFilter.Builder().setServiceData(BTHOME_UUID, new byte[0]).build()
        );
    }

    static ScanSettings settings() {
        return new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setReportDelay(0)
                .build();
    }

    private static ScanSettings persistentSettings() {
        return new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(
                        ScanSettings.CALLBACK_TYPE_FIRST_MATCH
                                | ScanSettings.CALLBACK_TYPE_MATCH_LOST
                )
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setReportDelay(0)
                .build();
    }

    static String describeStartError(Context context, int code) {
        switch (code) {
            case START_OK:
                return context.getString(R.string.monitoring_active);
            case ERROR_NO_PERMISSION:
                return context.getString(R.string.bluetooth_permission_missing);
            case ERROR_BLUETOOTH_OFF:
                return context.getString(R.string.bluetooth_off);
            case ERROR_NO_SCANNER:
                return context.getString(R.string.bluetooth_unavailable);
            case ERROR_SECURITY:
                return context.getString(R.string.bluetooth_scan_blocked);
            case ERROR_FEATURE_UNSUPPORTED:
                return context.getString(R.string.bluetooth_scan_unsupported);
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return context.getString(R.string.bluetooth_scan_registration_failed);
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return context.getString(R.string.bluetooth_scan_internal_error);
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return context.getString(R.string.bluetooth_scan_unsupported);
            case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                return context.getString(R.string.bluetooth_scan_no_resources);
            case ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY:
                return context.getString(R.string.bluetooth_scan_throttled);
            default:
                return context.getString(R.string.bluetooth_scan_failed_code, code);
        }
    }

    private static BluetoothAdapter getAdapter(Context context) {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        return manager == null ? null : manager.getAdapter();
    }

    private static PendingIntent scanPendingIntent(Context context) {
        Intent intent = new Intent(context, BleScanReceiver.class).setAction(ACTION_SCAN_RESULT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, SCAN_REQUEST_CODE, intent, flags);
    }
}
