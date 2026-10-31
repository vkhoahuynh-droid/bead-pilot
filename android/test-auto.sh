#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?Set ANDROID_HOME to platform 36/build-tools 35.0.0}"
mkdir -p test-build/auto
java com.sun.tools.javac.Main -source 8 -target 8 -Xlint:-options \
 -bootclasspath "$ANDROID_HOME/build-tools/35.0.0/core-lambda-stubs.jar:$ANDROID_HOME/platforms/android-36/android.jar" \
 -d test-build/auto app/src/main/java/com/beadout/bot/{Planner,Config,Capture,Vision,Tracker,SceneMarker,PathGeometry,AutoLayout,AutoPerception,TextScene}.java \
 app/src/test/java/com/beadout/bot/AutomaticModeTest.java
java -cp "test-build/auto:$ANDROID_HOME/platforms/android-36/android.jar" com.beadout.bot.AutomaticModeTest
java com.sun.tools.javac.Main -source 11 -target 11 -Xlint:-options \
 -cp "test-build/auto:$ANDROID_HOME/platforms/android-36/android.jar" -d test-build/auto app/src/test/java/com/beadout/bot/AdaptiveLayoutTest.java
java -Djava.awt.headless=true -cp "test-build/auto:$ANDROID_HOME/platforms/android-36/android.jar" com.beadout.bot.AdaptiveLayoutTest
if [[ $# -gt 0 ]]; then
 java com.sun.tools.javac.Main -source 11 -target 11 -Xlint:-options \
 -cp "test-build/auto:$ANDROID_HOME/platforms/android-36/android.jar" -d test-build/auto app/src/test/java/com/beadout/bot/AutoDetectionReplayTest.java
 java -cp "test-build/auto:$ANDROID_HOME/platforms/android-36/android.jar" com.beadout.bot.AutoDetectionReplayTest "$1"
fi
