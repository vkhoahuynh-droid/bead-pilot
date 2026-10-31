# Current continuation — v0.11

User corrected an initially wrong inline screenshot. Correct Level 24 image has gray and white at 50%, empty middle holding slots, and magenta slot markers incorrectly placed on the lower pink/lime/purple/dark-green board row. Logs repeatedly adopt 4 slots/3 squares, then Reading existing fill percentages and Found 3 feeder paths. Do not use the first wrong screenshot as evidence that the fourth slot necessarily deadlocked the game; the corrected image shows brown/lime collectors have left.

Confirmed reproduction with generated pixels: v0.10 detects holding row y=.696875 instead of about .58 and reports exactly 3 remaining squares when the true tray is partially gray/white, fully occupied, or erased. That matches the reported failure pattern. The exact inline image still is not a local PNG; this is a synthetic reproduction plus the original real-video regression, not an exact pixel replay of Level 24.

v0.11 AutoLayout validates holding-row separation from the observed loop exit (scaled by cup width), constructs regular rows from at least three candidate objects with visible material required for a missing fourth, and checks prior tray anchors first when their theme/pixels remain supported. Bare floor cannot validate a missing known slot. The detector no longer falls through to a different row after a supported existing row fails track validation. Compact loop remnants require stronger branch evidence to be new feeders; shortened previously observed feeders are retained when supported, and two distinct matching known feeds may disambiguate extra components. This preserves the real-frame late shortened-queue test while rejecting a triangular-loop apex fragment that previously acted as a third feed.

AutomaticRootMain no longer destroys collector state after six seconds of detection trouble or silently adopts changed geometry mid-level. It withholds taps, retains slots/palette/fill state and resets only motion learning. A confirmed level-cycle reset or explicit Start still replaces the model. Direction recalibration also preserves capacities. Tracker accumulates initial percentages across multiple OCR results, keyed by slot color, instead of requiring all occupied percentages in one OCR response. Missing reads stay unknown, and changed slot colors invalidate their cached read.

User asked why OCR is used with memory capture and why screen recording is blocked. Explained: capture is mapped screen pixels in RAM, not Bead Out process variables; OCR turns the visible percentages into numbers. Removed FLAG_SECURE from PilotService's floating window. This only removes Bead Pilot's own protected-window setting; recording behavior needs phone confirmation. No screenshot files/history or game-heap introspection were added.

All 3,544 host checks passed: 1,220 planner/search, 1,607 finite feeders, 49 level cycle, 76 recorded model, 51 legacy perception, 20 observed recovery, 39 automatic interpretation, 39 adaptive palette, 338 adaptive geometry and 105 real-frame replay. New tray tests explicitly assert correct slot row for five occupancy patterns, board count, known-geometry retention and rejection of an erased tray; split OCR cache/motion preservation are covered. Build and lint passed. Phone capture/OCR, recording, the exact Level 24 case and full autonomous completion still require live validation.

VersionCode 11/versionName 0.11. Same signing key as v0.10. Publish APK/source/checksum under downloads and provide normal GitHub HTTPS links. The cloud workspace briefly disconnected during this work, then recovered; drafts survived. Current source is /workspace/bead-pilot-publish/android. Historical context follows.

---

# Current continuation — v0.10 floating controls

User requested Start and Pause in the floating panel. PilotService.showOverlay now uses a compact 180 dp row with equal-width Start, Pause and Stop buttons. Start calls scheduleAutomatic(auto, 0): immediate root auto/re-detection while staying in the game. The app's Start calls the same helper with its existing five-second delay. Floating Pause cancels any scheduled start, sends pause and leaves the panel visible; Stop stops the service/removes the panel. Existing non-focusable overlay and status-text drag handle remain. MainActivity permission label now says Allow floating controls and explains the controls.

Only PilotService, MainActivity, version metadata and docs changed. VersionCode 10/versionName 0.10; same signing key as v0.9. Build and lint passed. The reversible UI-only change did not add tests or rerun unchanged planner/detector tests; the last core run was v0.9's 3,507 host checks. Phone overlay touch/layout and gameplay remain unvalidated here. Preserve source/APK/checksum under downloads/ and share normal GitHub HTTPS URLs.

---

# Current continuation — v0.9

User supplied a log stuck alternating Scanning and Waiting for a safe collector with 2/4 active slots, 15 board detections, 12 color groups and increasing movement samples. An inline annotated Level 24 screenshot shows gray and royal-blue collectors both at 50%, distinct cyan beads, several visibly reachable colored cups and some apparent white wall-corner markers. User explicitly wants adaptive, predictive play for progressively harder layouts, rather than rigid refusal while free slots remain. This screenshot is viewable in conversation but not available as a local PNG in this executor; do not claim exact screenshot pixel replay.

Confirmed planner defect: strict search rejected every tap whenever ANY active color's estimated remaining need exceeded its visible inventory plus slack. That is invalid as a universal veto for approximate pixel observations. New ObservedPlannerTest reproduces the old no-action result with two active collectors at 50% and known deficits, then verifies observed-mode search selects a different fully supported color and completes it. A cyan (ID 2) collection explicitly leaves royal-blue (ID 1) remaining capacity unchanged except for the royal-blue beads actually present.

Planner.searchObserved is now used by AutomaticRootMain. It checks the proposed new color's supply instead of global stranded state. Six seconds without a tracked percentage decrease enables first-action partial recovery only with at most two occupied slots and at least five observed matching beads; this can use a third slot while preserving one spare. Fourth-slot root guard still needs forecast completion, and observed search rejects partial fourth-slot trials. Unknown/zero-visible/duplicate proposals remain excluded. Legacy exact-inventory search remains available for reference tests. Taps are considered before WAIT forecasts, no-action cached plans are periodically retried even on identical state keys, and search beam/depth scale with initial board complexity within the existing budget. Model resets rebuild the planner and stall timer.

Runtime diagnostics include percentages, palette IDs/RGB, approximate visible matching inventory, eligible count, expansion count and a concrete wait reason. Tracker now says Movement tracked rather than predictive play ready. Source snapshot preview caches image + matching serialized geometry and refreshes at most every four seconds during play; still RAM-only with no screenshot history/files.

Geometry regression: a generated white wall elbow makes v0.8 detect eight cups in a seven-cup fixture. The new interior-material check rejects the elbow. Strongly overlapping cup subcomponents are merged instead of separately blocking one another. The original real-frame suite and adaptive shape suite pass. The exact phone square count after this change remains unverified.

3,507 host checks passed: planner/search 1,220; finite feeders 1,607; level cycle 49; recorded model 76; perception/tracking 51; automatic interpretation 34; adaptive palette 39; adaptive layout pixels 306; actual-frame replay 105; observed-planner recovery 20. Recovery tests include hidden/blocked 15/30/60-position boards and multi-color FIFO dependency chains, plus negative tests for hidden, duplicate, zero-supply and partial-fourth choices. Build/lint/signature must pass before delivery; actual phone OCR and full autonomous level completion are still unverified. This is an approximate online planner, not a guarantee of solving arbitrary unknown hard levels.

APK versionCode 9, same signing key as v0.8. Publish versioned APK/source/checksum under downloads/ and use GitHub HTTPS links. Historical context follows.

---

# Current continuation — v0.8

User reported v0.7 stuck waiting for two observed feeder paths, provided an inline Level 24 screenshot (beige theme, white enclosure, triangular loop, gray bead stretches/slate cups), and clarified that layouts and color sets change every level, with additional/fewer/new colors. The exact inline PNG is not a local file in this executor; do not claim an exact screenshot pixel replay. The original Level 23 video is still locally available.

Confirmed general code defects: Grid.color excluded dark neutral/slate beads, breaking gray feeder stretches; AutoPerception treated gray as empty, and automatic planner/state labels assumed Config's 14 legacy colors. A short loop remnant could also throw Queue centerline incomplete before evaluating the two actual feeds.

v0.8 adds AdaptivePalette: append-only observed RGB prototypes, dense-sample evidence (at least three votes), hue/value/perceptual color matching, cached descriptors, reset per new Config/level. Learns detected collectors first, then bead paths, avoiding an intermediate bead-edge shade merging cyan/blue. Holding-slot identity uses bounded shade hysteresis until observed empty. Prototypes append without reindexing. This is approximate clustering, bounded to 64 groups, not a promise to distinguish every shade/color; dark neutral question-mark squares remain excluded from known-color training.

Planner is recreated when learned count changes; new IDs can exceed the 14 old labels without indexing failures. Legacy fixed RGB/name constants remain only for old manual code/tests and geometry uses coarse color segmentation, not semantic automatic labels. Config serializes its observed palette. Status shows observed color count and selected prototype RGB. AutoLayout recognizes gray/white foreground relative to theme, preserves unknown black tiles as obstacles, skips short/near-loop strip remnants and reports actual feeder count on failure.

Optional debug preview keeps one most-recent failed-discovery game image in RAM (updated at most every four seconds), only encodes/sends it when requested. No screenshots/history are written. Reset clears stale UI geometry. Existing notification/floating status and Copy recent status remain.

3,486 host checks passed: previous domain/perception 3,003; interpretation 34; adaptive palette 39; adaptive geometry 305; actual-frame replay 105. New synthetic fixtures exercise gray feeders on beige with oval, rectangular and triangular loops, plus a shaded white cup on beige. Palette tests exercise 2/5/18 colors, new additions, shade matching, gray/white/brown/orange distinction, count beyond 14, outlier rejection, and clearing/relearning fewer groups. Real replay requires four distinct holding colors and stable historical slot identities. These are not native phone OCR/full-level validation. The exact reported Level 24 phone failure still needs on-device confirmation after installing v0.8.

Build/toolchain/signing/delivery remain as documented below. APK versionCode 8; v0.7 upgrades in place, v0.6's different certificate requires uninstalling Bead Pilot (not the game). Sources are extracted under android/ and a source ZIP accompanies the APK under downloads/.

---

# Current debugging continuation — v0.7

Working repository source is now extracted in android/. The old source-ZIP-only description below is historical. Current workspace is /workspace/bead-pilot-publish; /workspace/bead-out is the preserved v0.5 baseline.

User said v0.6 failed and they could not tell whether it did anything. Source comparison confirmed substantive v0.6 changes in AutoLayout, AutomaticRootMain and generated tests. v0.6 was NOT a renamed v0.5. We retained its hue-independent walls and shape support and fixed reproduced real-frame regressions rather than blindly reverting all changes.

Reproduction: unmodified v0.6 test-auto.sh passed 34 interpretation and 293 generated checks, then failed on the first actual gameplay frame with “Waiting for a supported, closed loop outline.” Lowering its rigid band threshold exposed damaged cup footprints (all candidates blocked), feeder texture branches, geometry resets on moving beads, and queue tails shrinking during refill. These defects are covered by the expanded real-frame sequence regression.

v0.7 changes: preserve complete cup components and use opening only for wall-connected components; remove rim/shadow halo from wall occupancy; offset observed closed rails with real shaded bead support and retain validated skeleton support; save trackOutline in Config and compare rails instead of moving bead centerlines; smooth filled feeder bodies, locate remote ends and compare shared feeder corridors; recognize visibly empty tinted rail floors while rejecting synthetic opaque occlusion. Previously observed loop geometry may survive complete collection only while its same closed rail is observed. Full geometry checks run about every 1.5 s; failures withhold taps, transient failures retain tracker up to bounded recovery, real geometry changes need two observations. Search and actual input guards remain.

Visibility: notification now shows status, optional floating status+STOP is draggable, activity displays/copies last 20 timestamped events (RAM only). No screenshot logs/files were introduced. Root-not-ready Start commands are retained for delivery at ready. Reports waiting for geometry, OCR, movement, safe choice or fresh capture rather than only a generic active label.

3,420 host checks passed (295 generated adaptive +88 real-frame +34 interpretation +3,003 previous domain/perception checks). v0.6's generated checks alone did not establish real-frame compatibility. Video file/frames remain host-only and are excluded from archives. No complete phone gameplay or native OCR validation has been performed; do not call the user-reported phone issue fully solved until live evidence supports that.

Signing: v0.7 uses v0.5 certificate SHA256 35c7fe5a3f8493bcad71c8e87b7c260e5539663ddf153f3a6ed673780ec4046c. v0.6 has a different signing certificate, so uninstall Bead Pilot v0.6 before installing v0.7 (not Bead Out). Original debug keystore remains outside repo at /workspace/bead-tools/android-user/debug.keystore. Never include it in archives.

Download delivery uses normal GitHub HTTPS URLs under downloads/. Verify final binary/hash/release before sharing. GitHub upload-assets credentials failed previously; git push works for binary files below 100 MB. Preserve v0.5/v0.6 artifacts and history.

---

# Bead Pilot debugging handoff

## Current development changes (v0.6)

The archive's Android project is now extracted as editable source under `android/` in this repository. The previous v0.5 artifacts in `downloads/` are preserved. The experimental v0.6 APK and SHA-256 file are added there alongside them.

- `AutoLayout` segments visible enclosure structures by background contrast rather than blue hue, separates thin borders from square bodies, retains unknown squares as obstacles, and requires enclosure evidence before declaring exit paths safe.
- Loop extraction follows the cycle of the observed bead-band skeleton after removing open branches. It supports the tested concave shape without offsetting normals across its indentation. It rejects open, unsupported or intersecting outlines.
- Feeders are traced outside the observed loop, including bent/upper branches. There are no invented fixed side paths. A drained tail can retain its own previously observed path only with unchanged loop/slots and visible background along the missing portion; occlusion does not qualify.
- `AutomaticRootMain` rechecks complete geometry before collector taps. Changed geometry requires two consistent observations and resets the tracker, pending plan, slot generations and OCR epoch. Failed detection clears stability; stale OCR and stale frames cannot enable collector taps.
- `AdaptiveLayoutTest` adds 293 generated-pixel checks: oval, rounded-rectangle and concave loops; six border colors; changing counts and arrangements; bent/upper feeders; flat and unknown squares; same-color touching border; blocked exits; drained/occluded queues; missing geometry and temporal stability. The checks are included in `test-auto.sh`.

The cloud toolchain is at `/workspace/bead-tools`: source `env.sh` for JDK 17, SDK platform 36/build-tools 35.0.0, platform-tools, managed Gradle proxy and CA trust. Work in `/workspace/bead-pilot/android`. Validation runs all three host scripts (3,330 checks), `:app:assembleDebug` and `:app:lintDebug`. The generated v0.6 APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

These changes address confirmed code assumptions, but the specific phone failure has not been reproduced: no new gameplay frames or rooted device were available. The historical real-frame replay suite was not rerun. The generated APK uses a new debug signing certificate, different from the published v0.5 APK, so an in-place update requires the original signing key. The v0.6 APK is supplied under `downloads/`; no GitHub Release was created because release API access is blocked. The following sections retain the historical v0.5 context.

## Current request and reported bug

The user wants a fresh debugging session because v0.5 is bugging. Their latest clarification: **the square layout changes, the enclosure/border outline color changes, and the loop changes shape between levels.** Exact failure symptoms, current level screenshot and status/error text have not yet been captured. Do not invent a diagnosis or claim it is fixed.

Primary investigation: automatic detection must work across changing layouts, wall colors and loop topology. Current detector includes blue-wall assumptions and geometry heuristics derived from the supplied clip. Repeated frames from one layout are not evidence of generalization. Inspect wall segmentation, square accessibility, track/hole extraction, feeder junctions and model resets at level transitions. Unknown or low-confidence geometry must prevent taps; do not fall back to fixed coordinates or require manual tracing.

## User requirements

- Bead Out on rooted Android, KernelSU Next, OxygenOS 16, portrait 1440 x 3168.
- Fully on-device automatic play. No manual slot/square marking, color teaching, loop tracing or fixed square counts.
- Read captured pixels in RAM; no screenshot files or recording history on the phone. Root permission already granted.
- Four holding slots. Tapping a reachable square moves it into a slot; 100 matching beads fill it, then it leaves and frees the slot.
- Beads circulate in a middle loop; both outer FIFO queues refill gaps. Predict collector choices to avoid occupying all slots with stalled colors.
- Raised/taller squares are clickable; shorter/flat ones are blocked. Walls and other squares block escape paths to the four slots. Border outlines can change color.
- Black question-mark squares are unknown obstacles. They reveal color when they become clickable, BEFORE tapping. Never guess their hidden color or tap black squares.
- After a confirmed win: press Android Home, wait 3 seconds, reopen, then Next. Next commonly triggers ads; bounded Home/reopen recovery may skip them. Sometimes returns to Bead Out menu; recognize it and press Play.
- User wants simple installation and Start, not calibration instructions.

## Delivered version and repository

Repository: https://github.com/vkhoahuynh-droid/bead-pilot (public, user created it).
Release: https://github.com/vkhoahuynh-droid/bead-pilot/releases/tag/v0.5
APK: https://github.com/vkhoahuynh-droid/bead-pilot/raw/refs/heads/main/downloads/Bead-Pilot-v0.5.apk
Source ZIP: https://github.com/vkhoahuynh-droid/bead-pilot/raw/refs/heads/main/downloads/bead-pilot-v0.5-source.zip

The repository contains README, handoff and downloads; **Android sources are inside the source ZIP**, not an extracted Android tree. Unpack it to start debugging in a fresh environment.

v0.5 APK: versionCode 5, com.beadout.bot, 52,022,094 bytes.
SHA256: 1470cadd0485feabfc8aba6ffffdd0abf5cf34c45e854fb1b5455ec79f3a04e6
The HTTPS APK was downloaded back and its checksum verified.

Sandbox/workspace links did nothing in the user's client. Use normal GitHub HTTPS download links. GitHub release asset uploads returned 401, but normal git push worked; binaries were committed under downloads/. Creating repositories through the integration returned 403; the user created this repository manually. Do not ask for tokens.

## Current workspace, if retained

- Working project: /workspace/bead-out
- GitHub publishing clone: /workspace/bead-pilot-publish
- Android code: /workspace/bead-out/android/app/src/main/java/com/beadout/bot
- Final build log: /workspace/bead-tools/final-build-v05.log
- APK/source: /workspace/bead-out/dist/bead-pilot-v0.5.{apk,zip} (source filename is bead-pilot-v0.5-source.zip)
- Gameplay video: /workspace/.codex-attachments/190d0d8e-1f49-412c-b627-10a3305174ed/be4c6e74-db9a-4f4d-b918-44f4b3f98952/VID_20261030_174134_1.mp4
- Host-only decoded frames: /workspace/bead-out/video-analysis/frames/001.png through 019.png, half-second steps.

The supplied file is 388 x 854, 9.102 seconds, encoded at 30 fps. User says original recording is 60 fps. Use timestamps, not assumed FPS. The video/frames are deliberately excluded from APK/source archive.

## Implementation map

- MainActivity: simple Start automatic play, Pause, Stop and optional detected-board preview. Five-second switch-to-game delay. No calibration UI.
- PilotService: app-side foreground service, root control pipe, optional floating STOP, offline OCR bridge. Launches AutomaticRootMain, not legacy RootMain. Preserves explicit root errors on exit.
- AutomaticRootMain: automatic discovery, package/foreground checks, capture, model tracking, bounded predictive taps, OCR requests and level cycle. Layout detection requires two stable frames. Rechecks squares about twice per second. Failed taps excluded temporarily, pauses after two failures. Stale OCR guarded by model epoch, slot generation, source colors and timestamps.
- AutoLayout: pure Java geometry detector. Finds 4 tray slots, colored and black board components, loop/inner-hole outline, outer feeder paths, bead-row pitch. Accessibility combines raised height and rectangular-footprint escape search. **Blue-wall assumptions and hole/track geometry heuristics need review for the newly reported changing borders/shapes.**
- AutoPerception: sample slots/board/loop/feeders from detected geometry; rowUnit 5, avoids duplicate connector samples.
- OcrReader: bundled ML Kit text-recognition 16.0.1, normal Android app process, RAM-only crops and bitmaps, one request at a time. Root does not initialize native ML Kit. Full scenes less frequently, small percentage ROI at most twice per second.
- TextScene: interpret OCR words/boxes, percentages in multiples of five, strict Congrats+Next and Play+LEVEL+Home scene checks.
- Tracker: legacy inventory mode and automatic progress mode. Automatic remaining capacity comes from OCR, tolerates approximate visual inventory, learns movement across multiple frames. Completion requires observed occupied-to-empty slot transition.
- Planner: row-weighted loop, gaps, finite FIFO feeders, candidate collector combinations, unlock score, bounded search 60 ms max twice per second; avoids simultaneous duplicate collector colors. Fourth slot guarded by predicted completion.
- Capture: root ImageReader display mirror, 582 pixels wide, mapped plane. Fallback screencap streamed/decode in RAM. UI preview and OCR PNGs only in RAM. Actual phone compatibility/resource usage unmeasured.
- Input: root Binder touch/Home, physical coordinate scaling.
- LevelCycle: confirmed win -> Home >=3 sec -> reopen -> Next; bounded ad recovery and menu Play; stops persistent failure.
- Config/Vision: automatic geometry/row weights and legacy helpers. RootMain and old manual code remain in source but are not used by the current app UI.

## What is verified and what is not

v0.5 assembled, lint passed, APK signature verified, no Internet or external-storage permission. Bundled offline OCR explains APK size; ACCESS_NETWORK_STATE remains from a dependency.

3,106 host checks passed:
- Planner/search 1,220; finite feeder comparisons 1,607; level cycle 49; recorded-gameplay model regression 76; legacy perception/tracking 51.
- Automatic-mode interpretation 34; actual-frame detection/motion and synthetic obstacle checks 69.

Frames identify 4 slots, 9 then 5 squares, one loop, two feeders; initially 3 eligible squares, later 2. Synthetic wall/unknown edits exercise blockers. Clip shows cyan and red stalling at 50% separately, motivating coordinated collectors. No square fully completes and no win/ad is recorded in that clip.

**No full autonomous level, actual phone ML Kit OCR accuracy, KernelSU input/capture compatibility, generalized layouts, arbitrary border colors or changed loop shapes have been verified.** Host OCR interpretation uses supplied words; it is not native OCR validation. Do not claim the new report is solved based on existing tests.

## Recommended next work

1. Inspect current screenshot/status to locate whether failure is slot detection, board detection, wall segmentation, loop extraction, OCR or planning. Continue code inspection without asking user to manually trace anything.
2. Remove fixed blue-wall and single-shape assumptions; separate enclosure from cup/bead colors using spatial structure, continuity and temporal evidence. Validate open corridors for cup-sized movement.
3. Verify loop and feeder connectivity for changed track shapes; explicitly handle low confidence or topology changes. Reset stale model/slot associations on real new-board detection.
4. Add meaningful replay cases for multiple actual layouts and border colors; test rejected geometry and safe rescan behavior, not just the original clip.
5. Build, lint, run targeted tests, increment version, retain signing key/application ID for updates, upload a new versioned artifact and HTTPS link. Explain precisely what was tested and what still needs phone validation.

## Build environment, if retained

JDK: /workspace/bead-tools/jdk (17)
SDK: /workspace/bead-tools/sdk (platform 36, build-tools 35.0.0)
Gradle: /workspace/bead-tools/gradle-8.11.1/bin/gradle
JAVA_HOME=/workspace/bead-tools/jdk
ANDROID_HOME=/workspace/bead-tools/sdk
ANDROID_USER_HOME=/workspace/bead-tools/android-user
GRADLE_USER_HOME=/workspace/bead-tools/gradle-user

Run from android/: gradle --offline :app:assembleDebug :app:lintDebug
Then ./test-engine.sh; ANDROID_HOME=... ./test-perception.sh; ANDROID_HOME=... ./test-auto.sh [optional host frame directory].
Gradle may need network-enabled tool permissions for local IPC even offline. For dependency downloads retain configured proxy/CA trust; previous commands used /etc/ssl/certs/java/cacerts and proxy:8080. Read the managed cloud runtime skill when resuming, and never print credential values.

Source archives must exclude build/.gradle/test-build/check-build/__pycache__/dist/video-analysis/local.properties, signing secrets and uploaded footage. No subagents requested or authorized.
