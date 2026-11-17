# OffPeak · SMUD Rates (Android)

An unofficial Android app that shows Sacramento Municipal Utility District (SMUD)
Time-of-Day electricity rates at a glance — the current period and price, a live
countdown to the next change, a 24-hour timeline, upcoming pricing windows, and
the full rate tables.

Inspired by the iOS app
[OffPeak – SMUD Rates](https://apps.apple.com/us/app/offpeak-smud-rates/id6802120827).
Schedule data is compiled from the open spec at
[smud-time-of-day-schedule](https://github.com/nacdwk/smud-time-of-day-schedule)
(MIT). Not affiliated with or endorsed by SMUD, nor by the iOS app's author.

## Features

- Current SMUD Time-of-Day period and rate, with live countdown to the next change
- 24-hour timeline with a "now" marker (correct across DST transitions)
- Upcoming pricing windows and full summer / non-summer rate tables
- Board-approved 2027 rates activate automatically on Jan 1, 2027
- Home-screen widget with the current rate
- Peak pricing alerts — configurable 15/30/60/90-minute warning plus an "ended" note
- Light / dark / system theme toggle
- No network access, no location, no analytics, no ads — nothing leaves your device

## Install

Download the APK from the
[latest release](https://github.com/aus10haymond/offpeak-smud-android/releases/latest) and install it (Android 8.0+).
Because it's sideloaded, Android will ask you to allow "install unknown apps"
once.

## Build

No Gradle, no dependencies — just the Android SDK and a JDK:

```bash
# Needs: JDK 17+, Android SDK with platform android-35 and build-tools 34.0.0
export JAVA_HOME=<your jdk17> PATH=$JAVA_HOME/bin:$PATH
./build.sh        # -> build/apk/OffPeak-SMUD-<version>.apk
./tests/run_tests.sh   # pricing-logic unit tests (no Android needed)
```

The signing key is created on first build. Its password is stored in
`~/.android/offpeak-release.keystore.pass` (owner-only permissions) — never in
the build script, never in this repo.

## Pricing rules implemented

- SMUD Residential Time-of-Day (5–8 p.m.) plan, RT02
- Peak 5:00–8:00 p.m. weekdays (half-open interval); weekends and 11 SMUD
  holidays are off-peak all day, with no "observed" weekday shifting
- All times in America/Los_Angeles, independent of device timezone
- Rates stored as integer tenths-of-a-cent to avoid float rounding

## License

MIT — see [LICENSE](LICENSE). The SMUD tariff data itself is SMUD's public
published rate schedule.
