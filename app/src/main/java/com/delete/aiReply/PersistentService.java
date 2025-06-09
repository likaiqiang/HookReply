package com.delete.aiReply;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class PersistentService extends Service {
    private BroadcastReceiver mReceiver;
    private boolean isForegroundStarted = false;
    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("PersistentService222","PersistentService created");
        if(mReceiver == null){
            mReceiver = new com.delete.aiReply.AiRequestReceiver();

            // Register for broadcasts
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.delete.aiReply.RECEIVE_PROMPT");

            ContextCompat.registerReceiver(this, mReceiver, filter,ContextCompat.RECEIVER_EXPORTED);
        }

    }

    @SuppressLint("ForegroundServiceType")
    private void startForeground() {
        // Create a notification for foreground service
        android.app.Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String CHANNEL_ID = "xposed_channel";
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Xposed Module Service",
                    android.app.NotificationManager.IMPORTANCE_LOW);

            android.app.NotificationManager notificationManager =
                    (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);

            builder = new android.app.Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(this);
        }

        android.app.Notification notification = builder
                .setContentTitle("Xposed Module AIReply")
                .setContentText("Service is running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(1, notification);
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isForegroundStarted) {
            startForeground(); // 只启动一次
            isForegroundStarted = true;
        }
        // Return START_STICKY to ensure the service restarts if it's killed
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d("PersistentService222","PersistentService destroyed, attempting to restart");

        // Unregister the receiver
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        // Try to restart the service if it gets destroyed
        Intent restartIntent = new Intent(this, PersistentService.class);
        startService(restartIntent);

        super.onDestroy();
    }
}