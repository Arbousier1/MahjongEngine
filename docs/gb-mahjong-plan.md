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

## Current Codebase Gap

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

Because of that, implementing GB Mahjong is not a small rules toggle. It requires extracting or replacing the current riichi-first game engine assumptions.

## Recommended Implementation Order

1. Introduce a ruleset abstraction above the current round engine.
   - Define a game-variant boundary so table/runtime code does not directly depend on `RiichiRoundEngine`.
2. Split rule-neutral table state from riichi-specific state.
   - seat occupancy
   - wall / hand / discard ownership
   - meld visibility
   - turn order
3. Add a GB-specific engine package.
   - suggested package root: `doublemoon.mahjongcraft.paper.gb`
4. Implement GB settlement data structures.
   - fan list
   - total fan
   - flower handling
   - minimum fan threshold
5. Adapt commands and UI so they are ruleset-aware.
   - hide riichi-only actions on GB tables
   - render GB settlement terminology
6. Add persistence/versioning for the table ruleset.
   - existing stored tables currently deserialize directly into riichi-flavored rule objects

## Expected Hotspots

- `src/main/kotlin/doublemoon/mahjongcraft/paper/riichi/RiichiRoundEngine.kt`
- `src/main/java/doublemoon/mahjongcraft/paper/table/core/MahjongTableSession.java`
- `src/main/java/doublemoon/mahjongcraft/paper/table/core/PersistentTableStore.java`
- `src/main/java/doublemoon/mahjongcraft/paper/command/MahjongCommand.java`
- `src/main/java/doublemoon/mahjongcraft/paper/ui/SettlementUi.java`

## JNI Direction

This branch is currently taking a JNI-first path for the GB backend.

- JVM bridge docs: `docs/gb-mahjong-jni.md`
- native stub project: `native/gbmahjong/`

## Practical Constraint

`GB-Mahjong` is a C++ project, not a JVM library, so it cannot be dropped into this plugin directly.
With JNI chosen, the practical constraint becomes API surface design and native packaging instead of direct JVM reimplementation.
