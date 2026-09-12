package com.offpeak.smud;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SMUD Residential Time-of-Day (5-8 p.m.) schedule, plan RT02.
 *
 * Data from https://github.com/nacdwk/smud-time-of-day-schedule
 * (verified against SMUD Rate Schedule R-TOD, August 2026).
 *
 * Rules honored here:
 *  - All times in America/Los_Angeles (the meter's zone, not the device zone).
 *  - Summer = Jun 1 - Sep 30; non-summer = Oct 1 - May 31.
 *  - Weekends and the 11 SMUD holidays are off-peak all day; holidays NEVER shift.
 *  - Intervals are half-open: [17:00, 20:00). 5:00:00pm sharp is peak; 8:00:00pm is not.
 *  - Times built from calendar components (never by adding seconds) for DST safety.
 *  - Rates stored as integer tenths of a mill ($/kWh * 10000); rounded in integer math.
 *  - Adjacent windows sharing period AND price are merged.
 */
public final class SmudSchedule {

    public static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");

    public enum Period { OFF_PEAK, MID_PEAK, PEAK }

    private SmudSchedule() {}

    /** One rate step. Prices are integer tenths of a mill ($/kWh * 10000). */
    public static final class Step {
        public final LocalDate effective;
        public final int summerPeak, summerMid, summerOff;
        public final int winterPeak, winterOff;
        Step(String effective, int sp, int sm, int so, int wp, int wo) {
            this.effective = LocalDate.parse(effective);
            this.summerPeak = sp; this.summerMid = sm; this.summerOff = so;
            this.winterPeak = wp; this.winterOff = wo;
        }
    }

    private static final Step[] STEPS = {
        new Step("2025-05-01", 3655, 2077, 1505, 1724, 1248),
        new Step("2026-01-01", 3765, 2139, 1550, 1776, 1285),
        // Board-approved (Resolution 25-06-15) - activates automatically by date.
        new Step("2027-01-01", 3878, 2203, 1596, 1829, 1324),
    };

    public static Step activeStep(LocalDate date) {
        Step active = STEPS[0];
        for (Step s : STEPS) {
            if (!s.effective.isAfter(date)) active = s;
        }
        return active;
    }

    public static boolean isSummer(LocalDate d) {
        int m = d.getMonthValue();
        return m >= 6 && m <= 9;
    }

    // ---- Holidays: 11, never shifted -------------------------------------

    private static LocalDate nthWeekdayOfMonth(int year, int month, DayOfWeek dow, int ordinal) {
        LocalDate d = LocalDate.of(year, month, 1);
        if (ordinal > 0) {
            return d.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, dow));
        }
        return d.with(TemporalAdjusters.lastInMonth(dow));
    }

    /** Name of the SMUD holiday on this date, or null. */
    public static String holidayName(LocalDate d) {
        int y = d.getYear(), m = d.getMonthValue(), day = d.getDayOfMonth();
        if (m == 1 && day == 1) return "New Year's Day";
        if (m == 6 && day == 19) return "Juneteenth";
        if (m == 7 && day == 4) return "Independence Day";
        if (m == 11 && day == 11) return "Veterans Day";
        if (m == 12 && day == 25) return "Christmas Day";
        if (d.equals(nthWeekdayOfMonth(y, 1, DayOfWeek.MONDAY, 3))) return "Martin Luther King Jr. Day";
        if (d.equals(nthWeekdayOfMonth(y, 2, DayOfWeek.MONDAY, 3))) return "Presidents Day";
        if (d.equals(nthWeekdayOfMonth(y, 5, DayOfWeek.MONDAY, -1))) return "Memorial Day";
        if (d.equals(nthWeekdayOfMonth(y, 9, DayOfWeek.MONDAY, 1))) return "Labor Day";
        if (d.equals(nthWeekdayOfMonth(y, 10, DayOfWeek.MONDAY, 2))) return "Indigenous Peoples' Day";
        if (d.equals(nthWeekdayOfMonth(y, 11, DayOfWeek.THURSDAY, 4))) return "Thanksgiving Day";
        return null;
    }

    public static boolean isWeekendOrHoliday(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY || holidayName(d) != null;
    }

    // ---- Daily segments (start minute -> period), half-open --------------

    /** Each entry: {startMinuteFromMidnight, periodOrdinal}. */
    private static List<int[]> segmentsFor(LocalDate d) {
        List<int[]> segs = new ArrayList<>();
        if (isWeekendOrHoliday(d)) {
            segs.add(new int[]{0, Period.OFF_PEAK.ordinal()});
            return segs;
        }
        if (isSummer(d)) {
            segs.add(new int[]{0, Period.OFF_PEAK.ordinal()});
            segs.add(new int[]{720, Period.MID_PEAK.ordinal()});   // 12:00
            segs.add(new int[]{1020, Period.PEAK.ordinal()});     // 17:00
            segs.add(new int[]{1200, Period.MID_PEAK.ordinal()});  // 20:00
        } else {
            segs.add(new int[]{0, Period.OFF_PEAK.ordinal()});
            segs.add(new int[]{1020, Period.PEAK.ordinal()});      // 17:00
            segs.add(new int[]{1200, Period.OFF_PEAK.ordinal()});  // 20:00
        }
        return segs;
    }

    public static int priceTenths(LocalDate date, Period p) {
        Step s = activeStep(date);
        boolean summer = isSummer(date);
        switch (p) {
            case PEAK: return summer ? s.summerPeak : s.winterPeak;
            case MID_PEAK: return s.summerMid; // mid-peak only exists in summer
            default: return summer ? s.summerOff : s.winterOff;
        }
    }

    // ---- Windows ----------------------------------------------------------

    public static final class Window {
        public final ZonedDateTime start;
        public final ZonedDateTime end;
        public final Period period;
        public final int priceTenths;

        Window(ZonedDateTime start, ZonedDateTime end, Period period, int priceTenths) {
            this.start = start;
            this.end = end;
            this.period = period;
            this.priceTenths = priceTenths;
        }

        public boolean contains(ZonedDateTime t) {
            return !t.isBefore(start) && t.isBefore(end); // half-open [start, end)
        }
    }

    /**
     * Merged pricing windows around a center date. Adjacent windows sharing
     * period AND price are merged, so a non-summer weekend is one window
     * from Friday 8pm to Monday 5pm.
     */
    public static List<Window> mergedWindows(LocalDate center, int daysBefore, int daysAfter) {
        List<Window> out = new ArrayList<>();
        for (int i = -daysBefore; i <= daysAfter; i++) {
            LocalDate date = center.plusDays(i);
            List<int[]> segs = segmentsFor(date);
            for (int j = 0; j < segs.size(); j++) {
                int startMin = segs.get(j)[0];
                Period p = Period.values()[segs.get(j)[1]];
                ZonedDateTime start = ZonedDateTime.of(date,
                        LocalTime.of(startMin / 60, startMin % 60), ZONE);
                ZonedDateTime end;
                if (j + 1 < segs.size()) {
                    int endMin = segs.get(j + 1)[0];
                    end = ZonedDateTime.of(date, LocalTime.of(endMin / 60, endMin % 60), ZONE);
                } else {
                    end = ZonedDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT, ZONE);
                }
                Window w = new Window(start, end, p, priceTenths(date, p));
                if (!out.isEmpty()) {
                    Window last = out.get(out.size() - 1);
                    if (last.period == w.period && last.priceTenths == w.priceTenths
                            && last.end.equals(w.start)) {
                        out.set(out.size() - 1,
                                new Window(last.start, w.end, last.period, last.priceTenths));
                        continue;
                    }
                }
                out.add(w);
            }
        }
        return out;
    }

    public static Window currentWindow(List<Window> windows, ZonedDateTime now) {
        for (Window w : windows) {
            if (w.contains(now)) return w;
        }
        return null;
    }

    // ---- Formatting (integer math only) -----------------------------------

    /** Tenths of a mill -> "37.7¢" (one decimal, half-up, no floats). */
    public static String formatRate(int tenths) {
        long x10 = (tenths * 10L + 50) / 100; // ¢/kWh * 10
        return String.format(Locale.US, "%d.%d¢", x10 / 10, x10 % 10);
    }

    public static String periodLabel(Period p) {
        switch (p) {
            case PEAK: return "Peak";
            case MID_PEAK: return "Mid-Peak";
            default: return "Off-Peak";
        }
    }
}
