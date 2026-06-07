#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BUILD_TOOLS_DIR="$SDK_DIR/build-tools/36.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"
AAPT_BIN="$BUILD_TOOLS_DIR/aapt"
D8_BIN="$BUILD_TOOLS_DIR/d8"
WORK_DIR="$ROOT_DIR/build"
OUT_DIR="$ROOT_DIR/out"
UNSIGNED_APK="$WORK_DIR/base-unsigned.apk"
FINAL_APK="$OUT_DIR/blankapk.apk"
SIGN_DIR="$WORK_DIR/signing"
KEYSTORE_PATH="$SIGN_DIR/minimal-ec.jks"
KEY_ALIAS="a"
KEY_PASS="pass123"

for file in \
  "$AAPT_BIN" \
  "$D8_BIN" \
  "$BUILD_TOOLS_DIR/apksigner" \
  "$ANDROID_JAR" \
  "$(command -v keytool)" \
  "$(command -v javac)"; do
  [[ -f "$file" ]] || {
    echo "Missing required file: $file" >&2
    exit 1
  }
done

mkdir -p "$WORK_DIR/raw" "$OUT_DIR" "$SIGN_DIR"
rm -f "$UNSIGNED_APK" "$FINAL_APK"
find "$WORK_DIR" -mindepth 1 \
  ! -path "$WORK_DIR/raw" ! -path "$WORK_DIR/raw/*" \
  ! -path "$SIGN_DIR" ! -path "$SIGN_DIR/*" \
  -exec rm -rf {} +
mkdir -p "$WORK_DIR/classes" "$WORK_DIR/dex"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$KEY_PASS" \
    -keypass "$KEY_PASS" \
    -alias "$KEY_ALIAS" \
    -keyalg EC \
    -groupname secp256r1 \
    -sigalg SHA256withECDSA \
    -validity 10000 \
    -dname "CN=A" \
    >/dev/null 2>&1
fi

"$AAPT_BIN" p \
  -f \
  -M "$ROOT_DIR/src/AndroidManifest.xml" \
  -I "$ANDROID_JAR" \
  -F "$UNSIGNED_APK" \
  "$WORK_DIR/raw"

javac \
  --release 8 \
  -d "$WORK_DIR/classes" \
  "$ROOT_DIR/src/a/b/A.java"

"$D8_BIN" \
  --release \
  --min-api 24 \
  --output "$WORK_DIR/dex" \
  "$WORK_DIR/classes/a/b/A.class"

(
  cd "$WORK_DIR/dex"
  zip -q -u "$UNSIGNED_APK" classes.dex
)

"$BUILD_TOOLS_DIR/apksigner" sign \
  --ks "$KEYSTORE_PATH" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEY_PASS" \
  --key-pass "pass:$KEY_PASS" \
  --v1-signing-enabled true \
  --v2-signing-enabled false \
  --v3-signing-enabled false \
  --v4-signing-enabled false \
  --v1-signer-name A \
  --out "$FINAL_APK" \
  "$UNSIGNED_APK"

"$BUILD_TOOLS_DIR/apksigner" verify "$FINAL_APK"
stat --printf='APK: %n\nSize: %s bytes\n' "$FINAL_APK"
