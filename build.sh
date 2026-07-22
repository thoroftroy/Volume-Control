#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ----- check prerequisites -----
if ! command -v java &> /dev/null; then
    echo "Error: Java is required (JDK 17+). Install it and try again."
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | grep -oP '\d+' | head -1)
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
    echo "Warning: Java $JAVA_VER detected. JDK 17+ is recommended."
fi

# ----- find Android SDK -----
if [ -n "$ANDROID_HOME" ]; then
    SDK_DIR="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    SDK_DIR="$ANDROID_SDK_ROOT"
else
    for guess in "$HOME/Android/Sdk" "/usr/local/android-sdk" "$HOME/Android" "/opt/android-sdk"; do
        if [ -d "$guess/platforms" ] || [ -d "$guess/build-tools" ]; then
            SDK_DIR="$guess"
            export ANDROID_HOME="$SDK_DIR"
            break
        fi
    done
fi

if [ -z "$SDK_DIR" ]; then
    echo "Error: Android SDK not found."
    echo "Set ANDROID_HOME to your SDK path (e.g. ~/Android/Sdk)."
    exit 1
fi

echo "ANDROID_HOME = $SDK_DIR"
echo "sdk.dir=$SDK_DIR" > local.properties

# ----- setup Gradle wrapper -----
if [ ! -f "gradlew" ]; then
    echo "Setting up Gradle wrapper..."

    if command -v gradle &> /dev/null; then
        echo "Using system Gradle to generate wrapper..."
        gradle wrapper --gradle-version 8.7
    else
        GRADLE_VER="8.7"
        ZIP="gradle-${GRADLE_VER}-bin.zip"
        URL="https://services.gradle.org/distributions/${ZIP}"
        TMPDIR="/tmp/vc-gradle"

        echo "Downloading Gradle ${GRADLE_VER}..."
        mkdir -p "$TMPDIR"

        if command -v wget &> /dev/null; then
            wget -q --show-progress "$URL" -O "${TMPDIR}/${ZIP}"
        elif command -v curl &> /dev/null; then
            curl -fSL --progress-bar "$URL" -o "${TMPDIR}/${ZIP}"
        else
            echo "Error: install wget or curl to continue."
            exit 1
        fi

        echo "Extracting..."
        unzip -qo "${TMPDIR}/${ZIP}" -d "$TMPDIR"

        echo "Generating wrapper..."
        "${TMPDIR}/gradle-${GRADLE_VER}/bin/gradle" wrapper --gradle-version "$GRADLE_VER"

        rm -rf "$TMPDIR"
    fi

    echo "Gradle wrapper ready."
fi

# ----- build -----
echo ""
echo "Building debug APK..."
./gradlew assembleDebug --no-daemon --warning-mode=none 2>&1 | tail -20

APK="app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$APK" ]; then
    mkdir -p "$SCRIPT_DIR/Output"
    cp "$APK" "$SCRIPT_DIR/Output/VolumeControl.apk"

    echo ""
    echo "============================================"
    echo "  APK built successfully!"
    echo "  $SCRIPT_DIR/Output/VolumeControl.apk"
    echo "============================================"

    APK_SIZE=$(du -h "$SCRIPT_DIR/Output/VolumeControl.apk" | cut -f1)
    echo "  Size: $APK_SIZE"
else
    echo ""
    echo "Build failed. Check output above for errors."
    exit 1
fi
