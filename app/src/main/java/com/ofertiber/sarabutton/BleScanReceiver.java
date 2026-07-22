package com.ofertiber.sarabutton;

import android.annotation.TargetApi;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.O)
public final class BleScanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !BleScanManager.ACTION_SCAN_RESULT.equals(intent.getAction())) {
            return;
        }
        Context localizedContext = AppLanguage.wrap(context);
        SharedPreferences preferences = AppPreferences.get(localizedContext);
        if (!preferences.getBoolean(AppPreferences.KEY_MONITORING, false)) {
            return;
        }

        int errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0);
        if (errorCode != 0) {
            preferences.edit()
                    .putString(AppPreferences.KEY_STATUS,
                            localizedContext.getString(
                                    R.string.monitoring_scan_error,
                                    errorCode
                            ))
                    .apply();
            return;
        }

        PendingResult pendingResult = goAsync();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            for (ScanResult result : getResults(intent)) {
                ButtonEventProcessor.processResult(localizedContext, preferences, result);
            }
            // ButtonEventProcessor delays the call briefly for the confirmation
            // sound. Keep this background broadcast alive until that work runs.
            handler.postDelayed(pendingResult::finish, 2_000L);
        });
    }

    @SuppressWarnings("deprecation")
    private static List<ScanResult> getResults(Intent intent) {
        ArrayList<ScanResult> results;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results = intent.getParcelableArrayListExtra(
                    BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                    ScanResult.class
            );
        } else {
            results = intent.getParcelableArrayListExtra(
                    BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
            );
        }
        return results == null ? new ArrayList<>() : results;
    }
}
