package com.ofertiber.sarabutton;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class AppPreferences {
    static final String FILE = "sara_button_preferences";
    private static final String KEY_DEVICE_STORAGE_READY = "device_storage_ready";
    private static final String KEY_MULTI_REMOTE_READY = "multi_remote_ready";
    private static final Object MIGRATION_LOCK = new Object();
    private static volatile boolean migrationChecked;
    // Kept for seamless migration from versions before 1.2.0.
    static final String KEY_PHONE = "caregiver_phone";
    private static final String KEY_BUTTON_PHONE_PREFIX = "button_phone_";
    private static final String KEY_PANEL_BUTTON_PHONE_PREFIX = "panel_button_phone_";
    static final String KEY_TOUGH_PHONE = "tough_button_phone";
    static final String KEY_PANEL_REMOTE_ADDRESS = "panel_remote_address";
    static final String KEY_PANEL_REMOTE_NAME = "panel_remote_name";
    static final String KEY_TOUGH_REMOTE_ADDRESS = "tough_remote_address";
    static final String KEY_TOUGH_REMOTE_NAME = "tough_remote_name";
    // Kept for migration from versions that supported one selected remote.
    static final String KEY_REMOTE_ADDRESS = "remote_address";
    static final String KEY_REMOTE_NAME = "remote_name";
    static final String KEY_REMOTE_BUTTON_COUNT = "remote_button_count";
    static final String KEY_TRIGGER_BUTTON = "trigger_button";
    static final String KEY_TRIGGER_EVENT = "trigger_event";
    static final String KEY_MONITORING = "monitoring_enabled";
    static final String KEY_STATUS = "status";
    static final String KEY_UI_LOCALE = "ui_locale";
    static final String KEY_LAST_ACTIVITY = "last_activity";
    static final String KEY_LAST_ACTIVITY_AT = "last_activity_at";
    static final String KEY_LAST_ACTIVITY_BUTTON = "last_activity_button";
    static final String KEY_LAST_ACTIVITY_EVENT = "last_activity_event";
    static final String KEY_LAST_ACTIVITY_REMOTE_BUTTON_COUNT =
            "last_activity_remote_button_count";
    static final String KEY_LAST_EVENT_FINGERPRINT = "last_event_fingerprint";
    static final String KEY_LAST_EVENT_AT = "last_event_at";
    static final String KEY_LAST_CALL_AT = "last_call_at";
    static final String KEY_CALL_GUARD_UNTIL = "call_guard_until";
    static final String KEY_USE_FIRST_FOR_ALL = "use_first_number_for_all_buttons";

    private AppPreferences() {
    }

    static SharedPreferences get(Context context) {
        Context appContext = context.getApplicationContext();
        Context deviceContext = storageContext(appContext);
        SharedPreferences devicePreferences = deviceContext.getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
        );
        if (isUserUnlocked(appContext)) {
            migrateCredentialPreferences(appContext, devicePreferences);
        }
        migrateMultiRemotePreferences(devicePreferences);
        return devicePreferences;
    }

    static Context storageContext(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.createDeviceProtectedStorageContext();
        }
        return context;
    }

    private static boolean isUserUnlocked(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        }
        UserManager manager = context.getSystemService(UserManager.class);
        return manager != null && manager.isUserUnlocked();
    }

    private static void migrateCredentialPreferences(
            Context context,
            SharedPreferences devicePreferences
    ) {
        if (migrationChecked
                || devicePreferences.getBoolean(KEY_DEVICE_STORAGE_READY, false)) {
            migrationChecked = true;
            return;
        }
        synchronized (MIGRATION_LOCK) {
            if (migrationChecked
                    || devicePreferences.getBoolean(KEY_DEVICE_STORAGE_READY, false)) {
                migrationChecked = true;
                return;
            }

            SharedPreferences credentialPreferences = context.getSharedPreferences(
                    FILE,
                    Context.MODE_PRIVATE
            );
            SharedPreferences.Editor editor = devicePreferences.edit();
            for (Map.Entry<String, ?> entry : credentialPreferences.getAll().entrySet()) {
                copyPreference(editor, entry.getKey(), entry.getValue());
            }
            // commit() is intentional: a boot receiver must see the complete
            // operational configuration immediately after migration.
            editor.putBoolean(KEY_DEVICE_STORAGE_READY, true).commit();
            migrationChecked = true;
        }
    }

    private static void copyPreference(
            SharedPreferences.Editor editor,
            String key,
            Object value
    ) {
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Set<?>) {
            Set<String> strings = new HashSet<>();
            for (Object item : (Set<?>) value) {
                if (item instanceof String) {
                    strings.add((String) item);
                }
            }
            editor.putStringSet(key, strings);
        }
    }

    static void migrateMultiRemotePreferences(SharedPreferences preferences) {
        if (preferences.getBoolean(KEY_MULTI_REMOTE_READY, false)) {
            return;
        }
        synchronized (MIGRATION_LOCK) {
            if (preferences.getBoolean(KEY_MULTI_REMOTE_READY, false)) {
                return;
            }
            String legacyAddress = nonNull(preferences.getString(KEY_REMOTE_ADDRESS, ""));
            String legacyName = nonNull(preferences.getString(KEY_REMOTE_NAME, ""));
            int legacyButtonCount = legacyRemoteButtonCount(preferences, legacyName);
            SharedPreferences.Editor editor = preferences.edit();

            if (!legacyAddress.isEmpty()
                    && legacyButtonCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1) {
                putIfMissing(
                        preferences,
                        editor,
                        KEY_TOUGH_REMOTE_ADDRESS,
                        legacyAddress
                );
                putIfMissing(preferences, editor, KEY_TOUGH_REMOTE_NAME, legacyName);
                putIfMissing(
                        preferences,
                        editor,
                        KEY_TOUGH_PHONE,
                        getLegacyIndividuallyAssignedPhone(preferences, 1)
                );
            } else {
                if (!legacyAddress.isEmpty()) {
                    putIfMissing(
                            preferences,
                            editor,
                            KEY_PANEL_REMOTE_ADDRESS,
                            legacyAddress
                    );
                    putIfMissing(preferences, editor, KEY_PANEL_REMOTE_NAME, legacyName);
                }
                for (int button = 1; button <= ShellyButtonDevice.BUTTON_COUNT_RC_4; button++) {
                    putIfMissing(
                            preferences,
                            editor,
                            panelButtonPhoneKey(button),
                            getLegacyIndividuallyAssignedPhone(preferences, button)
                    );
                }
            }

            // commit() is intentional so Direct Boot monitoring cannot observe a
            // partially migrated set of remote profiles.
            editor.putBoolean(KEY_MULTI_REMOTE_READY, true).commit();
        }
    }

    private static void putIfMissing(
            SharedPreferences preferences,
            SharedPreferences.Editor editor,
            String key,
            String value
    ) {
        if (!preferences.contains(key)) {
            editor.putString(key, value);
        }
    }

    private static int legacyRemoteButtonCount(
            SharedPreferences preferences,
            String legacyName
    ) {
        int savedCount = preferences.getInt(
                KEY_REMOTE_BUTTON_COUNT,
                ShellyButtonDevice.BUTTON_COUNT_UNKNOWN
        );
        if (savedCount == ShellyButtonDevice.BUTTON_COUNT_UNKNOWN) {
            savedCount = ShellyButtonDevice.identifyButtonCount(legacyName, null);
        }
        return savedCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                ? ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                : ShellyButtonDevice.BUTTON_COUNT_RC_4;
    }

    static String panelButtonPhoneKey(int buttonIndex) {
        if (buttonIndex < 1 || buttonIndex > 4) {
            throw new IllegalArgumentException("Button index must be 1 through 4");
        }
        return KEY_PANEL_BUTTON_PHONE_PREFIX + buttonIndex;
    }

    static String getPanelButtonPhone(SharedPreferences preferences, int buttonIndex) {
        if (preferences.getBoolean(KEY_USE_FIRST_FOR_ALL, false)) {
            return getIndividuallyAssignedPanelPhone(preferences, 1);
        }
        return getIndividuallyAssignedPanelPhone(preferences, buttonIndex);
    }

    static String getToughPhone(SharedPreferences preferences) {
        return nonNull(preferences.getString(KEY_TOUGH_PHONE, ""));
    }

    static String getPhoneForRemote(
            SharedPreferences preferences,
            int remoteButtonCount,
            int buttonIndex
    ) {
        if (remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1) {
            return buttonIndex == 1 ? getToughPhone(preferences) : "";
        }
        if (remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_RC_4) {
            return getPanelButtonPhone(preferences, buttonIndex);
        }
        return "";
    }

    static String getIndividuallyAssignedPanelPhone(
            SharedPreferences preferences,
            int buttonIndex
    ) {
        return nonNull(preferences.getString(panelButtonPhoneKey(buttonIndex), ""));
    }

    static String getRemoteAddress(SharedPreferences preferences, int remoteButtonCount) {
        return nonNull(preferences.getString(remoteAddressKey(remoteButtonCount), ""));
    }

    static String getRemoteName(SharedPreferences preferences, int remoteButtonCount) {
        return nonNull(preferences.getString(remoteNameKey(remoteButtonCount), ""));
    }

    static String remoteAddressKey(int remoteButtonCount) {
        if (remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1) {
            return KEY_TOUGH_REMOTE_ADDRESS;
        }
        if (remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_RC_4) {
            return KEY_PANEL_REMOTE_ADDRESS;
        }
        throw new IllegalArgumentException("Unsupported remote button count");
    }

    static String remoteNameKey(int remoteButtonCount) {
        if (remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1) {
            return KEY_TOUGH_REMOTE_NAME;
        }
        if (remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_RC_4) {
            return KEY_PANEL_REMOTE_NAME;
        }
        throw new IllegalArgumentException("Unsupported remote button count");
    }

    static boolean hasRemote(SharedPreferences preferences, int remoteButtonCount) {
        return !getRemoteAddress(preferences, remoteButtonCount).isEmpty();
    }

    static boolean hasAnyRemote(SharedPreferences preferences) {
        return hasRemote(preferences, ShellyButtonDevice.BUTTON_COUNT_RC_4)
                || hasRemote(preferences, ShellyButtonDevice.BUTTON_COUNT_TOUGH_1);
    }

    static boolean hasBothRemoteTypes(SharedPreferences preferences) {
        return hasRemote(preferences, ShellyButtonDevice.BUTTON_COUNT_RC_4)
                && hasRemote(preferences, ShellyButtonDevice.BUTTON_COUNT_TOUGH_1);
    }

    static int getRemoteButtonCountForAddress(
            SharedPreferences preferences,
            String resultAddress
    ) {
        return getRemoteButtonCountForAddress(
                getRemoteAddress(preferences, ShellyButtonDevice.BUTTON_COUNT_RC_4),
                getRemoteAddress(preferences, ShellyButtonDevice.BUTTON_COUNT_TOUGH_1),
                resultAddress
        );
    }

    static int getRemoteButtonCountForAddress(
            String panelAddress,
            String toughAddress,
            String resultAddress
    ) {
        if (resultAddress == null || resultAddress.isEmpty()) {
            return ShellyButtonDevice.BUTTON_COUNT_UNKNOWN;
        }
        if (panelAddress != null && panelAddress.equalsIgnoreCase(resultAddress)) {
            return ShellyButtonDevice.BUTTON_COUNT_RC_4;
        }
        if (toughAddress != null && toughAddress.equalsIgnoreCase(resultAddress)) {
            return ShellyButtonDevice.BUTTON_COUNT_TOUGH_1;
        }
        return ShellyButtonDevice.BUTTON_COUNT_UNKNOWN;
    }

    private static String getLegacyIndividuallyAssignedPhone(
            SharedPreferences preferences,
            int buttonIndex
    ) {
        String key = KEY_BUTTON_PHONE_PREFIX + buttonIndex;
        if (preferences.contains(key)) {
            return nonNull(preferences.getString(key, ""));
        }
        return buttonIndex == 1
                ? nonNull(preferences.getString(KEY_PHONE, ""))
                : "";
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    static String lastEventFingerprintKey(int remoteButtonCount, int buttonIndex) {
        String remote = remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                ? "tough"
                : "panel";
        return KEY_LAST_EVENT_FINGERPRINT + "_" + remote + "_button_" + buttonIndex;
    }
}
