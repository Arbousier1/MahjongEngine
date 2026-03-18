# GB Mahjong Status And Roadmap

This document tracks the current implementation state of GB Mahjong in the `dev` branch and the most likely follow-up areas.

Reference rules source:

- <https://github.com/zheng-fan/GB-Mahjong>
- See also: [gb-mahjong-rules.md](./gb-mahjong-rules.md)
- JNI implementation notes: [gb-mahjong-jni.md](./gb-mahjong-jni.md)

## Current Status

GB Mahjong is no longer a placeholder branch experiment. It is integrated into the live table/session runtime and can be selected as a real ruleset through table rule presets.

What is already landed:

- table runtime can switch between `RIICHI` and `GB`
- `MahjongTableSession` delegates round behavior through a round-controller abstraction
- GB tables use `GbTableRoundController`
- GB tables support flower tiles and supplement draws
- GB reaction windows support chi, pon, open kan, concealed kan, added kan, ron, and tsumo
- robbing-kong handling is wired into GB reaction flow
- settlement UI has GB-aware output
- bot scheduling is variant-aware
- command handling hides or blocks ruleset-specific actions where appropriate
- the native GB evaluator is wired through a vendored copy of `GB-Mahjong`

## Important Boundaries

GB support is live, but it should not be described as “done forever”.

The current implementation deliberately splits ownership like this:

- upstream `GB-Mahjong` owns fan counting, ting analysis, and win evaluation rules
- MahjongPaper owns table flow, spectator state, rendering, persistence, UI, and region-thread-safe scheduling

That means future work is more about hardening, coverage, and compatibility than about bootstrapping the ruleset from scratch.

## Stable Hotspots In The Codebase

When changing GB behavior, these files are usually the highest-signal places to inspect first:

- `src/main/java/doublemoon/mahjongcraft/paper/table/core/MahjongTableSession.java`
- `src/main/java/doublemoon/mahjongcraft/paper/table/core/round/GbTableRoundController.java`
- `src/main/java/doublemoon/mahjongcraft/paper/table/core/round/TableRoundController.java`
- `src/main/java/doublemoon/mahjongcraft/paper/command/MahjongCommand.java`
- `src/main/java/doublemoon/mahjongcraft/paper/ui/SettlementUi.java`
- `src/main/java/doublemoon/mahjongcraft/paper/gb/runtime/GbNativeRulesGateway.java`
- `src/main/kotlin/doublemoon/mahjongcraft/paper/gb/jni/GbMahjongNativeModels.kt`

## Remaining Roadmap

### 1. Native Build And Release Hardening

The JNI source and Gradle tasks already exist, but release discipline still matters:

- confirm native builds for each supported target platform
- validate bundled extraction paths on Windows, Linux, and macOS
- verify that packaged jars include the expected native sidecar files

### 2. Rule Calibration Against More Upstream Cases

The implementation direction is locked to `GB-Mahjong`, but complex edge cases still benefit from more parity testing:

- unusual flower-heavy hands
- multiple simultaneous reactions
- robbing-kong edge cases
- low-level flag mapping around last tile / after kong / robbed kong situations

### 3. Broader Acceptance Coverage

The codebase already includes GB controller tests, but practical coverage should continue expanding:

- multi-hand live validation
- table persistence and reload behavior while GB tables exist
- UI and localization checks for GB-specific settlement output
- more regression coverage for bot behavior and suggestion stability

### 4. Player-Facing Documentation And Translation

GB support now deserves first-class docs and copy quality, not just developer notes:

- keep command help and README coverage aligned with actual GB behavior
- expand translated player-facing fan names and descriptions where needed
- maintain a practical live checklist for real server validation

## What This Is Not

This project is not aiming at a vague “Chinese-style Mahjong” mode with loosely interpreted rules.

The intended contract is:

- MahjongPaper follows the gameplay/session model of this plugin
- GB legality and fan behavior are anchored to the upstream `GB-Mahjong` rules engine

If behavior diverges, the preferred fix is usually to examine request mapping, settlement translation, or plugin-side state flow before inventing a new local rule interpretation.
