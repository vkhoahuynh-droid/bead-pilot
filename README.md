# Bead Pilot 0.6 (development)

The current editable Android source is in `android/`. The experimental v0.6 APK is [available here](https://github.com/vkhoahuynh-droid/bead-pilot/raw/refs/heads/main/downloads/Bead-Pilot-v0.6.apk). Previous v0.5 artifacts are preserved.

**Installation:** v0.6 uses a different debug signing key and cannot update v0.5 in place. Replacing v0.5 requires uninstalling it first, which removes its local app data/settings, or rebuilding v0.6 with the original signing key. Phone gameplay has not yet been validated.

Automatic rooted Android player for Bead Out. Supports KernelSU Next through `su`.

1. Install the APK from the Releases page and grant root.
2. Tap **Start automatic play**, then switch to Bead Out within five seconds.
3. Use **Pause**, notification **Stop**, or the optional floating **STOP** to interrupt.

No tracing, coordinate entry, or teaching is required. **Show detected board** is an optional preview of the last discovered layout.

The app detects four holding slots, changing square layouts, the loop and both feeders. It checks square height and a collision-free exit through the enclosure, treating other squares and visible border structures as obstacles regardless of hue. Black question-mark squares remain obstacles until they reveal their colors when clickable; their hidden colors are not invented. The planner combines observed percentages, moving bead rows and finite feeder queues to choose collectors and replan after reveals.

The development detector follows a cycle in the observed track skeleton, including concave loops, and traces feeder branches without fixed side coordinates. It rechecks geometry before collector taps and waits for two consistent observations after a shape change. Missing or obscured geometry pauses tapping. A visibly drained queue may reuse its own previously observed path while the same loop and slots remain visible. No manual calibration is required.

After a confirmed win it presses Android Home, waits three seconds, reopens Bead Out and taps Next. It uses bounded Home/reopen recovery after ads, and recognizes the game menu to press Play. Persistent recognition or input failure pauses/stops instead of repeatedly tapping.

## Capture and efficiency

Root mirrors the display into an in-memory ImageReader at 582 pixels wide. Pixels are read from the mapped image plane. If unsupported, piped screencap is decoded entirely in RAM. No screenshot files, video history or gallery entries are created. Root uses Binder input rather than launching a process for every tap.

Bundled offline ML Kit OCR runs in the normal app process, with only one request outstanding. Small percentage crops are read at most twice per second; full-frame scene recognition is less frequent. Temporary OCR/preview images stay in RAM. The model increases installation size but does not download at runtime. The app requests no Internet or external-storage permission. Android still maintains normal app/cache/system storage.

Planning is bounded to 60 ms per search, at most twice per second. Performance, native memory and battery consumption have not been measured on the phone. Root status reports Java heap rather than total process memory.

## Validation and limitations

Host tests cover simulation, finite feeders, tracking, level recovery, OCR-result interpretation and automatic detection. `test-auto.sh` includes generated pixel fixtures with varied square arrangements, six border colors, oval/rounded-rectangle/concave loops, bent and upper feeders, blocked exits, unknown squares, drained queues, occlusion and rejected geometry. These are synthetic regressions, not validation on new phone footage. The optional replay suite requires decoded gameplay frames that are not in this repository; its historical v0.5 findings are described in HANDOFF.md.

**Experimental: actual phone OCR, root capture/input compatibility and a complete autonomous level have not been validated here.** Different track shapes, animations, occlusion and duplicate collector rules may require further changes. A successful build and frame replay do not prove reliable completion of arbitrary levels.

## Build

Use JDK 17+, Android SDK platform 36 and build-tools 35.0.0. The app includes bundled ML Kit text recognition and AndroidX dependencies.

```sh
cd android
./gradlew :app:assembleDebug :app:lintDebug
./test-engine.sh
ANDROID_HOME=/path/to/sdk ./test-perception.sh
ANDROID_HOME=/path/to/sdk ./test-auto.sh
# Optional host-only real-frame replay:
ANDROID_HOME=/path/to/sdk ./test-auto.sh /path/to/decoded/frames
```

The APK is `android/app/build/outputs/apk/debug/app-debug.apk`. Preserve the signing key for updates. This debug build keeps root entrypoint classes; release shrinking needs explicit keep rules. The source archive excludes the uploaded video and decoded frames. `bead_bot.py` and manual calibration classes are legacy drafts and are not used by the automatic Android interface.
