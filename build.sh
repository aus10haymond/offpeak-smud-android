#!/bin/bash
# Builds a signed release APK for OffPeak (SMUD rates) without Gradle:
# aapt2 -> javac -> d8 -> zipalign -> apksigner
set -euo pipefail

SDK="$HOME/android-sdk"
BT="$SDK/build-tools/34.0.0"
PLAT="$SDK/platforms/android-35/android.jar"
PROJ="$HOME/workspace/offpeak-android"
OUT="$PROJ/build"
APK_NAME="OffPeak-SMUD-v1.2.apk"

for t in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$PLAT"; do
  [ -e "$t" ] || { echo "MISSING: $t"; exit 1; }
done
command -v javac >/dev/null || { echo "MISSING: javac"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/obj" "$OUT/dex" "$OUT/apk"

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/compiled.zip"

echo "== aapt2 link =="
"$BT/aapt2" link -o "$OUT/apk/base.apk" \
  -I "$PLAT" \
  --manifest "$PROJ/AndroidManifest.xml" \
  --min-sdk-version 26 \
   --target-sdk-version 35 \
  --java "$OUT/gen" \
  "$OUT/compiled.zip"

echo "== javac =="
find "$PROJ/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$PLAT" \
  -d "$OUT/obj" @"$OUT/sources.txt"

echo "== d8 =="
"$BT/d8" --min-api 26 --lib "$PLAT" \
  --output "$OUT/dex" \
  $(find "$OUT/obj" -name '*.class')

echo "== package dex =="
cp "$OUT/apk/base.apk" "$OUT/apk/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/apk/unsigned.apk" classes.dex)

echo "== zipalign =="
"$BT/zipalign" -f 4 "$OUT/apk/unsigned.apk" "$OUT/apk/aligned.apk"

echo "== sign =="
KS="$HOME/.android/offpeak-release.keystore"
PASS_FILE="$HOME/.android/offpeak-release.keystore.pass"
mkdir -p "$(dirname "$KS")"
if [ ! -f "$KS" ]; then
  # New key: random password stored with owner-only permissions, never in this script.
  PASS="$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 24)"
  printf '%s' "$PASS" > "$PASS_FILE"
  chmod 600 "$PASS_FILE"
  keytool -genkeypair -keystore "$KS" -alias offpeak \
    -storepass "$PASS" -keypass "$PASS" \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=OffPeak SMUD, OU=Personal, O=Personal, C=US"
elif [ ! -f "$PASS_FILE" ]; then
  # One-time migration for the key created by the v1.0 build script.
  printf '%s' "offpeak123" > "$PASS_FILE"
  chmod 600 "$PASS_FILE"
  echo "(migrated legacy keystore password out of the script)"
fi
PASS="$(cat "$PASS_FILE")"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PASS" \
  --out "$OUT/apk/$APK_NAME" "$OUT/apk/aligned.apk"

echo "== verify =="
"$BT/apksigner" verify --print-certs "$OUT/apk/$APK_NAME" | head -5
"$BT/aapt2" dump badging "$OUT/apk/$APK_NAME" | head -3
ls -la "$OUT/apk/$APK_NAME"
