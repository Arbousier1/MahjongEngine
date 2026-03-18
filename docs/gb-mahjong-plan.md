# GB Mahjong Branch Plan

Branch: `feature/gb-mahjong`

Reference rules source:

- <https://github.com/zheng-fan/GB-Mahjong>
- See also: `docs/gb-mahjong-rules.md`

## Reference Summary

The referenced `GB-Mahjong` project is a C++ library for Chinese Official Mahjong (Guobiao / GB Mahjong).
According to its README, it is primarily based on the 1998 `中国麻将竞赛规则（试行）`, with scoring behavior aligned to common practice from large tournaments such as the World Mahjong Sports Games and major Chinese GB Mahjong events.
This branch treats that repository as the authoritative gameplay/scoring target for the future GB variant.

The library explicitly provides:

- fan counting
- ting calculation
- ASCII hand-string parsing
- detailed per-fan composition output

## What Is Already Landed

The branch is no longer only planning work. The following pieces are already in place:

- table/session runtime can switch between `RIICHI` and `GB`
- `MahjongTableSession` now uses a round-controller abstraction instead of directly hard-binding to `RiichiRoundEngine`
- GB tables use `GbTableRoundController`
- the GB JNI bridge is wired to a vendored copy of `zheng-fan/GB-Mahjong`
- GB flow now supports:
  - start/deal
  - flower tiles and supplement draws
  - discard
  - ting prompts
  - ron / tsumo
  - chi / pon / open kan / concealed kan / added kan
  - robbing-kong reaction windows
  - multi-ron settlement aggregation
- settlement UI has a GB-specific presentation branch
- bot scheduling and command behavior are variant-aware at runtime

## Remaining Gap

This repository is currently tightly coupled to a riichi ruleset:

- round engine: `src/main/kotlin/doublemoon/mahjongcraft/paper/riichi/RiichiRoundEngine.kt`
- rule model and settlement types: `src/main/kotlin/doublemoon/mahjongcraft/paper/riichi/model/`
- runtime actions and table flow still expose riichi-specific concepts such as:
  - riichi
  - tsumo / ron
  - red fives
  - dora / ura dora
  - chankan / rinshan / haitei handling
- settlement UI and messages are also riichi-oriented

Because of that, GB support still is not “finished forever”. The remaining work is mostly calibration and production hardening rather than raw branch bootstrap.

## Remaining Implementation Focus

1. Native build verification in CI and release packaging.
   - the source is vendored and wired, but this workstation does not have a native toolchain
2. Rule-detail calibration against more upstream cases.
   - especially edge-case flags and tournament-specific settlement expectations
3. Round progression beyond the current single-hand controller focus.
   - prevailing wind / dealership advancement for long GB matches can still be refined
4. More translation coverage for GB fan names and player-facing copy.
5. Broader regression coverage.
   - native integration tests once CI can build the JNI library

## Expected Hotspots

- `src/main/kotlin/doublemoon/mahjongcraft/paper/riichi/RiichiRoundEngine.kt`
- `src/main/java/doublemoon/mahjongcraft/paper/table/core/MahjongTableSession.java`
- `src/main/java/doublemoon/mahjongcraft/paper/table/core/PersistentTableStore.java`
- `src/main/java/doublemoon/mahjongcraft/paper/command/MahjongCommand.java`
- `src/main/java/doublemoon/mahjongcraft/paper/ui/SettlementUi.java`

## JNI Direction

This branch is now on a real JNI path rather than a stub-only path.

- JVM bridge docs: `docs/gb-mahjong-jni.md`
- native project: `native/gbmahjong/`
- upstream vendored source: `native/gbmahjong/vendor/GB-Mahjong/`

## Practical Constraint

`GB-Mahjong` is a C++ project, not a JVM library, so it cannot be dropped into this plugin directly.
With JNI chosen, the practical constraint is now mostly:

- native packaging for each target platform
- CI verification of the shared library build
- keeping the plugin-side table flow and the upstream fan engine aligned over time
