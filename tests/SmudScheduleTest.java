import com.offpeak.smud.SmudSchedule;
import java.time.*;
import java.util.List;
public class SmudScheduleTest {
    static void check(String name, boolean cond) {
        System.out.println((cond ? "PASS " : "FAIL ") + name);
        if (!cond) System.exit(1);
    }
    public static void main(String[] a) {
        ZoneId z = SmudSchedule.ZONE;
        // 1. Fri Sep 11 2026 23:05 PDT -> non-summer weekday, off-peak, 12.9c
        ZonedDateTime fri = ZonedDateTime.of(2026,9,11,23,5,0,0,z);
        List<SmudSchedule.Window> w = SmudSchedule.mergedWindows(LocalDate.of(2026,9,11),1,4);
        SmudSchedule.Window cur = SmudSchedule.currentWindow(w, fri);
        check("sep11 23:05 mid-peak (summer)", cur.period == SmudSchedule.Period.MID_PEAK);
        check("sep rate 21.4c", SmudSchedule.formatRate(cur.priceTenths).equals("21.4¢"));

        // 2. Summer weekday Wed Aug 12 2026 13:00 -> mid-peak 21.4c
        ZonedDateTime wed = ZonedDateTime.of(2026,8,12,13,0,0,0,z);
        List<SmudSchedule.Window> w2 = SmudSchedule.mergedWindows(LocalDate.of(2026,8,12),1,4);
        SmudSchedule.Window c2 = SmudSchedule.currentWindow(w2, wed);
        check("aug wed 13:00 mid-peak", c2.period == SmudSchedule.Period.MID_PEAK);
        check("mid-peak 21.4c", SmudSchedule.formatRate(c2.priceTenths).equals("21.4¢"));

        // 3. Peak boundary half-open: 17:00 peak, 20:00 not
        ZonedDateTime p1 = ZonedDateTime.of(2026,8,12,17,0,0,0,z);
        ZonedDateTime p2 = ZonedDateTime.of(2026,8,12,20,0,0,0,z);
        check("17:00 sharp is peak", SmudSchedule.currentWindow(w2,p1).period == SmudSchedule.Period.PEAK);
        check("20:00 sharp is mid-peak", SmudSchedule.currentWindow(w2,p2).period == SmudSchedule.Period.MID_PEAK);

        // 4. July 3 2026 (Friday, July 4 on Saturday) is NOT a holiday -> peak 5-8pm
        check("jul3 not holiday", SmudSchedule.holidayName(LocalDate.of(2026,7,3)) == null);
        ZonedDateTime j3 = ZonedDateTime.of(2026,7,3,18,0,0,0,z);
        List<SmudSchedule.Window> w3 = SmudSchedule.mergedWindows(LocalDate.of(2026,7,3),1,4);
        check("jul3 18:00 peak", SmudSchedule.currentWindow(w3,j3).period == SmudSchedule.Period.PEAK);

        // 5. Thanksgiving 2026-11-26 -> off-peak all day
        check("thanksgiving", "Thanksgiving Day".equals(SmudSchedule.holidayName(LocalDate.of(2026,11,26))));
        ZonedDateTime th = ZonedDateTime.of(2026,11,26,18,0,0,0,z);
        List<SmudSchedule.Window> w4 = SmudSchedule.mergedWindows(LocalDate.of(2026,11,26),1,4);
        check("thanksgiving 18:00 off-peak", SmudSchedule.currentWindow(w4,th).period == SmudSchedule.Period.OFF_PEAK);

        // 6b. Non-summer weekday: Mon Oct 12 2026 23:00 -> off-peak 12.9c
        ZonedDateTime oct = ZonedDateTime.of(2026,10,12,23,0,0,0,z);
        List<SmudSchedule.Window> w5b = SmudSchedule.mergedWindows(LocalDate.of(2026,10,12),1,2);
        SmudSchedule.Window c5b = SmudSchedule.currentWindow(w5b, oct);
        check("oct12 23:00 off-peak", c5b.period == SmudSchedule.Period.OFF_PEAK);
        check("oct rate 12.9c", SmudSchedule.formatRate(c5b.priceTenths).equals("12.9¢"));

        // 6. Weekend merge (non-summer): Fri Oct 9 20:00 -> Tue Oct 13 17:00 is ONE off-peak window
        //    (Oct 12 is Indigenous Peoples' Day, so the holiday Monday extends it to Tuesday)
        check("indigenous peoples day 2026", "Indigenous Peoples' Day".equals(SmudSchedule.holidayName(LocalDate.of(2026,10,12))));
        List<SmudSchedule.Window> w5 = SmudSchedule.mergedWindows(LocalDate.of(2026,10,10),1,4);
        boolean found = false;
        for (SmudSchedule.Window x : w5) {
            if (x.start.equals(ZonedDateTime.of(2026,10,9,20,0,0,0,z))
                && x.end.equals(ZonedDateTime.of(2026,10,13,17,0,0,0,z))
                && x.period == SmudSchedule.Period.OFF_PEAK) { found = true; break; }
        }
        check("non-summer holiday weekend merged Fri20:00-Tue17:00", found);
        // 6b. Summer weekend: Sat 00:00 -> Mon 00:00 merges (Fri 20:00-midnight stays mid-peak)
        List<SmudSchedule.Window> w5c = SmudSchedule.mergedWindows(LocalDate.of(2026,9,12),1,4);
        boolean foundC = false;
        for (SmudSchedule.Window x : w5c) {
            if (x.start.equals(ZonedDateTime.of(2026,9,12,0,0,0,0,z))
                && x.end.equals(ZonedDateTime.of(2026,9,14,12,0,0,0,z))
                && x.period == SmudSchedule.Period.OFF_PEAK) { foundC = true; break; }
        }
        check("summer weekend merged Sat00:00-Mon12:00", foundC);

        // 7. 2027 step activates by date
        ZonedDateTime y27 = ZonedDateTime.of(2027,1,4,18,0,0,0,z);
        List<SmudSchedule.Window> w6 = SmudSchedule.mergedWindows(LocalDate.of(2027,1,4),1,4);
        SmudSchedule.Window c6 = SmudSchedule.currentWindow(w6,y27);
        check("2027 winter peak 18.3c", SmudSchedule.formatRate(c6.priceTenths).equals("18.3¢"));

        // 8. Floating holidays: MLK 2026 = Jan 19, Memorial 2026 = May 25, Labor 2026 = Sep 7
        check("MLK 2026", "Martin Luther King Jr. Day".equals(SmudSchedule.holidayName(LocalDate.of(2026,1,19))));
        check("Memorial 2026", "Memorial Day".equals(SmudSchedule.holidayName(LocalDate.of(2026,5,25))));
        check("Labor 2026", "Labor Day".equals(SmudSchedule.holidayName(LocalDate.of(2026,9,7))));
        check("Christmas 2026", "Christmas Day".equals(SmudSchedule.holidayName(LocalDate.of(2026,12,25))));

        // 9. Season boundary: May 31 non-summer, Jun 1 summer
        check("may31 winter", !SmudSchedule.isSummer(LocalDate.of(2026,5,31)));
        check("jun1 summer", SmudSchedule.isSummer(LocalDate.of(2026,6,1)));

        System.out.println("ALL TESTS PASSED");
    }
}
