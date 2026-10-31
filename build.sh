#!/bin/bash
set -e
SDK=$HOME/android-sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
cd "$(dirname "$0")"

mkdir -p tools
if [ ! -f tools/r8.jar ]; then
  echo "downloading r8..."
  curl -sL -o tools/r8.jar https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.7.18/r8-8.7.18.jar
fi

rm -rf build
mkdir -p build/gen build/obj build/dex

echo "[1/5] aapt package (resources + manifest + assets)"
aapt package -f -m \
  -J build/gen \
  -M AndroidManifest.xml \
  -S res \
  -A assets \
  -I "$PLATFORM" \
  -F build/app.unsigned.apk \
  --min-sdk-version 26 --target-sdk-version 32 \
  --version-code 12 --version-name 1.11

echo "[2/5] javac"
find src build/gen -name "*.java" > build/sources.txt
javac -encoding UTF-8 --release 11 -nowarn -classpath "$PLATFORM" -d build/obj @build/sources.txt

echo "[3/5] d8"
find build/obj -name "*.class" > build/classes.txt
java -Xmx1500m -cp tools/r8.jar com.android.tools.r8.D8 --min-api 26 --lib "$PLATFORM" \
  --output build/dex @build/classes.txt

echo "[4/5] dex into apk + align"
( cd build/dex && zip -q -X ../app.unsigned.apk classes.dex )
zipalign -f -p 4 build/app.unsigned.apk build/app.aligned.apk

echo "[5/5] sign"
KEYSTORE=${ADA_KEYSTORE_FILE:-ada.keystore}
if [ -z "${ADA_KEYSTORE_PASS:-}" ]; then
  if [ -t 0 ]; then
    read -s -p "Android keystore password: " ADA_KEYSTORE_PASS
    echo
  else
    echo "Set ADA_KEYSTORE_PASS before building." >&2
    exit 1
  fi
fi
ADA_KEY_PASS=${ADA_KEY_PASS:-$ADA_KEYSTORE_PASS}
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias ada -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$ADA_KEYSTORE_PASS" -keypass "$ADA_KEY_PASS" \
    -dname "CN=Ada Island, OU=Dev, O=Ada, L=Istanbul, C=TR" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$ADA_KEYSTORE_PASS" --key-pass "pass:$ADA_KEY_PASS" \
  --min-sdk-version 26 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out ada.apk build/app.aligned.apk

apksigner verify --print-certs ada.apk | head -3
ls -lh ada.apk
echo "OK"
