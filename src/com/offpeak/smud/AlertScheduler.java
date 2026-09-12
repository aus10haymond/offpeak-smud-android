package com.offpeak.smud;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Schedules exact alarms for the next peak window: a 15-minute warning and an "ended" note. */
public class AlertScheduler {

    public static final String PREFS = "offpeak_prefs";
    public static final String KEY_ALERTS = "alerts_enabled";
    public static final String KEY_LEAD = "alert_lead_minutes";
    public static final String ACTION_PEAK_WARNING = "com.offpeak.smud.PEAK_WARNING";
    public static final String ACTION_PEAK_ENDED = "com.offpeak.smud.PEAK_ENDED";

    private static final int REQ_WARN = 101;
    private static final int REQ_END = 102;
    private static final DateTimeFormatter TF =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    public static boolean alertsEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALERTS, true);
    }

    public static void setAlertsEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ALERTS, on).apply();
        if (on) scheduleNext(ctx);
        else cancel(ctx);
    }

    /** Minutes of advance warning before peak starts. One of 15, 30, 60, 90. */
    public static int leadMinutes(Context ctx) {
        int m = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_LEAD, 15);
        return (m == 30 || m == 60 || m == 90) ? m : 15;
    }

    public static void setLeadMinutes(Context ctx, int minutes) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_LEAD, minutes).apply();
        if (alertsEnabled(ctx)) scheduleNext(ctx);
    }

    /** Schedule alarms for the next upcoming peak window, cancelling any previous ones. */
    public static void scheduleNext(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        cancel(ctx);
        if (!alertsEnabled(ctx) || am == null) return;

        ZonedDateTime now = ZonedDateTime.now(SmudSchedule.ZONE);
        List<SmudSchedule.Window> windows =
                SmudSchedule.mergedWindows(now.toLocalDate(), 0, 3);
        for (SmudSchedule.Window w : windows) {
            if (w.period != SmudSchedule.Period.PEAK || !w.end.isAfter(now)) continue;

            if (w.start.isAfter(now)) {
                int lead = leadMinutes(ctx);
                ZonedDateTime warnAt = w.start.minusMinutes(lead);
                if (warnAt.isBefore(now)) warnAt = now.plusSeconds(10);
                String title = "Peak pricing starts at " + w.start.format(TF);
                String text = "Peak rate " + SmudSchedule.formatRate(w.priceTenths)
                        + "/kWh runs " + w.start.format(TF) + " – " + w.end.format(TF) + ".";
                setAlarm(ctx, am, warnAt, ACTION_PEAK_WARNING, REQ_WARN, title, text);
            }

            int i = windows.indexOf(w);
            String endText;
            if (i + 1 < windows.size()) {
                SmudSchedule.Window n = windows.get(i + 1);
                endText = "Now " + SmudSchedule.periodLabel(n.period) + " ("
                        + SmudSchedule.formatRate(n.priceTenths) + "/kWh) until "
                        + n.end.format(TF) + ".";
            } else {
                endText = "The peak period is over.";
            }
            setAlarm(ctx, am, w.end, ACTION_PEAK_ENDED, REQ_END,
                    "Peak pricing ended", endText);
            break; // only the next peak window
        }
    }

    private static void setAlarm(Context ctx, AlarmManager am, ZonedDateTime when,
                                 String action, int req, String title, String text) {
        Intent it = new Intent(ctx, AlertReceiver.class)
                .setAction(action)
                .putExtra("title", title)
                .putExtra("text", text);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, req, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long millis = when.toInstant().toEpochMilli();
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // Exact alarms not granted: fall back to inexact so the user still gets a heads-up.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi);
        }
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int[] reqs = {REQ_WARN, REQ_END};
        String[] actions = {ACTION_PEAK_WARNING, ACTION_PEAK_ENDED};
        for (int req : reqs) {
            for (String action : actions) {
                PendingIntent pi = PendingIntent.getBroadcast(ctx, req,
                        new Intent(ctx, AlertReceiver.class).setAction(action),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.cancel(pi);
                pi.cancel();
            }
        }
    }
}
