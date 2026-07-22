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
    private static final Object MIGRATION_LOCK = new Object();
    private static volatile boolean migrationChecked;
    // Kept for seamless migration from versions before 1.2.0.
    static final String KEY_PHONE = "caregiver_phone";
    private static final String KEY_BUTTON_PHONE_PREFIX = "button_phone_";
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

    static String buttonPhoneKey(int buttonIndex) {
        if (buttonIndex < 1 || buttonIndex > 4) {
            throw new IllegalArgumentException("Button index must be 1 through 4");
        }
        return KEY_BUTTON_PHONE_PREFIX + buttonIndex;
    }

    static String getButtonPhone(SharedPreferences preferences, int buttonIndex) {
        if (preferences.getBoolean(KEY_USE_FIRST_FOR_ALL, false)) {
            return getIndividuallyAssignedPhone(preferences, 1);
        }
        return getIndividuallyAssignedPhone(preferences, buttonIndex);
    }

    static int getRemoteButtonCount(SharedPreferences preferences) {
        int savedCount = preferences.getInt(
                KEY_REMOTE_BUTTON_COUNT,
                ShellyButtonDevice.BUTTON_COUNT_UNKNOWN
        );
        if (savedCount == ShellyButtonDevice.BUTTON_COUNT_UNKNOWN) {
            String remoteName = preferences.getString(KEY_REMOTE_NAME, "");
            savedCount = ShellyButtonDevice.identifyButtonCount(remoteName, null);
        }
        return savedCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                ? ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                : ShellyButtonDevice.BUTTON_COUNT_RC_4;
    }

    static String getIndividuallyAssignedPhone(
            SharedPreferences preferences,
            int buttonIndex
    ) {
        String key = buttonPhoneKey(buttonIndex);
        if (preferences.contains(key)) {
            String assigned = preferences.getString(key, "");
            return assigned == null ? "" : assigned;
        }
        if (buttonIndex == 1) {
            String legacy = preferences.getString(KEY_PHONE, "");
            return legacy == null ? "" : legacy;
        }
        return "";
    }

    static String lastEventFingerprintKey(int buttonIndex) {
        return KEY_LAST_EVENT_FINGERPRINT + "_button_" + buttonIndex;
    }
}
