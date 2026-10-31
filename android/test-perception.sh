#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?Set ANDROID_HOME to an SDK with platform 36 and build-tools 35.0.0}"
mkdir -p test-build/perception
java com.sun.tools.javac.Main -source 8 -target 8 -Xlint:-options \
 -bootclasspath "$ANDROID_HOME/build-tools/35.0.0/core-lambda-stubs.jar:$ANDROID_HOME/platforms/android-36/android.jar" \
 -d test-build/perception app/src/main/java/com/beadout/bot/{Planner,Config,Capture,Vision,Tracker,SceneMarker,PathGeometry}.java \
 app/src/test/java/com/beadout/bot/PerceptionTest.java
java -cp "test-build/perception:$ANDROID_HOME/platforms/android-36/android.jar" com.beadout.bot.PerceptionTest
