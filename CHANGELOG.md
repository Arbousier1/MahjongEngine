# Changelog

## 0.7.2 - 2026-03-25

Round-visibility and localization release.

- Added `tables.allowFreeMoveDuringRound` so players can leave seats and walk around during active rounds without being treated as having left the table.
- Updated seat dismount/sneak handling and round-start seat watchdog behavior to respect free-move mode.
- Localized the new free-move config comments in `config_zh_CN.yml` and `config_zh_TW.yml`.
- Added `mahjong-utils` to upstream references in `README.md` and `README.zh-CN.md`.

## 0.7.1 - 2026-03-25

Match flow stabilization release.

- Fixed a round-start regression where the previous hand settlement could reopen when a new hand began.
- Added MahjongPlay-style public action announcements in center text for resolved actions (`Pon`/`Chii`/`Kan`/`Ron`/`Riichi`/`Tsumo`/`Kyuushu`), with automatic fade-out timing.
- Improved action resolution detection to announce only when meld/reaction changes are actually applied.
- Cleared stale public action state at discard and new-round boundaries to avoid carry-over overlays.
- Added `table.last_action` localization key across default/Chinese bundles to keep wording consistent.

## 0.7.0 - 2026-03-25

MahjongPlay-style interaction alignment release.

- Aligned action overlay layout with MahjongPlay: fixed-facing action labels, seat-relative placement, and improved row arrangement.
- Added robust dynamic action-button spacing and hitbox sizing using wide-character aware width estimation to prevent label overlap in CJK locales.
- Reworked waiting controls so `Join` / `Ready` / `Leave` hide immediately when round-start dice flow begins (`roundStartInProgress`).
- Optimized submenu transitions to reduce flicker and keep button switching consistent.
- Simplified center/public text output to reduce overload while preserving last-discard ownership.
- Migrated actionable prompts out of chat-click buttons and into in-world controls while keeping suggestion text in chat.
- Added MySQL compatibility support alongside existing database options.
- Fixed and normalized Chinese localization bundles (zh_CN / zh_HK / zh_MO / zh_TW), including action labels and overlay wording consistency.

## 0.6.4 - 2026-03-24

Table and chair stability release / 牌桌与椅子稳定性版本

- Kept table and chair visual regions static during normal gameplay updates, so ready-state and game-info refreshes no longer trigger table/chair re-spawns.
- During regular render updates, MahjongPaper now refreshes board tiles and state overlays while preserving existing table/chair entities.
- Fixed chair interaction mapping to keep seat furniture focused on `JOIN_SEAT`, while ready toggles remain on seat labels/status layers.
- 在常规对局状态刷新中，牌桌与椅子区域保持静态，不再因准备状态或游戏信息更新而重建。
- 常规渲染只刷新牌桌上的麻将牌与状态信息图层，保留已有牌桌/椅子实体不动。
- 修正椅子交互映射：椅子保持用于 `JOIN_SEAT`（入座），准备切换由座位标签/状态层处理。

## 0.6.2 - 2026-03-22

Riichi added-kong display hotfix release.

- Fixed Riichi `kakan` meld projection so display data consistently exports three base tiles plus one stacked tile.
- Added Riichi regression coverage to lock the `kakan` shape (`3 base + 1 stacked`) and prevent future visual regressions.

## 0.6.1 - 2026-03-22

Hotfix and maintenance release.

- Fixed GB added-kong visual output so the meld renders as three base tiles with one stacked tile, matching expected table notation instead of showing an extra base tile.
- Tightened hand interaction hitbox dimensions to align with tile geometry for more precise click targeting.
- Added Dependabot configuration for weekly Gradle and GitHub Actions dependency update PRs.
- Added regression coverage for GB added-kong visual data shape.

## 0.6.0 - 2026-03-22

Turn-transition and meld-visual consistency release.

- Fixed a round-transition bug where replacing a seated player between hands could leave stale previous-hand seat ids in the active round controller, causing wrong discard ownership and blocked turns.
- Updated round startup to recreate the controller whenever live seat assignments no longer match controller seats.
- Fixed added-kong (`kakan`) table rendering so the fourth tile is stacked on top of the called horizontal tile and no longer disappears due to claim-anchor reset.
- Aligned `pon`/`open-kong` source-position mapping with left/middle/right display rules across both Riichi and GB controllers.
- Tightened tsumo-action prompts so actionable UI only shows tsumo when declaration is actually legal.
- Added regression tests for seat-change controller rebuilds, claim-source slot mapping, and kakan stacking behavior.

## 0.5.2 - 2026-03-18

Folia dice animation hotfix release.

- Fixed opening dice animation display teleports so Folia region tasks now use async-safe entity teleport handling instead of synchronous teleports.
- Kept the existing Paper compatibility path unchanged by routing the dice animation updates through the shared scheduler teleport wrapper.

## 0.5.1 - 2026-03-18

Folia compatibility hotfix release.

- Fixed CraftEngine viewer visibility scheduling so Folia no longer reads display-entity state from the viewer region thread during culling show and hide callbacks.
- Reworked tracked cullable metadata to use cached entity ids and UUIDs in cross-thread visibility paths, avoiding `Thread failed main thread check` crashes on active tables.

## 0.5.0 - 2026-03-18

Folia performance and opening flow release.

- Reduced redundant private-entity visibility synchronization so unchanged private viewer sets no longer trigger full `showEntity` and `hideEntity` passes.
- Cached per-viewer render snapshot metadata and switched wall-size consumers to count-only accessors to cut avoidable render flush allocations.
- Memoized GB bot ting evaluation inside discard, kan, and reaction searches to reduce repeated JNI-style scoring work during a single decision window.
- Added benchmark coverage for render snapshot creation and duplicated-hand GB bot discard suggestion so optimization work can be verified before release.

## 0.4.3 - 2026-03-17

Interaction and CraftEngine furniture alignment release.

- Added `/mahjong reload` so MahjongPaper can reload config/runtime services and re-render active tables without a full server restart.
- Fixed duplicate display-action handling so seat joins and ready toggles are no longer processed twice from overlapping entity interaction events.
- Reworked private hand interaction back to dedicated `Interaction` entities so hand tiles remain clickable while the tile visuals keep their movement animation.
- Adjusted custom CraftEngine table and chair furniture anchors so replacing `tableFurnitureId` or `seatFurnitureId` no longer leaves external furniture floating about 1 block and 6 pixels too high.

## 0.4.2 - 2026-03-17

Config template hotfix release.

- Fixed the default `tileItemIdPrefix` YAML value by quoting `mahjongpaper:` in all bundled config templates.
- Added a regression test that loads every bundled config template through Bukkit YAML parsing to prevent invalid defaults from shipping again.

## 0.4.1 - 2026-03-17

Config documentation clarification release.

- Expanded the `integrations.craftengine.items.tileItemIdPrefix` comments in all config templates.
- Clarified that the setting resolves CraftEngine custom item ids, not furniture entities.
- Documented the automatic tile-name suffix rules, including the `back` id for face-down tiles.
- Documented the fallback behavior when `preferCustomItems` is disabled or a CraftEngine custom item cannot be built.

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
