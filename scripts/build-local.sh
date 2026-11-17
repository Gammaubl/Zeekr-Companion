#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

export JAVA_HOME="$HOME/.local/share/android-build-tools/jdk-17.0.21+9/Contents/Home"
export ANDROID_HOME="$HOME/.local/share/android-build-tools/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

echo "==========================================="
echo "  Building 7xTubeDash APK for Zeekr 7X"
echo "==========================================="
echo "Java Home: $JAVA_HOME"
echo "Android SDK: $ANDROID_HOME"

cd "$PROJECT_ROOT"
if [ ! -f ./gradlew ]; then
    echo "Initializing Gradle Wrapper..."
    $HOME/.local/share/android-build-tools/gradle-8.7/bin/gradle wrapper --gradle-version 8.7
fi

./gradlew assembleRelease --stacktrace

mkdir -p release
cp app/build/outputs/apk/release/*.apk release/7xTubeDash-arm64.apk

echo "==========================================="
echo "  BUILD SUCCESSFUL!"
echo "  Output: $PROJECT_ROOT/release/7xTubeDash-arm64.apk"
echo "==========================================="
