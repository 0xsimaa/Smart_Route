#!/usr/bin/env bash
# Download every Gradle dependency and produce debug + release APKs.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" ]] && [[ -d /usr/lib/jvm/java-17-openjdk-amd64 ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
fi

if [[ ! -f local.properties ]] && [[ -n "${ANDROID_HOME:-}" ]]; then
  echo "sdk.dir=${ANDROID_HOME}" > local.properties
elif [[ ! -f local.properties ]] && [[ -d "${HOME}/Android/Sdk" ]]; then
  echo "sdk.dir=${HOME}/Android/Sdk" > local.properties
fi

echo "==> Refreshing Gradle dependencies..."
./gradlew --refresh-dependencies :app:dependencies --configuration debugRuntimeClasspath

echo "==> Building debug + release APKs..."
./gradlew :app:assembleDebug :app:assembleRelease

echo "==> Running unit tests..."
./gradlew :app:testDebugUnitTest

echo ""
echo "Done."
echo "  Debug APK:   app/build/outputs/apk/debug/app-debug.apk"
echo "  Release APK: app/build/outputs/apk/release/app-release.apk"
echo ""
echo "Next: cp secrets.properties.example secrets.properties"
echo "      Add your MAPS_API_KEY, then open this folder in Android Studio."
