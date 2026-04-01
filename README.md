# MahjongPaper

> This project was created entirely by AI.

Chinese documentation: [README.zh-CN.md](./README.zh-CN.md)

`MahjongPaper` is a Paper plugin rewrite of `MahjongCraft` built around:

- Paper display entities
- `ItemMeta#setItemModel(...)` plus the bundled resource pack
- CraftEngine custom items, furniture hitboxes, and culling integration

## Current Scope

The current branch already supports playable Riichi Mahjong, GB Mahjong, and an in-progress Sichuan Mahjong flow on Paper/Folia:

- lobby-style tables that can persist across restart
- empty-table creation, fixed east/south/west/north seats, click-to-join, and click-to-ready
- automatic round start once 4 seats are filled and all seated players are ready
- bots for unattended seats, with bots treated as ready by default
- leaving before the round starts, or deferred leave after the current hand ends
- dealing, drawing, discarding, riichi, tsumo, ron, chii, pon, minkan, ankan, and kakan
- opening dice roll animation and live public table state displays
- Riichi scoring through `mahjong-utils`
- GB Mahjong rule evaluation through the bundled JNI bridge and vendored `GB-Mahjong` source
- spectator mode, private hand visibility, HUD overlays, and localized prompts
- Mahjong Soul-style rank persistence when database-backed ranking is enabled
- CraftEngine-backed seat/table interaction and CraftEngine bundle export
- round history persistence through H2 by default, with optional MariaDB/MySQL

## Command Summary

- `/mahjong help`: show in-game command help
- `/mahjong create`: create a new empty table at your location
- `/mahjong botmatch [hanchan|tonpuu]`: create a 4-bot test match and spectate it
- `/mahjong mode <MAJSOUL_TONPUU|MAJSOUL_HANCHAN|GB|SICHUAN>`: apply a preset before the next start
- `/mahjong join <tableId>`: join a table as a player
- `/mahjong leave`: leave immediately before the hand starts, or queue a leave after the current hand
- `/mahjong list`: list active tables and their locations
- `/mahjong start`: toggle ready status for your seat
- `/mahjong spectate <tableId>`: spectate a table
- `/mahjong unspectate`: stop spectating
- `/mahjong addbot` and `/mahjong removebot`: manage bots before the round starts
- `/mahjong rule [key] [value]`: view or change table rules before the next start
- Riichi multi-ron mode: `/mahjong rule ronMode <HEAD_BUMP|MULTI_RON>`
- Riichi flow profile: `/mahjong rule riichiProfile <MAJSOUL|TOURNAMENT>`
- `/mahjong state`: show the current table summary
- `/mahjong riichi <index>`, `/mahjong tsumo`, `/mahjong ron`, `/mahjong pon`, `/mahjong minkan`, `/mahjong chii <tileA> <tileB>`, `/mahjong kan <tile>`, `/mahjong skip`, `/mahjong kyuushu`: round actions
- `/mahjong settlement`: reopen the latest settlement UI
- `/mahjong rank`: show your Mahjong Soul-style rank summary when ranking is enabled
- `/mahjong render`, `/mahjong clear`, `/mahjong inspect`: table render maintenance and diagnostics
- `/mahjong forceend [tableId]`: admin command to stop a running match
- `/mahjong deletetable [tableId]`: admin command to delete a table
- `/mahjong reload`: admin command to reload configuration and re-render active tables

Admin targeting details:

- `forceend` and `deletetable` accept an explicit `tableId`
- if omitted, they first try the table you are in or spectating
- if you are not attached to a table, they fall back to the nearest table within range

## Lobby Flow

- `/mahjong create` creates an empty table only; it does not auto-seat the creator
- players join a fixed seat by interacting with that seat's floating label
- the same seat label is used to toggle ready before the match starts
- a round starts automatically only when all 4 seats are filled and all seated players are ready
- after a hand or a full match ends, players must ready again before the next start

## Rule References

- Riichi round flow rules: [docs/riichi-round-flow.md](./docs/riichi-round-flow.md)

## Sichuan Rules (Blood Battle, Chengdu-style)

The current `SICHUAN` mode implements mainstream Chengdu blood-battle flow and settlement:

- `Rule profile`: suited tiles only (`M/P/S`), no honors/flowers, and no `chii`.
  Code: [GbRuleProfile.java](./src/main/java/top/ellan/mahjong/table/core/round/GbRuleProfile.java)
- `Auto dingque + forced discard`: each player gets an auto-selected missing suit; while missing-suit tiles remain, only that suit can be discarded.
  Code: [GbTableRoundController.java](./src/main/java/top/ellan/mahjong/table/core/round/GbTableRoundController.java) (`assignSichuanMissingSuits`, `canDiscardBySichuanMissingSuit`)
- `Blood battle to the end`: winners leave active turn order; the hand ends when only one active player remains or the wall exhausts.
  Code: [GbTableRoundController.java](./src/main/java/top/ellan/mahjong/table/core/round/GbTableRoundController.java) (`recordSichuanWins`, `activeSichuanPlayerCount`, `finishSichuanBloodBattle`)
- `Sichuan fan engine`: `ping hu / dui dui hu / qing yi se / qi dui / long qi dui / qing dui / jiang dui / gen`, plus `gang shang hua`, `gang shang pao`, and `qiang gang hu`.
  Code: [GbTableRoundController.java](./src/main/java/top/ellan/mahjong/table/core/round/GbTableRoundController.java) (`evaluateSichuanFanResponse`)
- `Gang economy`: immediate gang settlement for concealed/open/added kong, plus call-transfer (`hu jiao zhuan yi`) on post-kong discard ron.
  Code: [GbTableRoundController.java](./src/main/java/top/ellan/mahjong/table/core/round/GbTableRoundController.java) (`applySichuanKanSettlement`, `applySichuanGangTransferOnRon`)
- `Exhaustive draw penalties`: hua-zhu penalty, cha-jiao style noten-to-tenpai settlement, and gang-tax refund for noten players.
  Code: [GbTableRoundController.java](./src/main/java/top/ellan/mahjong/table/core/round/GbTableRoundController.java) (`resolveSichuanExhaustiveDraw`)

Validation tests:

- [GbTableRoundControllerTest.kt](./src/test/kotlin/top/ellan/mahjong/table/core/round/GbTableRoundControllerTest.kt)
  Includes Sichuan checks for dingque enforcement, blood-battle progression, gang scoring, call transfer, and exhaustive-draw hua-zhu handling.

## Build

```powershell
.\gradlew.bat build
```

The plugin currently declares CraftEngine as a required dependency in [plugin.yml](./src/main/resources/plugin.yml), so CraftEngine must be present at runtime.

## Configuration

The default config lives at [src/main/resources/config.yml](./src/main/resources/config.yml).

The current human-friendly layout is:

- `database.connection`: database type and MariaDB/MySQL connection target
- `database.credentials`: MariaDB/MySQL username and password
- `database.h2`: local embedded H2 settings
- `database.pool`: connection pool sizing
- `tables.persistence`: persistent table restore file
- `ranking`: Mahjong Soul-style room presets and rank persistence
- `integrations.craftengine`: CraftEngine export and interaction preferences
- `debug`: debug logging switches

Notes:

- configuration is snapshotted on load
- `/mahjong reload` reloads config, rebuilds CraftEngine bridges, and re-renders active tables
- older flat keys are still accepted for backward compatibility

## CraftEngine

When CraftEngine is installed, MahjongPaper exports a bundle to:

- `plugins/CraftEngine/resources/mahjongpaper`

The exported bundle includes:

- `pack.yml`
- `configuration/items/mahjong_tiles.yml`
- `resourcepack/assets/mahjongcraft/...`

MahjongPaper currently uses CraftEngine for:

- custom mahjong tile items
- table and seat hitbox furniture
- tracked entity culling integration
- furniture interaction routing for table interaction

## Resource Pack

The bundled resource pack lives in [resourcepack](./resourcepack).

## Upstream References

- `MahjongCraft`: <https://github.com/EndlessCheng/MahjongCraft>
- `MahjongPlay`: <https://github.com/7yunluo/MahjongPlay>
- `mahjong-utils`: <https://github.com/ssttkkl/mahjong-utils>
- `Paper`: <https://papermc.io/software/paper>
- `CraftEngine`: <https://github.com/Xiao-MoMi/craft-engine>
- `GB-Mahjong`: <https://github.com/zheng-fan/GB-Mahjong>
- `mahjong_graphic`: <https://github.com/lietxia/mahjong_graphic>
