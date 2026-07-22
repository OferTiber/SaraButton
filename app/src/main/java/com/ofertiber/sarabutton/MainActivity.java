package com.ofertiber.sarabutton;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final String LOG_TAG = "SaraButton";
    private static final int REQUEST_PERMISSIONS = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;
    private static final int REQUEST_BACKGROUND_LOCATION = 1003;
    private static final int[] PRESS_EVENT_VALUES = {
            BthomeParser.EVENT_SINGLE_PRESS,
            BthomeParser.EVENT_DOUBLE_PRESS,
            BthomeParser.EVENT_LONG_PRESS
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            refreshOnboarding();
            handler.postDelayed(this, 1_000L);
        }
    };

    private enum OnboardingStep {
        PERMISSIONS,
        BACKGROUND_SCAN,
        NOTIFICATIONS,
        BATTERY,
        UNUSED_APP,
        PHONE,
        REMOTE,
        COMPLETE
    }

    private final EditText[] phoneFields = new EditText[4];
    private TextView statusText;
    private TextView remoteText;
    private TextView findHelpText;
    private TextView lastActivityText;
    private Spinner languageSpinner;
    private Spinner pressSpinner;
    private CheckBox useFirstForAllCheckBox;
    private View additionalPhoneFields;
    private Button monitoringButton;
    private View onboardingCard;
    private TextView onboardingStatusText;
    private TextView onboardingHelpText;
    private Button onboardingActionButton;

    private BluetoothLeScanner discoveryScanner;
    private Runnable afterPermissionGranted;
    private boolean findingRemote;
    private boolean activityVisible;
    private boolean loadingConfiguration;
    private String encryptedAddressShown;
    private final Set<String> discoveryAddresses = new HashSet<>();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    private final ScanCallback discoveryCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            inspectDiscoveryResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                inspectDiscoveryResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            findingRemote = false;
            showStatus(getString(
                    R.string.remote_search_failed,
                    BleScanManager.describeStartError(MainActivity.this, errorCode)
            ));
            handler.postDelayed(MainActivity.this::ensureAutomaticDiscovery, 3_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();
        clearStaleLocalizedStatus();

        phoneFields[0] = findViewById(R.id.button1PhoneField);
        phoneFields[1] = findViewById(R.id.button2PhoneField);
        phoneFields[2] = findViewById(R.id.button3PhoneField);
        phoneFields[3] = findViewById(R.id.button4PhoneField);
        statusText = findViewById(R.id.statusText);
        remoteText = findViewById(R.id.remoteText);
        findHelpText = findViewById(R.id.findHelpText);
        lastActivityText = findViewById(R.id.lastActivityText);
        languageSpinner = findViewById(R.id.languageSpinner);
        pressSpinner = findViewById(R.id.pressSpinner);
        useFirstForAllCheckBox = findViewById(R.id.useFirstForAllCheckBox);
        additionalPhoneFields = findViewById(R.id.additionalPhoneFields);
        monitoringButton = findViewById(R.id.startButton);
        onboardingCard = findViewById(R.id.onboardingCard);
        onboardingStatusText = findViewById(R.id.onboardingStatusText);
        onboardingHelpText = findViewById(R.id.onboardingHelpText);
        onboardingActionButton = findViewById(R.id.onboardingActionButton);

        configureSpinners();
        loadingConfiguration = true;
        loadConfiguration();
        loadingConfiguration = false;
        configureAutoSave();

        monitoringButton.setOnClickListener(view -> {
            boolean monitoring = AppPreferences.get(this)
                    .getBoolean(AppPreferences.KEY_MONITORING, false);
            if (monitoring) {
                stopMonitoring();
            } else if (!isOnboardingComplete()) {
                refreshOnboarding();
                advanceOnboarding();
            } else {
                startMonitoring();
            }
        });
        onboardingActionButton.setOnClickListener(view -> advanceOnboarding());
        refreshOnboarding();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityVisible = true;
        SharedPreferences preferences = AppPreferences.get(this);
        if (preferences.getBoolean(AppPreferences.KEY_MONITORING, false)
                && BleScanManager.hasScanPermission(this)
                && BleScanManager.hasBackgroundScanPermission(this)) {
            try {
                MonitoringService.start(this);
            } catch (RuntimeException exception) {
                int fallback = BleScanManager.startPersistentScan(this);
                preferences.edit()
                        .putString(AppPreferences.KEY_STATUS,
                                BleScanManager.describeStartError(this, fallback))
                        .apply();
            }
        }
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable);
        handler.post(() -> {
            refreshOnboarding();
            ensureAutomaticDiscovery();
        });
    }

    @Override
    protected void onPause() {
        activityVisible = false;
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopDiscovery();
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            refreshOnboarding();
            if (hasNotificationPermission()) {
                handler.postDelayed(this::advanceOnboarding, 250L);
            }
            return;
        }
        if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            refreshOnboarding();
            if (BleScanManager.hasBackgroundScanPermission(this)) {
                handler.postDelayed(this::advanceOnboarding, 250L);
            }
            return;
        }
        if (requestCode != REQUEST_PERMISSIONS) {
            return;
        }
        boolean granted = true;
        for (int result : grantResults) {
            granted &= result == PackageManager.PERMISSION_GRANTED;
        }
        Runnable action = afterPermissionGranted;
        afterPermissionGranted = null;
        if (granted && action != null) {
            action.run();
        } else {
            showStatus(getString(R.string.permission_required));
        }
        refreshOnboarding();
    }

    private void configureSpinners() {
        List<String> languageNames = new ArrayList<>();
        languageNames.add(getString(R.string.language_system_default));
        for (int position = 1; position < AppLanguage.getLanguageCount(); position++) {
            Locale locale = Locale.forLanguageTag(AppLanguage.getLanguageTag(position));
            String displayName = locale.getDisplayName(locale);
            if (!displayName.isEmpty()) {
                displayName = displayName.substring(0, 1).toUpperCase(locale)
                        + displayName.substring(1);
            }
            languageNames.add(displayName);
        }
        ArrayAdapter<String> languageAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                languageNames
        );
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);
        languageSpinner.setSelection(AppLanguage.getSelectedLanguagePosition(this));
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedTag = AppLanguage.getLanguageTag(position);
                String currentTag = AppLanguage.getSelectedLanguageTag(MainActivity.this);
                if (selectedTag.equalsIgnoreCase(currentTag)) {
                    return;
                }
                AppLanguage.setSelectedLanguageTag(MainActivity.this, selectedTag);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    recreate();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ArrayAdapter<CharSequence> pressAdapter = ArrayAdapter.createFromResource(
                this, R.array.press_types, android.R.layout.simple_spinner_item
        );
        pressAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pressSpinner.setAdapter(pressAdapter);
    }

    private void applySystemBarInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
            );
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        content.requestApplyInsets();
    }

    private void clearStaleLocalizedStatus() {
        SharedPreferences preferences = AppPreferences.get(this);
        String currentLocale = AppLanguage.currentLocale(
                getResources().getConfiguration()
        ).toLanguageTag();
        String previousLocale = preferences.getString(AppPreferences.KEY_UI_LOCALE, "");
        SharedPreferences.Editor editor = preferences.edit()
                .putString(AppPreferences.KEY_UI_LOCALE, currentLocale);
        if (previousLocale != null
                && !previousLocale.isEmpty()
                && !previousLocale.equals(currentLocale)) {
            editor.remove(AppPreferences.KEY_STATUS);
        }
        editor.apply();
    }

    private void loadConfiguration() {
        SharedPreferences preferences = AppPreferences.get(this);
        for (int index = 0; index < phoneFields.length; index++) {
            phoneFields[index].setText(
                    AppPreferences.getIndividuallyAssignedPhone(preferences, index + 1)
            );
        }
        boolean useFirstForAll = preferences.getBoolean(
                AppPreferences.KEY_USE_FIRST_FOR_ALL,
                false
        );
        useFirstForAllCheckBox.setChecked(useFirstForAll);
        updateAdditionalPhoneVisibility(useFirstForAll);
        int triggerEvent = preferences.getInt(
                AppPreferences.KEY_TRIGGER_EVENT,
                BthomeParser.EVENT_SINGLE_PRESS
        );
        int eventPosition = 0;
        for (int index = 0; index < PRESS_EVENT_VALUES.length; index++) {
            if (PRESS_EVENT_VALUES[index] == triggerEvent) {
                eventPosition = index;
                break;
            }
        }
        pressSpinner.setSelection(eventPosition);
        refreshStatus();
    }

    private void configureAutoSave() {
        for (int index = 0; index < phoneFields.length; index++) {
            final int buttonIndex = index + 1;
            phoneFields[index].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable text) {
                    if (!loadingConfiguration) {
                        persistPhone(buttonIndex, text.toString());
                    }
                }
            });
        }

        useFirstForAllCheckBox.setOnCheckedChangeListener((button, checked) -> {
            updateAdditionalPhoneVisibility(checked);
            if (!loadingConfiguration) {
                AppPreferences.get(this).edit()
                        .putBoolean(AppPreferences.KEY_USE_FIRST_FOR_ALL, checked)
                        .apply();
            }
        });

        pressSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!loadingConfiguration && position >= 0
                        && position < PRESS_EVENT_VALUES.length) {
                    AppPreferences.get(MainActivity.this).edit()
                            .putInt(AppPreferences.KEY_TRIGGER_EVENT,
                                    PRESS_EVENT_VALUES[position])
                            .apply();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void persistPhone(int buttonIndex, String value) {
        String phone = value == null ? "" : value.trim();
        SharedPreferences.Editor editor = AppPreferences.get(this).edit()
                .putString(AppPreferences.buttonPhoneKey(buttonIndex), phone);
        if (buttonIndex == 1) {
            editor.putString(AppPreferences.KEY_PHONE, phone);
        }
        editor.apply();
    }

    private void updateAdditionalPhoneVisibility(boolean useFirstForAll) {
        boolean singleButtonRemote = hasConfiguredRemote()
                && AppPreferences.getRemoteButtonCount(AppPreferences.get(this))
                == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1;
        useFirstForAllCheckBox.setVisibility(singleButtonRemote ? View.GONE : View.VISIBLE);
        additionalPhoneFields.setVisibility(
                singleButtonRemote || useFirstForAll ? View.GONE : View.VISIBLE
        );
    }

    private void ensureAutomaticDiscovery() {
        if (!activityVisible || findingRemote) {
            return;
        }
        String address = AppPreferences.get(this)
                .getString(AppPreferences.KEY_REMOTE_ADDRESS, "");
        if (address != null && !address.isEmpty()) {
            return;
        }
        if (!BleScanManager.hasScanPermission(this)) {
            refreshOnboarding();
            return;
        }
        startDiscovery();
    }

    private void withPermissions(
            boolean includeBluetooth,
            boolean includePhone,
            Runnable action
    ) {
        List<String> missing = new ArrayList<>();
        if (includeBluetooth) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
                addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                addIfMissing(missing, Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }
        if (includePhone) {
            addIfMissing(missing, Manifest.permission.CALL_PHONE);
            addIfMissing(missing, Manifest.permission.READ_PHONE_STATE);
        }
        if (missing.isEmpty()) {
            action.run();
            return;
        }
        afterPermissionGranted = action;
        requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    @SuppressLint("MissingPermission")
    private void startDiscovery() {
        if (!BleScanManager.isBluetoothEnabled(this)) {
            showStatus(getString(R.string.turn_on_bluetooth));
            try {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } catch (RuntimeException ignored) {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        discoveryScanner = adapter == null ? null : adapter.getBluetoothLeScanner();
        if (discoveryScanner == null) {
            showStatus(getString(R.string.bluetooth_unavailable));
            return;
        }
        stopDiscovery();
        discoveryScanner = adapter.getBluetoothLeScanner();
        findingRemote = true;
        encryptedAddressShown = null;
        discoveryAddresses.clear();
        findHelpText.setText(R.string.searching_remote_help);
        showStatus(getString(R.string.searching_remote));
        discoveryScanner.startScan(
                Collections.emptyList(),
                BleScanManager.settings(),
                discoveryCallback
        );
    }

    @SuppressLint("MissingPermission")
    private void inspectDiscoveryResult(ScanResult result) {
        if (!findingRemote || result == null || result.getScanRecord() == null) {
            return;
        }
        ScanRecord record = result.getScanRecord();
        byte[] serviceData = record.getServiceData(BleScanManager.BTHOME_UUID);
        BthomeParser.Packet packet = BthomeParser.parse(serviceData);
        String advertisedName = record.getDeviceName();
        String address;
        try {
            address = result.getDevice().getAddress();
        } catch (SecurityException exception) {
            showStatus(getString(R.string.bluetooth_permission_revoked));
            return;
        }
        discoveryAddresses.add(address);
        if (advertisedName == null || advertisedName.trim().isEmpty()) {
            try {
                advertisedName = result.getDevice().getName();
            } catch (SecurityException ignored) {
                // The scan record name is still sufficient when Android exposes it.
            }
        }
        String normalizedName = advertisedName == null
                ? ""
                : advertisedName.trim().toUpperCase(Locale.ROOT);
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "BLE result address=" + address
                    + " name=" + normalizedName
                    + " hasBthome=" + (serviceData != null)
                    + " bthomeBytes=" + (serviceData == null ? 0 : serviceData.length));
        }
        int remoteButtonCount = ShellyButtonDevice.identifyButtonCount(
                normalizedName,
                packet
        );

        if (packet.isEncrypted()) {
            if (remoteButtonCount != ShellyButtonDevice.BUTTON_COUNT_UNKNOWN
                    && !address.equals(encryptedAddressShown)) {
                encryptedAddressShown = address;
                showEncryptedRemoteDialog(remoteButtonCount);
            }
            return;
        }
        if (remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_UNKNOWN) {
            int deviceCount = discoveryAddresses.size();
            showStatus(getResources().getQuantityString(
                    R.plurals.scanning_count,
                    deviceCount,
                    deviceCount
            ));
            return;
        }

        String name = advertisedName;
        if (name == null || name.trim().isEmpty()) {
            name = getString(R.string.panel_default_name);
        }
        AppPreferences.get(this).edit()
                .putString(AppPreferences.KEY_REMOTE_ADDRESS, address)
                .putString(AppPreferences.KEY_REMOTE_NAME, name)
                .putInt(AppPreferences.KEY_REMOTE_BUTTON_COUNT, remoteButtonCount)
                .putString(AppPreferences.KEY_STATUS, getString(R.string.panel_found))
                .apply();
        stopDiscovery();
        updateAdditionalPhoneVisibility(useFirstForAllCheckBox.isChecked());
        refreshStatus();
        refreshOnboarding();
        Toast.makeText(this, R.string.panel_found_toast, Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("MissingPermission")
    private void stopDiscovery() {
        if (discoveryScanner != null && findingRemote && BleScanManager.hasScanPermission(this)) {
            try {
                discoveryScanner.stopScan(discoveryCallback);
            } catch (SecurityException ignored) {
                // Permission can be revoked from Settings while this screen is open.
            }
        }
        findingRemote = false;
        if (findHelpText != null) {
            String address = AppPreferences.get(this)
                    .getString(AppPreferences.KEY_REMOTE_ADDRESS, "");
            findHelpText.setText(address == null || address.isEmpty()
                    ? R.string.find_remote_help
                    : R.string.remote_found_help);
        }
    }

    private void showEncryptedRemoteDialog(int remoteButtonCount) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.encrypted_panel_title)
                .setMessage(remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                        ? R.string.encrypted_tough_panel_message
                        : R.string.encrypted_panel_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void startMonitoring() {
        if (!savePhones()) {
            return;
        }
        SharedPreferences preferences = AppPreferences.get(this);
        String address = preferences.getString(AppPreferences.KEY_REMOTE_ADDRESS, "");
        if (address == null || address.isEmpty()) {
            showStatus(getString(R.string.missing_remote));
            return;
        }
        preferences.edit()
                .putInt(AppPreferences.KEY_TRIGGER_EVENT,
                        PRESS_EVENT_VALUES[pressSpinner.getSelectedItemPosition()])
                .putBoolean(AppPreferences.KEY_MONITORING, true)
                .apply();
        BleScanManager.stopPersistentScan(this);
        String message;
        try {
            MonitoringService.start(this);
            message = getString(R.string.monitoring_active_background);
        } catch (RuntimeException exception) {
            int result = BleScanManager.startPersistentScan(this);
            message = BleScanManager.describeStartError(this, result);
        }
        preferences.edit().putString(AppPreferences.KEY_STATUS, message).apply();
        refreshStatus();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private boolean savePhones() {
        SharedPreferences.Editor editor = AppPreferences.get(this).edit();
        boolean anyAssigned = false;
        int remoteButtonCount = hasConfiguredRemote()
                ? AppPreferences.getRemoteButtonCount(AppPreferences.get(this))
                : phoneFields.length;
        int visibleFieldCount = remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                || useFirstForAllCheckBox.isChecked()
                ? 1
                : phoneFields.length;
        for (int index = 0; index < visibleFieldCount; index++) {
            EditText field = phoneFields[index];
            String raw = field.getText().toString().trim();
            if (raw.isEmpty()) {
                editor.putString(AppPreferences.buttonPhoneKey(index + 1), "");
                if (index == 0) {
                    editor.putString(AppPreferences.KEY_PHONE, "");
                }
                continue;
            }

            String phone = PhoneNumberUtils.normalizeNumber(raw);
            int digits = phone.replaceAll("[^0-9]", "").length();
            if (digits < 5 || !PhoneNumberUtils.isGlobalPhoneNumber(phone)) {
                field.setError(getString(R.string.invalid_phone));
                field.requestFocus();
                return false;
            }
            field.setText(phone);
            editor.putString(AppPreferences.buttonPhoneKey(index + 1), phone);
            if (index == 0) {
                // Retain the old value for downgrade compatibility.
                editor.putString(AppPreferences.KEY_PHONE, phone);
            }
            anyAssigned = true;
        }
        if (!anyAssigned) {
            phoneFields[0].setError(getString(R.string.missing_phone_assignment));
            phoneFields[0].requestFocus();
            return false;
        }
        editor.apply();
        return true;
    }

    private void stopMonitoring() {
        AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.KEY_MONITORING, false)
                .putString(AppPreferences.KEY_STATUS, getString(R.string.monitoring_stopped))
                .apply();
        MonitoringService.stop(this);
        BleScanManager.stopPersistentScan(this);
        refreshStatus();
    }

    private boolean requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
            return true;
        }
        return false;
    }

    private boolean hasRequiredRuntimePermissions() {
        return BleScanManager.hasScanPermission(this)
                && BleScanManager.hasCallPermission(this)
                && checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean requiresBackgroundScanPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R;
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBatteryOptimizationExemption() {
        PowerManager manager = getSystemService(PowerManager.class);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean hasUnusedAppExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true;
        }
        try {
            return getPackageManager().isAutoRevokeWhitelisted();
        } catch (RuntimeException exception) {
            Log.w(LOG_TAG, "Unable to read unused-app restriction state", exception);
            return false;
        }
    }

    private boolean hasConfiguredPhone() {
        SharedPreferences preferences = AppPreferences.get(this);
        int buttonCount = hasConfiguredRemote()
                ? AppPreferences.getRemoteButtonCount(preferences)
                : phoneFields.length;
        for (int button = 1; button <= buttonCount; button++) {
            String raw = AppPreferences.getButtonPhone(preferences, button);
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String phone = PhoneNumberUtils.normalizeNumber(raw);
            int digits = phone.replaceAll("[^0-9]", "").length();
            if (digits >= 5 && PhoneNumberUtils.isGlobalPhoneNumber(phone)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConfiguredRemote() {
        String address = AppPreferences.get(this)
                .getString(AppPreferences.KEY_REMOTE_ADDRESS, "");
        return address != null && !address.isEmpty();
    }

    private OnboardingStep currentOnboardingStep() {
        if (!hasRequiredRuntimePermissions()) {
            return OnboardingStep.PERMISSIONS;
        }
        if (!BleScanManager.hasBackgroundScanPermission(this)) {
            return OnboardingStep.BACKGROUND_SCAN;
        }
        if (!hasNotificationPermission()) {
            return OnboardingStep.NOTIFICATIONS;
        }
        if (!hasBatteryOptimizationExemption()) {
            return OnboardingStep.BATTERY;
        }
        if (!hasUnusedAppExemption()) {
            return OnboardingStep.UNUSED_APP;
        }
        if (!hasConfiguredPhone()) {
            return OnboardingStep.PHONE;
        }
        if (!hasConfiguredRemote()) {
            return OnboardingStep.REMOTE;
        }
        return OnboardingStep.COMPLETE;
    }

    private boolean isOnboardingComplete() {
        return currentOnboardingStep() == OnboardingStep.COMPLETE;
    }

    private String onboardingStatusLine(boolean ready, int labelResource) {
        return getString(
                ready ? R.string.onboarding_item_ready : R.string.onboarding_item_pending,
                getString(labelResource)
        );
    }

    private void refreshOnboarding() {
        if (onboardingCard == null) {
            return;
        }
        OnboardingStep step = currentOnboardingStep();
        if (step == OnboardingStep.COMPLETE) {
            onboardingCard.setVisibility(View.GONE);
            return;
        }

        onboardingCard.setVisibility(View.VISIBLE);
        onboardingActionButton.setVisibility(View.VISIBLE);
        onboardingActionButton.setEnabled(true);
        String status = onboardingStatusLine(
                hasRequiredRuntimePermissions(), R.string.onboarding_permissions
        );
        if (requiresBackgroundScanPermission()) {
            status += "\n" + onboardingStatusLine(
                    BleScanManager.hasBackgroundScanPermission(this),
                    R.string.onboarding_background_scan
            );
        }
        status += "\n" + onboardingStatusLine(
                hasNotificationPermission(), R.string.onboarding_notifications
        ) + "\n" + onboardingStatusLine(
                hasBatteryOptimizationExemption(), R.string.onboarding_battery
        ) + "\n" + onboardingStatusLine(
                hasUnusedAppExemption(), R.string.onboarding_unused_app
        ) + "\n" + onboardingStatusLine(
                hasConfiguredPhone(), R.string.onboarding_phone
        ) + "\n" + onboardingStatusLine(
                hasConfiguredRemote(), R.string.onboarding_remote
        );
        onboardingStatusText.setText(status);

        switch (step) {
            case PERMISSIONS:
                onboardingHelpText.setText(R.string.onboarding_permissions_help);
                onboardingActionButton.setText(R.string.onboarding_grant_permissions);
                break;
            case BACKGROUND_SCAN:
                onboardingHelpText.setText(R.string.onboarding_background_scan_help);
                onboardingActionButton.setText(R.string.onboarding_allow_background_scan);
                break;
            case NOTIFICATIONS:
                onboardingHelpText.setText(R.string.onboarding_notifications_help);
                onboardingActionButton.setText(R.string.onboarding_enable_notifications);
                break;
            case BATTERY:
                onboardingHelpText.setText(R.string.onboarding_battery_help);
                onboardingActionButton.setText(R.string.onboarding_allow_background);
                break;
            case UNUSED_APP:
                onboardingHelpText.setText(R.string.onboarding_unused_app_help);
                onboardingActionButton.setText(R.string.onboarding_open_unused_settings);
                break;
            case PHONE:
                onboardingHelpText.setText(R.string.onboarding_phone_help);
                onboardingActionButton.setText(R.string.onboarding_enter_phone);
                break;
            case REMOTE:
                onboardingHelpText.setText(R.string.onboarding_remote_help);
                onboardingActionButton.setVisibility(View.GONE);
                ensureAutomaticDiscovery();
                break;
            case COMPLETE:
                break;
        }
    }

    private void advanceOnboarding() {
        switch (currentOnboardingStep()) {
            case PERMISSIONS:
                withPermissions(true, true, () -> {
                    refreshOnboarding();
                    handler.postDelayed(this::advanceOnboarding, 250L);
                });
                break;
            case BACKGROUND_SCAN:
                requestBackgroundScanPermission();
                break;
            case NOTIFICATIONS:
                requestNotificationPermissionIfNeeded();
                break;
            case BATTERY:
                requestBatteryOptimizationExemptionIfNeeded();
                break;
            case UNUSED_APP:
                openUnusedAppSettings();
                break;
            case PHONE:
                phoneFields[0].requestFocus();
                phoneFields[0].post(() -> {
                    InputMethodManager keyboard = getSystemService(InputMethodManager.class);
                    if (keyboard != null) {
                        keyboard.showSoftInput(phoneFields[0], InputMethodManager.SHOW_IMPLICIT);
                    }
                });
                break;
            case REMOTE:
                ensureAutomaticDiscovery();
                break;
            case COMPLETE:
                refreshOnboarding();
                break;
        }
    }

    private void openUnusedAppSettings() {
        Uri packageUri = Uri.parse("package:" + getPackageName());
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri));
        } catch (RuntimeException exception) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void requestBackgroundScanPermission() {
        if (!requiresBackgroundScanPermission()
                || BleScanManager.hasBackgroundScanPermission(this)) {
            refreshOnboarding();
            return;
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_BACKGROUND_LOCATION
            );
            return;
        }
        openAppDetailsSettings();
    }

    private void openAppDetailsSettings() {
        Uri packageUri = Uri.parse("package:" + getPackageName());
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri));
        } catch (RuntimeException exception) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void requestBatteryOptimizationExemptionIfNeeded() {
        PowerManager manager = getSystemService(PowerManager.class);
        if (manager == null || manager.isIgnoringBatteryOptimizations(getPackageName())) {
            return;
        }
        Uri packageUri = Uri.parse("package:" + getPackageName());
        try {
            startActivity(new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    packageUri
            ));
        } catch (RuntimeException exception) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void refreshStatus() {
        SharedPreferences preferences = AppPreferences.get(this);
        boolean monitoring = preferences.getBoolean(AppPreferences.KEY_MONITORING, false);
        String status = preferences.getString(AppPreferences.KEY_STATUS, "");
        if (status == null || status.isEmpty()) {
            status = monitoring
                    ? getString(R.string.monitoring_active)
                    : getString(R.string.status_not_configured);
        }
        status = localizeLegacyStatus(status);
        statusText.setText(getString(R.string.status_format, monitoring ? "●" : "○", status));
        statusText.setTextColor(getColor(monitoring ? R.color.green_dark : R.color.ink));
        monitoringButton.setText(monitoring
                ? R.string.stop_monitoring
                : R.string.start_monitoring);
        monitoringButton.setBackgroundTintList(ColorStateList.valueOf(
                getColor(monitoring ? R.color.red : R.color.green)
        ));

        String remoteAddress = preferences.getString(AppPreferences.KEY_REMOTE_ADDRESS, "");
        String remoteName = preferences.getString(AppPreferences.KEY_REMOTE_NAME, "");
        if (remoteAddress == null || remoteAddress.isEmpty()) {
            remoteText.setText(R.string.remote_not_selected);
        } else {
            String displayName = remoteName == null || remoteName.isEmpty()
                    ? getString(R.string.panel_default_name)
                    : remoteName;
            remoteText.setText(getString(R.string.remote_format, displayName, remoteAddress));
        }
        if (findHelpText != null) {
            findHelpText.setText(findingRemote
                    ? R.string.searching_remote_help
                    : (remoteAddress == null || remoteAddress.isEmpty()
                    ? R.string.find_remote_help
                    : R.string.remote_found_help));
        }

        long lastActivityAt = preferences.getLong(AppPreferences.KEY_LAST_ACTIVITY_AT, 0L);
        int lastButton = preferences.getInt(AppPreferences.KEY_LAST_ACTIVITY_BUTTON, 0);
        int lastEvent = preferences.getInt(AppPreferences.KEY_LAST_ACTIVITY_EVENT, -1);
        if (lastActivityAt > 0L && lastButton >= 1 && lastButton <= 4 && lastEvent >= 0) {
            String date = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.MEDIUM
            ).format(new Date(lastActivityAt));
            lastActivityText.setText(getString(
                    R.string.last_activity_format,
                    date,
                    lastButton,
                    eventName(lastEvent)
            ));
        } else {
            String lastActivity = preferences.getString(AppPreferences.KEY_LAST_ACTIVITY, "");
            lastActivityText.setText(lastActivity == null || lastActivity.isEmpty()
                    ? getString(R.string.no_activity)
                    : localizeLegacyActivity(lastActivity));
        }
    }

    private String localizeLegacyStatus(String status) {
        if ("Monitoring is active".equals(status)) {
            return getString(R.string.monitoring_active);
        }
        if ("Monitoring is active in the background".equals(status)) {
            return getString(R.string.monitoring_active_background);
        }
        if ("Monitoring is stopped".equals(status)) {
            return getString(R.string.monitoring_stopped);
        }
        if ("Remote found — save to start monitoring".equals(status)) {
            return getString(R.string.panel_found);
        }
        return status;
    }

    private String localizeLegacyActivity(String activity) {
        return activity
                .replace(" — Button ", getString(R.string.last_activity_button_separator))
                .replace(" — כפתור ", getString(R.string.last_activity_button_separator))
                .replace("triple long press", getString(R.string.press_triple_long))
                .replace("שלוש לחיצות ארוכות", getString(R.string.press_triple_long))
                .replace("double long press", getString(R.string.press_double_long))
                .replace("שתי לחיצות ארוכות", getString(R.string.press_double_long))
                .replace("single press", getString(R.string.press_single))
                .replace("לחיצה אחת", getString(R.string.press_single))
                .replace("double press", getString(R.string.press_double))
                .replace("לחיצה כפולה", getString(R.string.press_double))
                .replace("triple press", getString(R.string.press_triple))
                .replace("לחיצה משולשת", getString(R.string.press_triple))
                .replace("long press", getString(R.string.press_long))
                .replace("לחיצה ארוכה", getString(R.string.press_long))
                .replace("hold", getString(R.string.event_hold))
                .replace("החזקה", getString(R.string.event_hold))
                .replace("released", getString(R.string.event_released))
                .replace("שחרור", getString(R.string.event_released));
    }

    private String eventName(int eventType) {
        int resource = BthomeParser.eventNameResource(eventType);
        if (resource != 0) {
            return getString(resource);
        }
        return getString(R.string.event_unknown, Integer.toHexString(eventType));
    }

    private void showStatus(String message) {
        AppPreferences.get(this).edit().putString(AppPreferences.KEY_STATUS, message).apply();
        refreshStatus();
    }
}
