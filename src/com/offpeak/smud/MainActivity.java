package com.offpeak.smud;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_POST_NOTIFICATIONS = 42;

    private TextView periodName, rateNow, nextChange, countdown, todayLabel, holidayNote;
    private TextView ratesTitle, ratesNote, alertLabel;
    private LinearLayout upcomingList, ratesTable;
    private TimelineView timeline;
    private Switch alertSwitch;
    private RadioGroup leadGroup, themeGroup;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocalDate builtFor;
    private boolean pendingAlertEnable;
    private final DateTimeFormatter timeFmt =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            LocalDate today = LocalDate.now(SmudSchedule.ZONE);
            if (!today.equals(builtFor)) buildStatic(today);
            updateNow();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppTheme.applyOverride(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        periodName = findViewById(R.id.periodName);
        rateNow = findViewById(R.id.rateNow);
        nextChange = findViewById(R.id.nextChange);
        countdown = findViewById(R.id.countdown);
        todayLabel = findViewById(R.id.todayLabel);
        holidayNote = findViewById(R.id.holidayNote);
        upcomingList = findViewById(R.id.upcomingList);
        ratesTable = findViewById(R.id.ratesTable);
        ratesTitle = findViewById(R.id.ratesTitle);
        ratesNote = findViewById(R.id.ratesNote);
        timeline = findViewById(R.id.timeline);
        alertSwitch = findViewById(R.id.alertSwitch);
        alertLabel = findViewById(R.id.alertLabel);
        leadGroup = findViewById(R.id.leadGroup);
        themeGroup = findViewById(R.id.themeGroup);

        // The countdown duplicates nextChange for screen readers; keep it out of the
        // accessibility tree so it doesn't chatter every second.
        countdown.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        // Peak alerts: channel, toggle, lead-time picker, initial scheduling.
        AlertReceiver.createChannel(this);
        alertSwitch.setChecked(AlertScheduler.alertsEnabled(this));
        alertSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                onAlertToggle(checked);
            }
        });
        checkLeadButton(AlertScheduler.leadMinutes(this));
        updateAlertLabel();
        setLeadGroupEnabled(alertSwitch.isChecked());
        leadGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                int minutes = 15;
                if (checkedId == R.id.lead30) minutes = 30;
                else if (checkedId == R.id.lead60) minutes = 60;
                else if (checkedId == R.id.lead90) minutes = 90;
                AlertScheduler.setLeadMinutes(MainActivity.this, minutes);
                updateAlertLabel();
            }
        });
        if (AlertScheduler.alertsEnabled(this)) {
            ensureNotificationPermissionThen(new Runnable() {
                @Override public void run() { AlertScheduler.scheduleNext(MainActivity.this); }
            });
        }

        // Appearance: System / Light / Dark.
        String mode = AppTheme.getMode(this);
        themeGroup.check(AppTheme.MODE_LIGHT.equals(mode) ? R.id.themeLight
                : AppTheme.MODE_DARK.equals(mode) ? R.id.themeDark : R.id.themeSystem);
        themeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                String m = AppTheme.MODE_SYSTEM;
                if (checkedId == R.id.themeLight) m = AppTheme.MODE_LIGHT;
                else if (checkedId == R.id.themeDark) m = AppTheme.MODE_DARK;
                if (!m.equals(AppTheme.getMode(MainActivity.this))) {
                    AppTheme.setMode(MainActivity.this, m);
                    recreate();
                }
            }
        });

        LocalDate today = LocalDate.now(SmudSchedule.ZONE);
        buildStatic(today);
        updateNow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    // ---------- Peak alerts ----------

    private void onAlertToggle(boolean checked) {
        setLeadGroupEnabled(checked);
        if (checked) {
            ensureNotificationPermissionThen(new Runnable() {
                @Override public void run() {
                    AlertScheduler.setAlertsEnabled(MainActivity.this, true);
                    promptExactAlarmIfNeeded();
                }
            });
        } else {
            AlertScheduler.setAlertsEnabled(this, false);
        }
    }

    private void checkLeadButton(int minutes) {
        int id = R.id.lead15;
        if (minutes == 30) id = R.id.lead30;
        else if (minutes == 60) id = R.id.lead60;
        else if (minutes == 90) id = R.id.lead90;
        leadGroup.check(id);
    }

    private void updateAlertLabel() {
        alertLabel.setText("Warn me " + AlertScheduler.leadMinutes(this)
                + " minutes before peak pricing starts");
    }

    private void setLeadGroupEnabled(boolean enabled) {
        for (int i = 0; i < leadGroup.getChildCount(); i++) {
            leadGroup.getChildAt(i).setEnabled(enabled);
        }
    }

    private void ensureNotificationPermissionThen(Runnable after) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            pendingAlertEnable = true;
            pendingAfterPermission = after;
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_POST_NOTIFICATIONS);
        } else {
            after.run();
        }
    }

    private Runnable pendingAfterPermission;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingAlertEnable && pendingAfterPermission != null) {
                pendingAfterPermission.run();
            } else {
                // Permission denied: revert the toggle.
                pendingAlertEnable = false;
                alertSwitch.setOnCheckedChangeListener(null);
                alertSwitch.setChecked(false);
                alertSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                        onAlertToggle(c);
                    }
                });
                AlertScheduler.setAlertsEnabled(this, false);
            }
            pendingAfterPermission = null;
        }
    }

    /** On Android 12+, exact alarms need a separate user grant in Settings. */
    private void promptExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }

    // ---------- Display ----------

    private int colorFor(SmudSchedule.Period p) {
        switch (p) {
            case PEAK: return getColor(R.color.peak);
            case MID_PEAK: return getColor(R.color.midPeak);
            default: return getColor(R.color.offPeak);
        }
    }

    private void updateNow() {
        ZonedDateTime now = ZonedDateTime.now(SmudSchedule.ZONE);
        LocalDate today = now.toLocalDate();
        List<SmudSchedule.Window> windows = SmudSchedule.mergedWindows(today, 1, 4);
        SmudSchedule.Window cur = SmudSchedule.currentWindow(windows, now);
        if (cur == null) return;

        periodName.setText(SmudSchedule.periodLabel(cur.period));
        periodName.setTextColor(colorFor(cur.period));
        rateNow.setText(SmudSchedule.formatRate(cur.priceTenths) + " per kWh");

        int idx = windows.indexOf(cur);
        SmudSchedule.Window next = (idx + 1 < windows.size()) ? windows.get(idx + 1) : null;
        if (next != null) {
            nextChange.setText(SmudSchedule.periodLabel(cur.period) + " until "
                    + next.start.format(timeFmt) + " → then "
                    + SmudSchedule.periodLabel(next.period) + " ("
                    + SmudSchedule.formatRate(next.priceTenths) + "/kWh)");
            Duration left = Duration.between(now, next.start);
            countdown.setText(formatDuration(left) + " left");
        } else {
            nextChange.setText("");
            countdown.setText("");
        }
        timeline.invalidate();
    }

    private static String formatDuration(Duration d) {
        long s = Math.max(0, d.getSeconds());
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%dh %02dm %02ds", h, m, sec);
        return String.format(Locale.US, "%dm %02ds", m, sec);
    }

    private void buildStatic(LocalDate today) {
        builtFor = today;
        List<SmudSchedule.Window> windows = SmudSchedule.mergedWindows(today, 1, 4);

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US);
        todayLabel.setText("Today · " + today.format(dayFmt));

        String hol = SmudSchedule.holidayName(today);
        if (hol != null) {
            holidayNote.setVisibility(View.VISIBLE);
            holidayNote.setText("🎉 " + hol + " — off-peak all day");
        } else {
            holidayNote.setVisibility(View.GONE);
        }

        timeline.setDay(today, windows);

        // Upcoming changes.
        upcomingList.removeAllViews();
        ZonedDateTime now = ZonedDateTime.now(SmudSchedule.ZONE);
        SmudSchedule.Window cur = SmudSchedule.currentWindow(windows, now);
        int startIdx = cur == null ? 0 : windows.indexOf(cur);
        for (int i = startIdx; i < Math.min(startIdx + 5, windows.size()); i++) {
            upcomingList.addView(makeUpcomingRow(windows.get(i), i == startIdx));
        }

        // Rates table for the active step.
        ratesTable.removeAllViews();
        boolean summer = SmudSchedule.isSummer(today);
        SmudSchedule.Step step = SmudSchedule.activeStep(today);
        ratesTitle.setText("Rates · effective "
                + step.effective.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)));
        addRateSection("Summer (Jun–Sep)", summer, new String[][]{
                {"Peak", SmudSchedule.formatRate(step.summerPeak)},
                {"Mid-Peak", SmudSchedule.formatRate(step.summerMid)},
                {"Off-Peak", SmudSchedule.formatRate(step.summerOff)},
        });
        addRateSection("Non-summer (Oct–May)", !summer, new String[][]{
                {"Peak", SmudSchedule.formatRate(step.winterPeak)},
                {"Off-Peak", SmudSchedule.formatRate(step.winterOff)},
        });
        ratesNote.setText("Board-approved 2027 rates activate automatically on Jan 1, 2027. "
                + "Fixed monthly charge and EV overnight credit don't affect windows.");
    }

    private LinearLayout makeUpcomingRow(SmudSchedule.Window w, boolean isCurrent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(10);
        row.setPadding(pad, dp(8), pad, dp(8));
        if (isCurrent) {
            row.setBackgroundColor(getColor(R.color.rowHighlight));
        }

        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotLp.setMarginEnd(dp(10));
        dot.setLayoutParams(dotLp);
        dot.setBackgroundColor(colorFor(w.period));
        row.addView(dot);

        TextView left = new TextView(this);
        left.setText(SmudSchedule.periodLabel(w.period)
                + (isCurrent ? " · now" : "")
                + "  ·  " + SmudSchedule.formatRate(w.priceTenths) + "/kWh");
        left.setTextSize(15);
        left.setTextColor(getColor(R.color.text));
        if (isCurrent) left.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams leftLp =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.setLayoutParams(leftLp);
        row.addView(left);

        TextView right = new TextView(this);
        String range;
        LocalDate sDay = w.start.toLocalDate(), eDay = w.end.toLocalDate();
        if (sDay.equals(eDay)) {
            range = w.start.format(timeFmt) + " – " + w.end.format(timeFmt);
        } else {
            DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US);
            range = w.start.format(dFmt) + " – " + w.end.format(dFmt);
        }
        right.setText(range);
        right.setTextSize(13);
        right.setTextColor(getColor(R.color.text2));
        row.addView(right);
        return row;
    }

    private void addRateSection(String title, boolean isCurrentSeason, String[][] rows) {
        TextView h = new TextView(this);
        h.setText(title + (isCurrentSeason ? "  ● current" : ""));
        h.setTextSize(14);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(isCurrentSeason ? getColor(R.color.accent) : getColor(R.color.text));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        h.setLayoutParams(lp);
        ratesTable.addView(h);
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView k = new TextView(this);
            k.setText(r[0]);
            k.setTextSize(14);
            k.setTextColor(getColor(R.color.text2));
            k.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView v = new TextView(this);
            v.setText(r[1] + "/kWh");
            v.setTextSize(14);
            v.setTypeface(Typeface.DEFAULT_BOLD);
            v.setTextColor(getColor(R.color.text));
            row.addView(k);
            row.addView(v);
            ratesTable.addView(row);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
