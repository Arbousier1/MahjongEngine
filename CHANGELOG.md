# Changelog

## 0.4.0 - 2026-03-17

CraftEngine and localization update release.

- Fixed the wall render layout so drawing from the live wall no longer causes all wall regions to shift and visually shake.
- Added localized config templates so a newly generated `config.yml` can use English, Simplified Chinese, or Traditional Chinese comments based on the system locale.
- Added `integrations.craftengine.items.tileItemIdPrefix` so all Mahjong tile visuals can resolve CraftEngine custom item ids from a configured prefix.
- Moved all message bundles into `src/main/resources/language/` and updated message index generation and runtime loading to match.
- Updated exported metadata authors to `openai and ellan`.

## 0.3.3 - 2026-03-17

Post-match seat and table state fix release.

- Fixed a bug where leaving your seat after a match ended could still leave you logically stuck in the table.
- Seat occupancy now falls back to the live participant registry once a round is no longer in progress, instead of reading stale finished-match engine seats.
- Added a regression test covering both finished-match seat removal and active-round engine seat usage.

## 0.3.2 - 2026-03-17

CraftEngine table furniture behavior update.

- Merged the built-in 3x3 table hitbox into `mahjongpaper:table_visual` so the default CraftEngine table is a single furniture entity.
- Changed table furniture handling so when `tableFurnitureId` is configured, MahjongPaper uses that furniture as-is, including all of its own hitboxes and behavior.
- Kept the fallback standalone table hitbox only for the built-in block-display table path when `tableFurnitureId` is left empty.

## 0.3.1 - 2026-03-17

Stability and interaction fix release.

- Fixed outdated test imports/packages so `./gradlew test jacocoTestReport` passes again in CI.
- Removed the green felt layer from both the built-in table render path and the CraftEngine `table_visual` model.
- Narrowed the CraftEngine hand-tile hitbox so adjacent hand tiles no longer overlap and mis-select under the pointer.

## 0.3.0 - 2026-03-17

Critical fix release.

- Fixed a severe bug in previous versions where the chair hitbox was positioned too high.
- Lowered the chair collision box by 0.5 blocks so seat interaction and collision align correctly.
- Users running any previous 0.2.x build should upgrade to 0.3.0 immediately.

## Release note

MahjongPaper 0.3.0 is a critical stability release.

Previous versions contained a severe bug in the chair collision/hitbox configuration, which could cause incorrect interaction and placement behavior around seats.

If you are using a 0.2.x version, upgrade to 0.3.0 as soon as possible.
