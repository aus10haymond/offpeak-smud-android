package com.offpeak.smud;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-schedules peak alerts after a reboot (alarms don't survive restarts). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AlertReceiver.createChannel(ctx);
            AlertScheduler.scheduleNext(ctx);
        }
    }
}
