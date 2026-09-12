package com.offpeak.smud;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Home-screen widget: current SMUD period, rate, and next change. */
public class OffPeakWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            mgr.updateAppWidget(id, buildViews(context));
        }
    }

    static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_offpeak);
        ZonedDateTime now = ZonedDateTime.now(SmudSchedule.ZONE);
        LocalDate today = now.toLocalDate();
        List<SmudSchedule.Window> windows = SmudSchedule.mergedWindows(today, 0, 2);
        SmudSchedule.Window cur = SmudSchedule.currentWindow(windows, now);

        if (cur != null) {
            int color;
            switch (cur.period) {
                case PEAK: color = context.getColor(R.color.peak); break;
                case MID_PEAK: color = context.getColor(R.color.midPeak); break;
                default: color = context.getColor(R.color.offPeak); break;
            }
            views.setTextViewText(R.id.widgetPeriod, SmudSchedule.periodLabel(cur.period));
            views.setTextColor(R.id.widgetPeriod, color);
            views.setTextViewText(R.id.widgetRate,
                    SmudSchedule.formatRate(cur.priceTenths) + " per kWh");

            int idx = windows.indexOf(cur);
            if (idx + 1 < windows.size()) {
                SmudSchedule.Window next = windows.get(idx + 1);
                views.setTextViewText(R.id.widgetNext,
                        "→ " + SmudSchedule.periodLabel(next.period) + " at "
                        + next.start.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)));
            } else {
                views.setTextViewText(R.id.widgetNext, "");
            }
        }

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, pi);
        return views;
    }
}
