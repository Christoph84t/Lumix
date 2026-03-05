#!/bin/bash
# Manuelles Build-Skript für S7 PLC Monitor (ohne Gradle/AGP)
set -e

APP_DIR="$(cd "$(dirname "$0")" && pwd)/app"
SRC="$APP_DIR/src/main"
BUILD="/tmp/s7plc_build"
ANDROID_HOME="${ANDROID_HOME:-/usr/lib/android-sdk}"
PLATFORM="$ANDROID_HOME/platforms/android-23/android.jar"
BUILD_TOOLS="$ANDROID_HOME/build-tools/29.0.3"
AAPT="$BUILD_TOOLS/aapt"
DX="$BUILD_TOOLS/dx"
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"
KOTLINC="$(which kotlinc)"

KOTLIN_STDLIB="/usr/share/java/kotlin-stdlib.jar"
COROUTINES_CORE="/tmp/kotlinx-coroutines-core.jar"
COROUTINES_ANDROID="/tmp/kotlinx-coroutines-android.jar"

echo "=== S7 PLC Monitor – Manueller Build ==="
echo "ANDROID_HOME: $ANDROID_HOME"
echo "BUILD_TOOLS: $BUILD_TOOLS"
echo ""

# Verzeichnisse anlegen
rm -rf "$BUILD"
mkdir -p "$BUILD"/{gen,obj,dex,apk_unsigned,apk}

# -------------------------------------------------------------------
# 1. Ressourcen kompilieren & R.java generieren
# -------------------------------------------------------------------
echo "[1/5] Ressourcen kompilieren (aapt)..."
"$AAPT" package -f -m \
  -J "$BUILD/gen" \
  -M "$SRC/AndroidManifest.xml" \
  -S "$SRC/res" \
  -I "$PLATFORM" \
  -F "$BUILD/apk_unsigned/resources.ap_"

echo "      R.java generiert"

# -------------------------------------------------------------------
# 2. Kotlin-Quellcode kompilieren
# -------------------------------------------------------------------
echo "[2/5] Kotlin kompilieren..."

# Alle .kt-Dateien sammeln
KT_FILES=$(find "$SRC/java" -name "*.kt" | tr '\n' ' ')

"$KOTLINC" \
  -cp "$PLATFORM:$KOTLIN_STDLIB:$COROUTINES_CORE:$COROUTINES_ANDROID" \
  -d "$BUILD/obj" \
  $KT_FILES "$BUILD/gen/com/lumix/s7plc/R.java" \
  2>&1

echo "      Kotlin kompiliert → $BUILD/obj"

# -------------------------------------------------------------------
# 3. DEX erstellen
# -------------------------------------------------------------------
echo "[3/5] DEX erstellen (dx)..."
"$DX" --dex \
  --output="$BUILD/apk_unsigned/classes.dex" \
  "$BUILD/obj" \
  "$COROUTINES_CORE" \
  "$COROUTINES_ANDROID" \
  "$KOTLIN_STDLIB"

echo "      classes.dex erstellt"

# -------------------------------------------------------------------
# 4. APK zusammenpacken
# -------------------------------------------------------------------
echo "[4/5] APK packen..."
cp "$BUILD/apk_unsigned/resources.ap_" "$BUILD/apk_unsigned/s7plc_unsigned.apk"

# classes.dex in die APK einfügen
cd "$BUILD/apk_unsigned" && zip -u s7plc_unsigned.apk classes.dex
cd - > /dev/null

echo "      APK gepackt"

# -------------------------------------------------------------------
# 5. Signieren (Debug-Keystore)
# -------------------------------------------------------------------
echo "[5/5] Signieren (Debug-Keystore)..."

KEYSTORE="$BUILD/debug.keystore"

# Debug-Keystore erstellen wenn nicht vorhanden
keytool -genkey -v \
  -keystore "$KEYSTORE" \
  -alias androiddebugkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass android \
  -keypass android \
  -dname "CN=Android Debug,O=Android,C=US" \
  2>/dev/null || true

# Zipalign + Signieren
"$ZIPALIGN" -f 4 \
  "$BUILD/apk_unsigned/s7plc_unsigned.apk" \
  "$BUILD/apk/s7plc_aligned.apk"

"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$BUILD/apk/S7PlcMonitor-debug.apk" \
  "$BUILD/apk/s7plc_aligned.apk"

echo ""
echo "=== BUILD ERFOLGREICH ==="
ls -lh "$BUILD/apk/S7PlcMonitor-debug.apk"
echo ""
echo "APK: $BUILD/apk/S7PlcMonitor-debug.apk"
echo ""
echo "Installation:"
echo "  adb install $BUILD/apk/S7PlcMonitor-debug.apk"
