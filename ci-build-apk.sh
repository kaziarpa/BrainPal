#!/usr/bin/env bash
set -euo pipefail

echo "=== [1/4] Configuring Android SDK & Accepting Licenses ==="
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ -d "$ANDROID_HOME" ]; then
  sudo chmod -R a+rwx "$ANDROID_HOME" || true
  mkdir -p "$ANDROID_HOME/licenses"
  cat << 'EOF' > "$ANDROID_HOME/licenses/android-sdk-license"
8933bad161af4178b1185d1a37fbf41ea5269c55
d56f5187479451eabf01fb78af6dfcb131a6481e
24333f8a63b6825ea9c5514f83c2829b004d1fee
859f317696f67ef3d7f30a50a5560e7834b43903
EOF
  cat << 'EOF' > "$ANDROID_HOME/licenses/android-sdk-preview-license"
84831b9409646a918e30573bab4c9c91346d8abd
504667f4c0de7af1a06de9f4b1727b84351f2910
EOF
fi

echo "=== [2/4] Ensuring Signing Keystores Exist ==="
if [ ! -f "debug.keystore" ]; then
  echo "Generating debug.keystore..."
  keytool -genkeypair -v \
    -keystore debug.keystore \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

if [ ! -f "my-upload-key.jks" ]; then
  echo "Generating my-upload-key.jks..."
  keytool -genkeypair -v \
    -keystore my-upload-key.jks \
    -storepass android \
    -alias upload \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Likhon Release,O=Likhon,C=US"
fi

echo "=== [3/4] Building Debug & Release APKs with Gradle ==="
export STORE_PASSWORD=android
export KEY_PASSWORD=android

gradle assembleDebug assembleRelease --no-daemon --stacktrace

echo "=== [4/4] Collecting APKs into dist/ ==="
mkdir -p dist

DEBUG_APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1 || true)
RELEASE_APK=$(find app/build/outputs/apk/release -name "*.apk" | head -n 1 || true)

if [ -n "$DEBUG_APK" ]; then
  cp "$DEBUG_APK" "dist/Likhon-debug.apk"
  echo "Copied $DEBUG_APK -> dist/Likhon-debug.apk"
fi

if [ -n "$RELEASE_APK" ]; then
  cp "$RELEASE_APK" "dist/Likhon-release.apk"
  echo "Copied $RELEASE_APK -> dist/Likhon-release.apk"
fi

ls -lh dist/
