package com.ofertiber.sarabutton;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "SaraButton";

    @Override
    public void onReceive(Context context, Intent intent) {
        context = AppLanguage.wrap(context);
        String action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        SharedPreferences preferences = AppPreferences.get(context);
        if (!preferences.getBoolean(AppPreferences.KEY_MONITORING, false)) {
            return;
        }
        try {
            Log.i(LOG_TAG, "Starting monitoring after " + action);
            MonitoringService.start(context);
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            context.getString(R.string.monitoring_active_background))
                    .apply();
        } catch (RuntimeException exception) {
            Log.w(LOG_TAG, "Foreground monitoring could not start after " + action,
                    exception);
            int result = BleScanManager.startPersistentScan(context);
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            BleScanManager.describeStartError(context, result))
                    .apply();
        }
    }
}
