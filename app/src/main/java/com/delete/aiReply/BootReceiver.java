package com.delete.aiReply;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Receiver to start our service when the device boots
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "XposedModule";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed, starting service");

            // Start the persistent service
            Intent serviceIntent = new Intent(context, com.delete.aiReply.PersistentService.class);

            // For Android 8.0 (API level 26) and higher, we should use startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}