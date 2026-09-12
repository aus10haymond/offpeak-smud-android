package com.offpeak.smud;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when a scheduled peak alert goes off: shows the notification, then chains the next one. */
public class AlertReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "peak_alerts";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        String title = intent.getStringExtra("title");
        String text = intent.getStringExtra("text");
        if (title != null) {
            int id = AlertScheduler.ACTION_PEAK_WARNING.equals(action) ? 1 : 2;
            showNotification(ctx, id, title, text);
        }
        // Chain: schedule alarms for the following peak window.
        AlertScheduler.scheduleNext(ctx);
    }

    public static void createChannel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Peak pricing alerts", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Warns you 15 minutes before peak pricing starts, and when it ends.");
        nm.createNotificationChannel(ch);
    }

    private void showNotification(Context ctx, int id, String title, String text) {
        Intent open = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(id, n);
    }
}
