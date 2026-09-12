#!/bin/bash
# Runs the SmudSchedule unit tests. SmudSchedule has no Android dependencies,
# so it compiles with plain javac (no android.jar needed).
set -e
PROJ="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$PROJ/tests/out"
rm -rf "$OUT"; mkdir -p "$OUT"
"${JAVA_HOME:-$HOME/jdk17}/bin/javac" -encoding UTF-8 -d "$OUT" \
  "$PROJ/src/com/offpeak/smud/SmudSchedule.java" \
  "$PROJ/tests/SmudScheduleTest.java"
"${JAVA_HOME:-$HOME/jdk17}/bin/java" -cp "$OUT" SmudScheduleTest
