package com.ofertiber.sarabutton;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

final class ConfirmationSound {
    private static final String LOG_TAG = "SaraButton";
    private static final String CHANNEL_ID = "sara_button_call_confirmation_v3";
    private static final int NOTIFICATION_ID_BASE = 4200;
    private static final long SOUND_DURATION_MILLIS = 850L;
    private static final long CALL_DELAY_MILLIS = 900L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static Ringtone activeRingtone;

    private ConfirmationSound() {
    }

    static void play(Context context, int buttonIndex) {
        playAlarmSound(context.getApplicationContext());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(LOG_TAG, "Confirmation notification needs Notifications permission");
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            Log.w(LOG_TAG, "Android NotificationManager is unavailable");
            return;
        }
        createChannel(context, manager);

        Intent openIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                buttonIndex,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVibrate(new long[]{0L, 350L, 180L, 350L});
        builder.setSmallIcon(R.drawable.ic_app)
                .setContentTitle(context.getString(R.string.confirmation_title))
                .setContentText(context.getString(
                        R.string.confirmation_text,
                        buttonIndex
                ))
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setTimeoutAfter(2_500L);
        }
        Notification notification = builder.build();
        manager.notify(NOTIFICATION_ID_BASE + buttonIndex, notification);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            MAIN_HANDLER.postDelayed(
                    () -> manager.cancel(NOTIFICATION_ID_BASE + buttonIndex),
                    2_500L
            );
        }
        Log.i(LOG_TAG, "Confirmation sound requested for Button " + buttonIndex);
    }

    static void playThenCall(Context context, int buttonIndex, Runnable callAction) {
        play(context, buttonIndex);
        MAIN_HANDLER.postDelayed(callAction, CALL_DELAY_MILLIS);
    }

    private static void playAlarmSound(Context context) {
        MAIN_HANDLER.post(() -> {
            stopActiveRingtone();
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) {
                sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            Ringtone ringtone = RingtoneManager.getRingtone(context, sound);
            if (ringtone == null) {
                Log.w(LOG_TAG, "No system confirmation sound is available");
                return;
            }
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ringtone.setAudioAttributes(attributes);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.setLooping(false);
                ringtone.setVolume(1.0f);
            }
            activeRingtone = ringtone;
            ringtone.play();
            MAIN_HANDLER.postDelayed(() -> {
                if (activeRingtone == ringtone) {
                    stopActiveRingtone();
                }
            }, SOUND_DURATION_MILLIS);
        });
    }

    private static void stopActiveRingtone() {
        if (activeRingtone == null) {
            return;
        }
        try {
            if (activeRingtone.isPlaying()) {
                activeRingtone.stop();
            }
        } catch (RuntimeException exception) {
            Log.w(LOG_TAG, "Could not stop confirmation sound", exception);
        }
        activeRingtone = null;
    }

    private static void createChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
        if (existing != null) {
            existing.setName(context.getString(R.string.confirmation_channel_name));
            existing.setDescription(
                    context.getString(R.string.confirmation_channel_description)
            );
            manager.createNotificationChannel(existing);
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.confirmation_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.confirmation_channel_description));
        // Audio is played directly on USAGE_ALARM before the call. Keeping this
        // channel silent prevents a second sound on Android versions that also
        // honor the notification channel while the dialer is taking audio focus.
        channel.setSound(null, null);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0L, 350L, 180L, 350L});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }
}
