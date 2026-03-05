#!/bin/bash
# =============================================================================
# S7 PLC Monitor – Manuelles APK-Build-Skript
# Baut die APK ohne Android Gradle Plugin (AGP) mit Kommandozeilen-Tools.
# Keine AndroidX/Material-Abhängigkeiten – nur Stock-Android + Coroutines.
# =============================================================================

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$SCRIPT_DIR/app"
SRC_DIR="$APP_DIR/src/main"
BUILD_DIR="$SCRIPT_DIR/build-output"

# ---- Toolpfade ---------------------------------------------------------------
KOTLINC="/opt/kotlin-1.8.20/kotlinc/bin/kotlinc"
ANDROID_HOME="/usr/lib/android-sdk"
ANDROID_JAR="$ANDROID_HOME/platforms/android-23/android.jar"
AAPT2="$ANDROID_HOME/build-tools/29.0.3/aapt2"
AAPT="$ANDROID_HOME/build-tools/29.0.3/aapt"
DX="/usr/bin/dalvik-exchange"
APKSIGNER="$ANDROID_HOME/build-tools/29.0.3/apksigner"

# Verzeichnisse bereinigen und neu anlegen
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{libs,classes,dex,apk,res-compiled,res-linked}

echo "=== S7 PLC Monitor APK-Build (ohne AndroidX) ==="
echo ""

# =============================================================================
# 1. Maven-Abhängigkeiten von Maven Central herunterladen
#    Nur: kotlin-stdlib + kotlinx-coroutines (kein AndroidX!)
# =============================================================================
echo "[1/7] Lade Abhängigkeiten von Maven Central..."

LIBS_DIR="$BUILD_DIR/libs"
BASE_URL="https://repo1.maven.org/maven2"

download_jar() {
    local group_path="$1"
    local artifact="$2"
    local version="$3"
    local output="$LIBS_DIR/${artifact}-${version}.jar"

    if [ ! -f "$output" ]; then
        local url="$BASE_URL/$group_path/$version/${artifact}-${version}.jar"
        echo "  Lade: $artifact:$version"
        curl -sL --max-time 120 "$url" -o "$output" 2>/dev/null
        if [ ! -s "$output" ] || file "$output" | grep -q "HTML"; then
            echo "  WARNUNG: $artifact nicht verfügbar"
            rm -f "$output"
        fi
    fi
}

# Kotlin Runtime
download_jar "org/jetbrains/kotlin/kotlin-stdlib"            "kotlin-stdlib"              "1.8.20"
download_jar "org/jetbrains/kotlin/kotlin-stdlib-common"     "kotlin-stdlib-common"       "1.8.20"

# Coroutines (von Maven Central – JetBrains-Artefakte)
download_jar "org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm" "kotlinx-coroutines-core-jvm"    "1.7.1"
download_jar "org/jetbrains/kotlinx/kotlinx-coroutines-android" "kotlinx-coroutines-android" "1.7.1"

# Coroutines-Atom (Abhängigkeit)
download_jar "org/jetbrains/kotlinx/kotlinx-coroutines-bom"         "kotlinx-coroutines-bom"          "1.7.1"
download_jar "org/jetbrains/kotlin/kotlin-stdlib-jdk8"              "kotlin-stdlib-jdk8"               "1.8.20"
download_jar "org/jetbrains/kotlin/kotlin-stdlib-jdk7"              "kotlin-stdlib-jdk7"               "1.8.20"
download_jar "org/jetbrains/kotlinx/atomicfu"                        "atomicfu"                         "0.20.2"
download_jar "org/jetbrains/kotlinx/atomicfu-jvm"                   "atomicfu-jvm"                     "0.20.2"

echo "  Fertig. $(ls "$LIBS_DIR"/*.jar 2>/dev/null | wc -l) JARs heruntergeladen."

# =============================================================================
# 2. Ressourcen kompilieren mit aapt2
# =============================================================================
echo ""
echo "[2/7] Kompiliere Android-Ressourcen..."

RES_DIR="$SRC_DIR/res"
COMPILED_RES="$BUILD_DIR/res-compiled"

"$AAPT2" compile --dir "$RES_DIR" -o "$COMPILED_RES" 2>&1 | head -20 || true

FLAT_COUNT=$(ls "$COMPILED_RES"/*.flat 2>/dev/null | wc -l)
echo "  $FLAT_COUNT Ressourcen-Dateien kompiliert."

if [ "$FLAT_COUNT" -eq 0 ]; then
    echo "FEHLER: Keine Ressourcen kompiliert. Build abgebrochen."
    exit 1
fi

# =============================================================================
# 3. Ressourcen verknüpfen (aapt2 link) → R.java + resources.apk + Manifest
# =============================================================================
echo ""
echo "[3/7] Verknüpfe Ressourcen (aapt2 link)..."

mkdir -p "$BUILD_DIR/res-linked/com/lumix/s7plc"

"$AAPT2" link \
    -o "$BUILD_DIR/res-linked/resources.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$SRC_DIR/AndroidManifest.xml" \
    --java "$BUILD_DIR/res-linked" \
    --auto-add-overlay \
    "$COMPILED_RES"/*.flat 2>&1 | head -20

R_JAVA=$(find "$BUILD_DIR/res-linked" -name "R.java" 2>/dev/null | head -1)
if [ -z "$R_JAVA" ]; then
    echo "  WARNUNG: R.java nicht gefunden – versuche Fallback mit aapt..."
    "$AAPT" package -f -m \
        -S "$RES_DIR" \
        -J "$BUILD_DIR/res-linked" \
        -M "$SRC_DIR/AndroidManifest.xml" \
        -I "$ANDROID_JAR" \
        2>&1 | head -10 || true
    R_JAVA=$(find "$BUILD_DIR/res-linked" -name "R.java" 2>/dev/null | head -1)
fi

echo "  R.java: ${R_JAVA:-NICHT GEFUNDEN}"

# =============================================================================
# 4. Kotlin + R.java kompilieren
# =============================================================================
echo ""
echo "[4/7] Kompiliere Kotlin-Quellen..."

# Classpath zusammenbauen
CP="$ANDROID_JAR"
for jar in "$LIBS_DIR"/*.jar; do
    [ -f "$jar" ] && CP="$CP:$jar"
done

# Quellen
KT_SOURCES=$(find "$SRC_DIR/java" -name "*.kt" | tr '\n' ' ')
JAVA_SOURCES=$(find "$BUILD_DIR/res-linked" -name "*.java" 2>/dev/null | tr '\n' ' ')

echo "  Kotlin-Quellen: $(find "$SRC_DIR/java" -name "*.kt" | wc -l)"
echo "  Java-Quellen (R.java): $(find "$BUILD_DIR/res-linked" -name "*.java" 2>/dev/null | wc -l)"
echo "  Classpath-Einträge: $(echo "$CP" | tr ':' '\n' | wc -l)"

"$KOTLINC" \
    $KT_SOURCES $JAVA_SOURCES \
    -classpath "$CP" \
    -d "$BUILD_DIR/classes" \
    -jvm-target 1.8 \
    -no-stdlib \
    2>&1 | grep -v "^w:" | grep -v "JAVA_TOOL" | head -80

CLASS_COUNT=$(find "$BUILD_DIR/classes" -name "*.class" | wc -l)
echo "  $CLASS_COUNT .class-Dateien kompiliert."

if [ "$CLASS_COUNT" -eq 0 ]; then
    echo "FEHLER: Keine .class-Dateien – Kompilierung fehlgeschlagen."
    exit 1
fi

# =============================================================================
# 5. DEX-Konvertierung
# =============================================================================
echo ""
echo "[5/7] Konvertiere zu Dalvik-Bytecode (.dex)..."

cd "$BUILD_DIR/classes" && jar cf "$BUILD_DIR/app-classes.jar" . && cd "$SCRIPT_DIR"

# Bereinige JARs: entferne META-INF/versions (dx versteht keine Multi-Release-JARs)
mkdir -p "$BUILD_DIR/clean-libs"
for jar in "$LIBS_DIR"/*.jar; do
    name=$(basename "$jar")
    clean="$BUILD_DIR/clean-libs/$name"
    cp "$jar" "$clean"
    zip -d "$clean" "META-INF/versions/*" "module-info.class" 2>/dev/null || true
done

"$DX" --dex \
    --output="$BUILD_DIR/dex/classes.dex" \
    --min-sdk-version=26 \
    "$BUILD_DIR/app-classes.jar" \
    $(ls "$BUILD_DIR/clean-libs"/*.jar 2>/dev/null | tr '\n' ' ') \
    2>&1 | grep -v "JAVA_TOOL" | head -30

DEX_SIZE=$(ls -lh "$BUILD_DIR/dex/classes.dex" 2>/dev/null | awk '{print $5}')
echo "  DEX: ${DEX_SIZE:-NICHT ERSTELLT}"

# =============================================================================
# 6. APK zusammenbauen
# =============================================================================
echo ""
echo "[6/7] Baue APK..."

APK_UNSIGNED="$BUILD_DIR/apk/s7plc-unsigned.apk"

if [ -f "$BUILD_DIR/res-linked/resources.apk" ]; then
    cp "$BUILD_DIR/res-linked/resources.apk" "$APK_UNSIGNED"
else
    # Leeres ZIP als Basis
    cd /tmp && zip -q "$APK_UNSIGNED" -b . . -x "*" 2>/dev/null; cd "$SCRIPT_DIR" || true
fi

# DEX hinzufügen
cd "$BUILD_DIR/dex" && zip -q "$APK_UNSIGNED" classes.dex && cd "$SCRIPT_DIR"

echo "  APK (unsigniert): $(ls -lh "$APK_UNSIGNED" | awk '{print $5}')"

# =============================================================================
# 7. Signieren
# =============================================================================
echo ""
echo "[7/7] Signiere APK..."

APK_SIGNED="$BUILD_DIR/apk/s7plc-debug.apk"
KEYSTORE="$BUILD_DIR/debug.keystore"

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" \
        2>/dev/null
fi

"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$APK_SIGNED" \
    "$APK_UNSIGNED" \
    2>&1 | grep -v "JAVA_TOOL"

echo ""
echo "✓ BUILD ERFOLGREICH!"
echo "  APK: $APK_SIGNED"
echo "  Größe: $(ls -lh "$APK_SIGNED" | awk '{print $5}')"
echo ""
echo "Auf Gerät installieren:"
echo "  adb install $APK_SIGNED"
