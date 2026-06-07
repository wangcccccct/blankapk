#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BUILD_TOOLS_DIR="$SDK_DIR/build-tools/36.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"
WORK_DIR="$ROOT_DIR/template-build"
OUT_APK="$ROOT_DIR/app/src/main/assets/template-base-unsigned.apk"

mkdir -p "$WORK_DIR/raw" "$WORK_DIR/classes" "$WORK_DIR/dex" "$(dirname "$OUT_APK")"
rm -f "$WORK_DIR/base.apk" "$OUT_APK"
find "$WORK_DIR" -mindepth 1 \
  ! -path "$WORK_DIR/raw" ! -path "$WORK_DIR/raw/*" \
  -exec rm -rf {} +
mkdir -p "$WORK_DIR/raw" "$WORK_DIR/classes" "$WORK_DIR/dex"

"$BUILD_TOOLS_DIR/aapt" p \
  -f \
  -M "$ROOT_DIR/template-src/AndroidManifest.xml" \
  -I "$ANDROID_JAR" \
  -F "$WORK_DIR/base.apk" \
  "$WORK_DIR/raw"

javac \
  --release 8 \
  -d "$WORK_DIR/classes" \
  "$ROOT_DIR/template-src/a/b/A.java"

"$BUILD_TOOLS_DIR/d8" \
  --release \
  --min-api 24 \
  --output "$WORK_DIR/dex" \
  "$WORK_DIR/classes/a/b/A.class"

(
  cd "$WORK_DIR/dex"
  zip -q -u "$WORK_DIR/base.apk" classes.dex
)

cp "$WORK_DIR/base.apk" "$OUT_APK"
stat --printf='Template asset: %n\nSize: %s bytes\n' "$OUT_APK"
