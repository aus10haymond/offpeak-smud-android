package com.offpeak.smud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 24-hour strip showing one day's SMUD pricing windows, with a "now" marker. */
public class TimelineView extends View {

    private List<SmudSchedule.Window> windows = new ArrayList<>();
    private LocalDate day;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final DateTimeFormatter hourFmt =
            DateTimeFormatter.ofPattern("ha", Locale.US);
    private final DateTimeFormatter timeFmt =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private int peakColor, midPeakColor, offPeakColor;

    public TimelineView(Context context) {
        super(context);
        init(context);
    }

    public TimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        textPaint.setTextSize(sp(10));
        textPaint.setColor(context.getColor(R.color.text2));
        nowPaint.setColor(context.getColor(R.color.timelineNow));
        nowPaint.setStrokeWidth(dp(2));
        peakColor = context.getColor(R.color.peak);
        midPeakColor = context.getColor(R.color.midPeak);
        offPeakColor = context.getColor(R.color.offPeak);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setDay(LocalDate day, List<SmudSchedule.Window> allWindows) {
        this.day = day;
        windows = new ArrayList<>();
        ZonedDateTime dayStart = ZonedDateTime.of(day, LocalTime.MIDNIGHT, SmudSchedule.ZONE);
        ZonedDateTime dayEnd = ZonedDateTime.of(day.plusDays(1), LocalTime.MIDNIGHT, SmudSchedule.ZONE);
        for (SmudSchedule.Window w : allWindows) {
            if (w.end.isAfter(dayStart) && w.start.isBefore(dayEnd)) {
                windows.add(w);
            }
        }
        setContentDescription(describeDay(day, windows));
        invalidate();
    }

    /** Screen-reader description of the day's schedule. */
    private String describeDay(LocalDate day, List<SmudSchedule.Window> wins) {
        if (wins.isEmpty()) return "No schedule data for this day.";
        StringBuilder sb = new StringBuilder("Electricity schedule. ");
        for (SmudSchedule.Window w : wins) {
            sb.append(SmudSchedule.periodLabel(w.period)).append(", ")
              .append(SmudSchedule.formatRate(w.priceTenths)).append(" per kilowatt hour, ")
              .append("from ").append(w.start.format(timeFmt))
              .append(" to ").append(w.end.format(timeFmt)).append(". ");
        }
        return sb.toString();
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private int colorFor(SmudSchedule.Period p) {
        switch (p) {
            case PEAK: return peakColor;
            case MID_PEAK: return midPeakColor;
            default: return offPeakColor;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (day == null || windows.isEmpty()) return;

        float labelH = dp(18);
        float barTop = dp(4);
        float barH = getHeight() - barTop - labelH;
        float w = getWidth();

        ZonedDateTime dayStart = ZonedDateTime.of(day, LocalTime.MIDNIGHT, SmudSchedule.ZONE);
        ZonedDateTime dayEnd = ZonedDateTime.of(day.plusDays(1), LocalTime.MIDNIGHT, SmudSchedule.ZONE);
        // Actual minutes in this local day (1380/1440/1500 around DST transitions).
        float dayMinutes = Duration.between(dayStart, dayEnd).toMinutes();

        // Window blocks.
        for (SmudSchedule.Window win : windows) {
            ZonedDateTime s = win.start.isBefore(dayStart) ? dayStart : win.start;
            ZonedDateTime e = win.end.isAfter(dayEnd) ? dayEnd : win.end;
            float x0 = w * minutesBetween(dayStart, s) / dayMinutes;
            float x1 = w * minutesBetween(dayStart, e) / dayMinutes;
            paint.setColor(colorFor(win.period));
            rect.set(x0, barTop, x1, barTop + barH);
            canvas.drawRoundRect(rect, dp(6), dp(6), paint);
        }

        // Hour ticks every 3 hours.
        textPaint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(getContext().getColor(R.color.timelineTick));
        for (int h = 0; h <= 24; h += 3) {
            ZonedDateTime tick = ZonedDateTime.of(day, LocalTime.of(h % 24, 0), SmudSchedule.ZONE);
            // On DST transition days the 2am tick may not exist; skip it.
            if (h < 24 && tick.getHour() != h) continue;
            float x = w * minutesBetween(dayStart, h == 24 ? dayEnd : tick) / dayMinutes;
            canvas.drawRect(x - dp(0.5f), barTop + barH + dp(4), x + dp(0.5f),
                    barTop + barH + dp(8), paint);
            if (h < 24) {
                String label = tick.format(hourFmt).toLowerCase(Locale.US);
                canvas.drawText(label, x, getHeight() - dp(4), textPaint);
            }
        }

        // "Now" marker.
        ZonedDateTime now = ZonedDateTime.now(SmudSchedule.ZONE);
        if (!now.toLocalDate().isBefore(day) && !now.toLocalDate().isAfter(day)) {
            float x = w * minutesBetween(dayStart, now) / dayMinutes;
            canvas.drawLine(x, barTop - dp(2), x, barTop + barH + dp(2), nowPaint);
            paint.setColor(getContext().getColor(R.color.timelineNow));
            canvas.drawCircle(x, barTop - dp(2), dp(4), paint);
        }
    }

    private static long minutesBetween(ZonedDateTime a, ZonedDateTime b) {
        return Duration.between(a, b).toMinutes();
    }
}
