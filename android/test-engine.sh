#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p test-build
java com.sun.tools.javac.Main -source 11 -target 11 -d test-build \
 app/src/main/java/com/beadout/bot/Planner.java \
 app/src/main/java/com/beadout/bot/LevelCycle.java \
 app/src/test/java/com/beadout/bot/PlannerTest.java \
 app/src/test/java/com/beadout/bot/LevelCycleTest.java \
 app/src/test/java/com/beadout/bot/FeederTest.java \
 app/src/test/java/com/beadout/bot/GameplayRegressionTest.java
java -cp test-build com.beadout.bot.PlannerTest
java -cp test-build com.beadout.bot.LevelCycleTest
java -cp test-build com.beadout.bot.FeederTest
java -cp test-build com.beadout.bot.GameplayRegressionTest
