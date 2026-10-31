# Bead Pilot 0.10 — experimental

[Download v0.10 APK](https://github.com/vkhoahuynh-droid/bead-pilot/raw/refs/heads/main/downloads/Bead-Pilot-v0.10.apk)

Editable Android sources are in `android/`; versioned APK/source downloads are in `downloads/`.

**If v0.6 is installed, uninstall Bead Pilot first**, then install v0.10 and grant root again. This removes Bead Pilot settings, not Bead Out progress. v0.10 uses the original v0.5/v0.7/v0.8/v0.9 signing certificate; v0.6 used a different certificate. v0.5, v0.7, v0.8 and v0.9 can update in place.

v0.10 adds **Start / Pause / Stop** to the draggable floating panel. Floating Start begins immediately while Bead Out remains open; Start in the main app retains the five-second switch-to-game delay. Pause cancels delayed starts and keeps the panel available for Start again. Stop ends the service and removes the panel. Enable **Allow floating controls** if the overlay permission is missing. Drag the status text to move the panel.

This UI update passed assemble/debug and lint. The planner/detector are unchanged from v0.9; their 3,507 host checks were last run for v0.9. The floating controls still need phone-side touch/layout validation.

v0.9 fixes a reproduced planner freeze: an estimated bead shortage for one already active collector no longer vetoes unrelated new collectors. Automatic play evaluates the new choice's visible support instead of requiring perfect inventory for all existing colors. After six seconds without tracked collection progress it may add a partial but bead-backed third color, leaving a spare slot; that recovery cannot occupy the fourth slot. The fourth-slot completion guard, unknown-color exclusion and duplicate-collector restriction remain. Queue order and access-unlock scores still guide bounded lookahead. Search width/depth scale with board size, no-action results are retried, and live choices are evaluated before expensive waiting forecasts.

Status now reports the specific waiting reason, tracked collector percentages, approximate matching inventory, eligible count and search work. Cyan and royal blue remain separate IDs; the regression verifies collecting cyan does not complete the royal-blue collector. Geometry filters also reject wall elbows with floor in their center and combine overlapping parts of one cup. Preview images and annotation geometry now refer to the same recent cached game frame (RAM only, at most one update per four seconds).

v0.8 replaces the automatic player's fixed color labels with a palette learned from visible collectors and beads. A new level starts with a fresh palette; newly observed colors append without renumbering old IDs. The planner uses the observed color count, including counts beyond the old 14 colors. Holding-slot color identities tolerate shading while a collector stays present. Low-confidence/unseen colors remain unknown; black question-mark tiles are never trained as known collectors. Color groups use bounded, approximate image clustering (up to 64 groups), so this is not a guarantee of separating arbitrary nearly identical colors.

It also addresses a general source of the reported feeder-path failure: dark gray beads were excluded as rail/floor. Gray/white foreground detection now uses the current theme, and short leftover loop strips are rejected instead of aborting detection of valid feeders. Status reports the number of detected feeder paths. An optional last attempted game view is kept in RAM when discovery fails, so Show detected board can show an unannotated failure image without manual tracing or screenshot files.

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

3,507 host checks passed: planner/search 1,220, finite feeders 1,607, level recovery 49, gameplay model 76, legacy perception/tracking 51, automatic interpretation 34, adaptive palette 39, generated adaptive layouts 306, actual-frame detection/motion 105; observed-planner recovery 20. Actual frames include the changing square count, all 19 half-second frames through motion/refill, and recovery from an unreadable slot animation. Generated fixtures cover varied border colors, oval/rectangular/concave/triangular loops, gray feeders on beige, shaded white cups, changed feeder routes, blockers, unknowns, empty loops, and obscured queues. Palette tests use 2, 5 and 18 colors, new color additions, ID retention and relearning fewer colors. New planner cases reproduce two active 50% collectors with missing estimated supply, test a supported new collector, partial third-slot stall recovery, spare-slot protection, separate cyan/royal-blue accounting, and longer queue dependencies on 15/30/60-position boards. Replay checks require the four visibly different collectors to remain distinct.

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
