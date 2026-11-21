#!/usr/bin/env bash
# Builds Pochette without Gradle: aapt -> javac -> dx -> zipalign -> apksigner.
# Needs (Ubuntu/Debian): aapt zipalign apksigner dalvik-exchange android-sdk-platform-23 openjdk
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
# Resources are compiled against a recent android.jar (new manifest attributes); code against API 23 so no newer API slips in.
AAPT_JAR=${AAPT_JAR:-/home/claude/tools/sdk/android-34.jar}
[ -f "$AAPT_JAR" ] || AAPT_JAR=$ANDROID_JAR
B=build; rm -rf $B; mkdir -p $B/gen $B/classes $B/dex
aapt package -f -m -J $B/gen -M AndroidManifest.xml -S res -I "$AAPT_JAR" -F $B/unsigned.apk
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
      -d $B/classes $(find src $B/gen -name '*.java')
dalvik-exchange --dex --min-sdk-version=23 --output=$B/dex/classes.dex $B/classes
(cd $B/dex && aapt add ../unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 $B/unsigned.apk $B/aligned.apk
KS=${KEYSTORE:-pochette.keystore}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass pochette -keypass pochette -alias pochette \
          -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Pochette" >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:pochette --key-pass pass:pochette --ks-key-alias pochette \
          --out Pochette.apk $B/aligned.apk
apksigner verify Pochette.apk && echo "OK -> $(pwd)/Pochette.apk"
