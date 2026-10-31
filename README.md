# Bead Pilot 0.7 — experimental

[Download v0.7 APK](https://github.com/vkhoahuynh-droid/bead-pilot/raw/refs/heads/main/downloads/Bead-Pilot-v0.7.apk)

Editable Android sources are in `android/`; versioned APK/source downloads are in `downloads/`.

**If v0.6 is installed, uninstall Bead Pilot first**, then install v0.7 and grant root again. This removes Bead Pilot settings, not Bead Out progress. v0.7 uses the original v0.5 signing certificate; v0.6 used a different certificate. v0.5 can update in place.

v0.7 fixes a reproduced regression: v0.6 rejected the first frame of the supplied gameplay despite passing generated-image tests. It restores recognition of shaded real beads/cup rims, tracks the closed rail independently of moving bead texture, and checks feeder overlap as queues drain. Brief animation/recognition failures pause taps before discarding the learned model. Status appears in the notification and optional draggable floating panel; Copy recent status copies a bounded RAM-only event history. Start commands wait for root startup when necessary.

Automatic rooted Android player for Bead Out. Supports KernelSU Next through `su`.

1. Install the APK from the Releases page and grant root.
2. Tap **Start automatic play**, then switch to Bead Out within five seconds.
3. Use **Pause**, notification **Stop**, or the optional floating **STOP** to interrupt.

No tracing, coordinate entry, or teaching is required. **Show detected board** is an optional preview of the last discovered layout.

The app detects four holding slots, changing square layouts, the loop and both feeders. It checks square height and a collision-free exit through the enclosure, treating other squares and visible border structures as obstacles regardless of hue. Black question-mark squares remain obstacles until they reveal their colors when clickable; their hidden colors are not invented. The planner combines observed percentages, moving bead rows and finite feeder queues to choose collectors and replan after reveals.

The detector uses the observed inner rail outline to distinguish real shape changes from bead movement. Supported loops use a measured offset from that outline or a validated skeleton for concave geometry. Initial discovery still needs visible bead support. An already observed, emptied loop can retain its own path while its closed rail remains visible. Feeders must be observed or have their missing tails visibly empty; unknown occlusion does not qualify. Geometry is checked periodically (about 1.5 seconds) and taps pause on failure or changed geometry. New geometry needs two consistent observations. No manual calibration is required.

After a confirmed win it presses Android Home, waits three seconds, reopens Bead Out and taps Next. It uses bounded Home/reopen recovery after ads, and recognizes the game menu to press Play. Persistent recognition or input failure pauses/stops instead of repeatedly tapping.

## Capture and efficiency

Root mirrors the display into an in-memory ImageReader at 582 pixels wide. Pixels are read from the mapped image plane. If unsupported, piped screencap is decoded entirely in RAM. No screenshot files, video history or gallery entries are created. Root uses Binder input rather than launching a process for every tap.

Bundled offline ML Kit OCR runs in the normal app process, with only one request outstanding. Small percentage crops are read at most twice per second; full-frame scene recognition is less frequent. Temporary OCR/preview images stay in RAM. The model increases installation size but does not download at runtime. The app requests no Internet or external-storage permission. Android still maintains normal app/cache/system storage.

Planning is bounded to 60 ms per search, at most twice per second. Performance, native memory and battery consumption have not been measured on the phone. Root status reports Java heap rather than total process memory.

## Validation and limitations

3,420 host checks passed: planner/search 1,220, finite feeders 1,607, level recovery 49, gameplay model 76, legacy perception/tracking 51, automatic interpretation 34, generated adaptive layouts 295, actual-frame detection/motion 88. Actual frames include the changing square count, all 19 half-second frames through motion/refill, and recovery from an unreadable slot animation. Generated fixtures cover varied border colors, oval/rectangular/concave loops, changed feeder routes, blockers, unknowns, empty loops, and obscured queues.

Build, lint and APK signature checks passed. These are host checks, not a complete phone autoplay test. The supplied clip has no completed collector, win or ad transition. Native phone OCR, input/capture compatibility, arbitrary level layouts, memory/battery use and full autonomous completion remain unvalidated. Border/track heuristics can still fail on new graphics; the live status is intended to make failures diagnosable. Hidden square colors are never guessed.

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
