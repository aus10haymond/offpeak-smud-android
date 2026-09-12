package com.offpeak.smud;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Manual theme override (System / Light / Dark) without depending on AppCompat.
 * Applies a uiMode night qualifier override in attachBaseContext so the
 * values-night resources are picked up when the user forces light or dark.
 */
public class AppTheme {

    public static final String KEY_THEME = "theme_mode";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    public static String getMode(Context ctx) {
        String m = ctx.getSharedPreferences(AlertScheduler.PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, MODE_SYSTEM);
        if (MODE_LIGHT.equals(m) || MODE_DARK.equals(m)) return m;
        return MODE_SYSTEM;
    }

    public static void setMode(Context ctx, String mode) {
        if (!MODE_LIGHT.equals(mode) && !MODE_DARK.equals(mode)) mode = MODE_SYSTEM;
        ctx.getSharedPreferences(AlertScheduler.PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, mode).apply();
    }

    /** Call from Activity.attachBaseContext before super.attachBaseContext. */
    public static Context applyOverride(Context base) {
        String mode = getMode(base);
        int night;
        if (MODE_LIGHT.equals(mode)) {
            night = Configuration.UI_MODE_NIGHT_NO;
        } else if (MODE_DARK.equals(mode)) {
            night = Configuration.UI_MODE_NIGHT_YES;
        } else {
            return base; // follow the system
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(config);
    }
}
