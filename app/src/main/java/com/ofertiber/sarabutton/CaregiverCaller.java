package com.ofertiber.sarabutton;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;

final class CaregiverCaller {
    // Packet-level deduplication is handled before this method. This short guard
    // only protects against two genuinely different events arriving almost at once.
    private static final long CALL_COOLDOWN_MILLIS = 20_000L;

    private CaregiverCaller() {
    }

    static Result placeCall(Context context, String phone, boolean ignoreCooldown) {
        SharedPreferences preferences = AppPreferences.get(context);
        int digits = phone == null ? 0 : phone.replaceAll("[^0-9]", "").length();
        if (phone == null || digits < 5 || !PhoneNumberUtils.isGlobalPhoneNumber(phone)) {
            return fail(preferences, context.getString(R.string.call_missing_phone));
        }
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            return fail(preferences, context.getString(R.string.call_permission_missing));
        }

        long now = System.currentTimeMillis();
        long lastCall = preferences.getLong(AppPreferences.KEY_LAST_CALL_AT, 0L);
        long elapsed = now - lastCall;
        if (!ignoreCooldown && elapsed >= 0L && elapsed < CALL_COOLDOWN_MILLIS) {
            return new Result(false, context.getString(R.string.call_recently_started));
        }

        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        if (telecomManager == null) {
            return fail(preferences, context.getString(R.string.call_service_unavailable));
        }
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                if (telecomManager.isInCall()) {
                    return new Result(false, context.getString(R.string.call_already_active));
                }
            } catch (SecurityException ignored) {
                // The persisted press guard below still prevents queued calls.
            }
        }

        preferences.edit()
                .putLong(AppPreferences.KEY_LAST_CALL_AT, now)
                .putString(AppPreferences.KEY_STATUS,
                        context.getString(R.string.call_outgoing))
                .apply();
        try {
            Bundle extras = new Bundle();
            extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
            telecomManager.placeCall(Uri.fromParts("tel", phone, null), extras);
            return new Result(true, context.getString(R.string.call_outgoing));
        } catch (RuntimeException exception) {
            preferences.edit().putLong(AppPreferences.KEY_LAST_CALL_AT, 0L).apply();
            String detail = exception.getMessage();
            return fail(preferences, detail == null
                    ? context.getString(R.string.call_failed)
                    : context.getString(R.string.call_failed_detail, detail));
        }
    }

    private static Result fail(SharedPreferences preferences, String message) {
        preferences.edit().putString(AppPreferences.KEY_STATUS, message).apply();
        return new Result(false, message);
    }

    static final class Result {
        final boolean callStarted;
        final String message;

        Result(boolean callStarted, String message) {
            this.callStarted = callStarted;
            this.message = message;
        }
    }
}
