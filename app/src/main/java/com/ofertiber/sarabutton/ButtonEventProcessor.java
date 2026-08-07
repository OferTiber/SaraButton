package com.ofertiber.sarabutton;

import android.annotation.SuppressLint;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

final class ButtonEventProcessor {
    private static final String LOG_TAG = "SaraButton";
    private static final long CALL_GUARD_MILLIS = 10_000L;

    private ButtonEventProcessor() {
    }

    @SuppressLint("MissingPermission")
    static void processResult(
            Context context,
            SharedPreferences preferences,
            ScanResult result
    ) {
        Context localizedContext = AppLanguage.wrap(context);
        if (result == null) {
            return;
        }

        String resultAddress;
        try {
            resultAddress = result.getDevice().getAddress();
        } catch (SecurityException exception) {
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedContext.getString(R.string.bluetooth_permission_revoked))
                    .apply();
            return;
        }
        int remoteButtonCount = AppPreferences.getRemoteButtonCountForAddress(
                preferences,
                resultAddress
        );
        if (remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_UNKNOWN) {
            return;
        }

        ScanRecord record = result.getScanRecord();
        if (record == null) {
            return;
        }
        byte[] serviceData = record.getServiceData(BleScanManager.BTHOME_UUID);
        BthomeParser.Packet packet = BthomeParser.parse(serviceData);
        if (packet.isEncrypted()) {
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedContext.getString(
                                    remoteButtonCount
                                            == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                                            ? R.string.encrypted_tough_status
                                            : R.string.encrypted_panel_status
                            ))
                    .apply();
            return;
        }
        if (!packet.isValidV2()) {
            return;
        }

        int triggerEvent = preferences.getInt(
                AppPreferences.KEY_TRIGGER_EVENT,
                BthomeParser.EVENT_SINGLE_PRESS
        );

        for (BthomeParser.ButtonEvent event : packet.getButtonEvents()) {
            if (!event.isActive()
                    || event.getButtonIndex() < 1
                    || event.getButtonIndex() > remoteButtonCount) {
                continue;
            }
            long activityAt = System.currentTimeMillis();
            int eventNameResource = BthomeParser.eventNameResource(event.getEventType());
            String eventName = eventNameResource == 0
                    ? localizedContext.getString(
                            R.string.event_unknown,
                            Integer.toHexString(event.getEventType())
                    )
                    : localizedContext.getString(eventNameResource);
            String activity = new SimpleDateFormat(
                    "dd/MM/yyyy HH:mm:ss",
                    Locale.getDefault()
            ).format(new Date(activityAt));
            activity = localizedContext.getString(
                    R.string.last_activity_format,
                    activity,
                    event.getButtonIndex(),
                    eventName
            );
            preferences.edit()
                    .putString(AppPreferences.KEY_LAST_ACTIVITY, activity)
                    .putLong(AppPreferences.KEY_LAST_ACTIVITY_AT, activityAt)
                    .putInt(AppPreferences.KEY_LAST_ACTIVITY_BUTTON, event.getButtonIndex())
                    .putInt(AppPreferences.KEY_LAST_ACTIVITY_EVENT, event.getEventType())
                    .putInt(
                            AppPreferences.KEY_LAST_ACTIVITY_REMOTE_BUTTON_COUNT,
                            remoteButtonCount
                    )
                    .apply();

            if (event.getEventType() != triggerEvent) {
                continue;
            }

            String assignedPhone = AppPreferences.getPhoneForRemote(
                    preferences,
                    remoteButtonCount,
                    event.getButtonIndex()
            );
            if (assignedPhone.isEmpty()) {
                continue;
            }

            String fingerprint = resultAddress.toUpperCase(Locale.ROOT)
                    + ":" + Arrays.hashCode(serviceData);
            String fingerprintKey = AppPreferences.lastEventFingerprintKey(
                    remoteButtonCount,
                    event.getButtonIndex()
            );
            String lastFingerprint = preferences.getString(
                    fingerprintKey,
                    ""
            );
            // A Shelly press is advertised repeatedly with the same BTHome packet.
            // Keep ignoring that exact packet until a new packet ID/data hash arrives,
            // even if the advertising burst lasts longer than a time window.
            if (fingerprint.equals(lastFingerprint)) {
                continue;
            }

            long now = System.currentTimeMillis();
            preferences.edit()
                    .putString(fingerprintKey, fingerprint)
                    .putString(AppPreferences.KEY_LAST_EVENT_FINGERPRINT, fingerprint)
                    .putLong(AppPreferences.KEY_LAST_EVENT_AT, now)
                    .apply();
            long callGuardUntil = preferences.getLong(
                    AppPreferences.KEY_CALL_GUARD_UNTIL,
                    0L
            );
            if (now < callGuardUntil) {
                Log.i(LOG_TAG, "Ignored repeated "
                        + remoteDescription(remoteButtonCount)
                        + " Button " + event.getButtonIndex()
                        + " press while a call is pending or active");
                continue;
            }
            preferences.edit()
                    .putLong(AppPreferences.KEY_CALL_GUARD_UNTIL,
                            now + CALL_GUARD_MILLIS)
                    .apply();
            int buttonIndex = event.getButtonIndex();
            int eventType = event.getEventType();
            ConfirmationSound.playThenCall(localizedContext, buttonIndex, () -> {
                if (!preferences.getBoolean(AppPreferences.KEY_MONITORING, false)) {
                    return;
                }
                CaregiverCaller.Result call = CaregiverCaller.placeCall(
                        localizedContext,
                        assignedPhone,
                        false
                );
                Log.i(LOG_TAG, remoteDescription(remoteButtonCount)
                        + " Button " + buttonIndex + " "
                        + BthomeParser.eventName(eventType)
                        + "; callStarted=" + call.callStarted);
            });
        }
    }

    private static String remoteDescription(int remoteButtonCount) {
        return remoteButtonCount == ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                ? "Tough"
                : "panel";
    }
}
