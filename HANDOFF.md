# Bead Pilot debugging handoff

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
