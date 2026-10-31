# Bead Pilot 0.12 — experimental

[Download v0.12 APK](https://github.com/vkhoahuynh-droid/bead-pilot/raw/refs/heads/main/downloads/Bead-Pilot-v0.12.apk)

Editable Android sources are in `android/`; versioned APK/source downloads are in `downloads/`.

**If v0.6 is installed, uninstall Bead Pilot first**, then install v0.12 and grant root again. This removes Bead Pilot settings, not Bead Out progress. v0.12 uses the original signing certificate (v0.5 and v0.7 onward); v0.6 used a different certificate. v0.5 and v0.7–v0.11 can update in place.

v0.12 runs continuously across levels. A confirmed win now taps **Next first**, waits two seconds after the actual tap, presses **Android Home**, waits three seconds after the Home action, and reopens Bead Out. It resumes a confirmed board, presses recognized **Play** on the game menu, or reconfirms **Next** if the win screen remains. Slow ad/menu transitions cool down and retry instead of terminating the root player. Unknown ad content is never tapped. Pause and Stop cancel further navigation.

The main app's **Start automatic play** button now only shows the floating controls (requesting overlay permission if needed). Open Bead Out and use **floating Start** to begin automatic play. Capture is created only when Start is actually pressed and released on Pause; opening the panel alone does not start capture or input.

v0.11 fixes holding-row misidentification: a row of board cups could previously replace the four real holding slots and leave only three detected board squares, followed by endless percentage rereads. Detection now checks proximity to the observed loop exit, uses regular spacing with visible material confirmation, and retains established tray anchors while they remain visible. A matching existing row is checked before fresh row discovery. Temporary geometry failures withhold taps without discarding collector state; confirmed level navigation or an explicit Start still initializes a new model. Startup OCR can combine partial percentage reads across requests, with per-slot color checks. Motion recalibration preserves tracked fills.

The floating **Start / Pause / Stop** panel remains draggable by its status area. Floating Start is immediate; the main app's Start only opens the panel. Pause cancels pending starts. Bead Pilot no longer sets FLAG_SECURE on its own floating window, removing the app's recording-blocking setting. Pixels and temporary OCR images remain in RAM; OCR reads visible percentage glyphs, not the game's internal variables.

v0.9 fixes a reproduced planner freeze: an estimated bead shortage for one already active collector no longer vetoes unrelated new collectors. Automatic play evaluates the new choice's visible support instead of requiring perfect inventory for all existing colors. After six seconds without tracked collection progress it may add a partial but bead-backed third color, leaving a spare slot; that recovery cannot occupy the fourth slot. The fourth-slot completion guard, unknown-color exclusion and duplicate-collector restriction remain. Queue order and access-unlock scores still guide bounded lookahead. Search width/depth scale with board size, no-action results are retried, and live choices are evaluated before expensive waiting forecasts.

Status now reports the specific waiting reason, tracked collector percentages, approximate matching inventory, eligible count and search work. Cyan and royal blue remain separate IDs; the regression verifies collecting cyan does not complete the royal-blue collector. Geometry filters also reject wall elbows with floor in their center and combine overlapping parts of one cup. Preview images and annotation geometry now refer to the same recent cached game frame (RAM only, at most one update per four seconds).

v0.8 replaces the automatic player's fixed color labels with a palette learned from visible collectors and beads. A new level starts with a fresh palette; newly observed colors append without renumbering old IDs. The planner uses the observed color count, including counts beyond the old 14 colors. Holding-slot color identities tolerate shading while a collector stays present. Low-confidence/unseen colors remain unknown; black question-mark tiles are never trained as known collectors. Color groups use bounded, approximate image clustering (up to 64 groups), so this is not a guarantee of separating arbitrary nearly identical colors.

It also addresses a general source of the reported feeder-path failure: dark gray beads were excluded as rail/floor. Gray/white foreground detection now uses the current theme, and short leftover loop strips are rejected instead of aborting detection of valid feeders. Status reports the number of detected feeder paths. An optional last attempted game view is kept in RAM when discovery fails, so Show detected board can show an unannotated failure image without manual tracing or screenshot files.

Automatic rooted Android player for Bead Out. Supports KernelSU Next through `su`.

1. Install the APK and open Bead Pilot.
2. Tap **Start automatic play** to show the floating controls; allow overlay/root permission if prompted.
3. Open Bead Out and tap **Start** on the floating panel.
4. Use floating **Pause** to suspend or **Stop** to end the service.

No tracing, coordinate entry, or teaching is required. **Show detected board** is an optional preview of the last discovered layout.

The app detects four holding slots, changing square layouts, the loop and both feeders. It checks square height and a collision-free exit through the enclosure, treating other squares and visible border structures as obstacles regardless of hue. Black question-mark squares remain obstacles until they reveal their colors when clickable; their hidden colors are not invented. The planner combines observed percentages, moving bead rows and finite feeder queues to choose collectors and replan after reveals.

The detector uses the observed inner rail outline to distinguish real shape changes from bead movement. Supported loops use a measured offset from that outline or a validated skeleton for concave geometry. Initial discovery still needs visible bead support. An already observed, emptied loop can retain its own path while its closed rail remains visible. Feeders must be observed or have their missing tails visibly empty; unknown occlusion does not qualify. Geometry is checked periodically (about 1.5 seconds) and taps pause on failure or changed geometry. New geometry needs two consistent observations. No manual calibration is required.

After a confirmed win: **Next → wait 2 seconds → Android Home → wait 3 seconds → reopen Bead Out**. A recognized board resumes automatically; a recognized game menu gets Play. A returned win screen may reconfirm Next. Each recovery burst is bounded, then uses a ten-second cooldown and retries while automatic mode stays enabled. Transient navigation errors enter recovery instead of ending the service. The root/control watchdog still stops input if its controller is lost.

## Capture and efficiency

Root mirrors the display into an in-memory ImageReader at 582 pixels wide. Pixels are read from the mapped image plane. If unsupported, piped screencap is decoded entirely in RAM. No screenshot files, video history or gallery entries are created. Root uses Binder input rather than launching a process for every tap.

Bundled offline ML Kit OCR runs in the normal app process, with only one request outstanding. Small percentage crops are read at most twice per second; full-frame scene recognition is less frequent. Temporary OCR/preview images stay in RAM. The model increases installation size but does not download at runtime. The app requests no Internet or external-storage permission. Android still maintains normal app/cache/system storage.

Planning is bounded to 60 ms per search, at most twice per second. Performance, native memory and battery consumption have not been measured on the phone. Root status reports Java heap rather than total process memory.

## Validation and limitations

3,568 host checks passed: planner/search 1,220, finite feeders 1,607, continuous level recovery 73, gameplay model 76, legacy perception/tracking 51, automatic interpretation 39, adaptive palette 39, generated adaptive layouts 338, actual-frame detection/motion 105; observed-planner recovery 20. Actual frames include the changing square count, all 19 half-second frames through motion/refill, and recovery from an unreadable slot animation. Generated fixtures cover varied border colors, oval/rectangular/concave/triangular loops, gray feeders on beige, shaded white cups, changed feeder routes, blockers, unknowns, empty loops, and obscured queues. Palette tests use 2, 5 and 18 colors, new color additions, ID retention and relearning fewer colors. New row tests cover empty, partially occupied gray/white and fully occupied trays, plus rejection of a lower board row when the real tray is absent. Partial OCR and motion-reset tests preserve capacities and reject stale percentages after a color change. New planner cases reproduce two active 50% collectors with missing estimated supply, test a supported new collector, partial third-slot stall recovery, spare-slot protection, separate cyan/royal-blue accounting, and longer queue dependencies on 15/30/60-position boards. Replay checks require the four visibly different collectors to remain distinct. Navigation checks cover the exact acknowledged 2/3-second delays, two consecutive levels without another Start, returned Next, menu Play, failed input acknowledgment and four simulated minutes of unknown-ad recovery without ad taps or terminating automatic mode.

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
