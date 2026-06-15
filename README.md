# MahjongPaper

> This project was created entirely by AI.

Chinese documentation: [README.zh-CN.md](./README.zh-CN.md)

`MahjongPaper` is a Paper plugin rewrite of `MahjongCraft` built around:

- Paper display entities
- `ItemMeta#setItemModel(...)` plus the bundled resource pack
- CraftEngine custom items, furniture hitboxes, and culling integration

## Current Scope

The current branch already supports playable Riichi Mahjong and GB Mahjong flows on Paper/Folia:

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
- `/mahjong mode <MAJSOUL_TONPUU|MAJSOUL_HANCHAN|GB>`: apply a preset before the next start
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
- GB Mahjong rule source: [docs/gb-mahjong-rules.md](./docs/gb-mahjong-rules.md)

## Gameplay Positioning

- Newly created tables default to Mahjong Soul-style Riichi hanchan rules.
- `MAJSOUL_TONPUU` and `MAJSOUL_HANCHAN` are the primary presets; both use three red fives, open tanyao, multi-ron, and the Mahjong Soul kan-dora reveal profile.
- `GB` remains available as an optional ruleset, backed by the vendored `GB-Mahjong` native bridge.

## Game Modes Explained (with Examples)

The plugin ships three independent rulesets. Switch between them before a hand starts with `/mahjong mode <MODE>`. Each is explained below with concrete tile examples.

### Shared Round Flow

Every mode uses the same play loop:

1. `/mahjong create`: create an empty table at your feet.
2. Click one of the east/south/west/north floating seat labels to sit down, or use `/mahjong join <tableId>`.
3. Fill empty seats with `/mahjong addbot` (bots count as ready).
4. `/mahjong start` toggles ready; the hand auto-starts once all 4 seats are filled and ready.
5. **Discard**: on your turn, click a tile in your hand to discard it.
6. **React to others**: after someone discards, a reaction window opens. Use commands to call:
   - `/mahjong pon`, `/mahjong chii <tileA> <tileB>` (from the player to your left), `/mahjong minkan`
   - `/mahjong ron` (win on a discard), `/mahjong skip` (pass)
7. **On your own turn**: `/mahjong tsumo` (self-draw win), `/mahjong kan <tile>` (closed or added kan).
8. A settlement screen pops up when the hand ends; reopen it with `/mahjong settlement`.

> Riichi-only commands: `/mahjong riichi <handIndex>` (declare riichi and discard that tile) and `/mahjong kyuushu` (nine-terminals abortive draw on the first turn). Both work on Riichi tables only.

### Mode 1: Mahjong Soul Riichi (default)

The primary mode, aimed at players familiar with Japanese / Mahjong Soul rules.

- **Match length**: `MAJSOUL_HANCHAN` is a half-game (East 1 through South 4, 8 hands); `MAJSOUL_TONPUU` is East-only (East 1 through East 4, 4 hands).
- **Points**: everyone starts at 25,000 with a 30,000 return target; final placement is by score.
- **Red fives**: one red 5 each in man/pin/sou (3 total), each worth one dora.
- **Winning floor**: 1 han minimum, and you must have a yaku (open tanyao is enabled, so melded hands can still score).
- **Riichi**: declare when closed and the wall still has draws left; you pay 1000 points and may only discard the tile you just drew.

**Example**: you self-draw a red 5-man after declaring riichi.

```
Menzen tsumo (1 han) + Riichi (1 han) + your hand's yaku + red-five dora (1 han) ...
```

Stacking more yaku pushes the han higher; yakuman hands (kokushi, daisangen, etc.) pay a fixed large score. The appeal is betting on riichi, reading discards, and combining yaku.

### Mode 2: GB Mahjong (Chinese Official)

China's official competition rules, the richest in fan types and the highest barrier. Switch with `/mahjong mode GB`.

- **Tiles**: man/pin/sou plus honor tiles (winds and dragons), with flower tiles (plum/orchid/bamboo/chrysanthemum) as bonus draws.
- **Winning floor**: **8 fan minimum** — hands worth fewer than 8 fan cannot win. This is the biggest difference from the other modes.
- **Scoring**: fan accumulate per the official fan table; more fan means more points.
- **Evaluation**: fan are judged by the bundled `GB-Mahjong` native library, following the official interpretation strictly.

**Example**: you build "Half Flush + All Triplets".

```
Half Flush (6 fan) + All Triplets (6 fan) = 12 fan >= 8 fan -> win allowed
```

With only "All Triplets (6 fan)" you fall short of 8 fan and **cannot win**, so you must keep building toward a bigger hand. GB pushes you toward complex, high-value hands — ideal for players who want depth.

### Mode 3: Sichuan Mahjong (Bloody Battle)

A fast, intense regional style. Switch with `/mahjong mode SICHUAN`.

- **Suited tiles only**: man/pin/sou, **no honor tiles and no flowers**.
- **Missing-a-suit (ding que)**: your winning hand must drop one entire suit (e.g. only man and pin, zero sou tiles).
- **Bloody battle (xue zhan dao di)**: a win does **not** end the hand immediately. Winners step out and the rest keep playing until the third player also wins, then the whole hand settles. So a single hand can produce up to 3 winners.
- **Any-fan win**: unlike GB's 8-fan floor, Sichuan lets you win once you complete a basic shape.
- **Fan cap**: capped at 5 fan (32x scoring).

**Main fan types**:

| Fan | Value | Notes |
| --- | --- | --- |
| Chicken Hand | 1 | basic shape |
| All Triplets | 1 | all triplets (pon/kan) |
| Full Flush | 2 | one suit only |
| Seven Pairs | 2 | seven pairs |
| Dragon Seven Pairs | 3 | seven pairs with 1 "root" (four identical) |
| Double Dragon Seven Pairs | 4 | seven pairs with 2 roots |
| Deluxe Dragon Seven Pairs | 5 | seven pairs with 3 roots (cap) |
| All 2-5-8 Pairs | +2 | every tile is 2/5/8 (stacks with all-triplets or seven pairs) |
| Root | +1 each | each kong adds one fan |
| Golden Hook | +1 | all-triplets waiting on a single pair tile |
| Under the Sea / Kong Bloom / Robbing the Kong, etc. | +1 | special winning methods |

**Example**: you drop sou, build a full-flush seven-pair hand, and one set is four pin tiles (1 root).

```
Full Flush (2 fan) + seven pairs upgraded to Dragon Seven Pairs (3 fan, includes 1 root) = 5 fan -> capped, scored at 32x
```

**Scoring**: fan map to a base of $2^{fan}$. On a self-draw the other three each pay a share; on a discard win only the discarder pays. That "one player pays for the deal-in" rule makes Sichuan especially tense.

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

## Community

- Contributing guide: [.github/CONTRIBUTING.md](./.github/CONTRIBUTING.md)
- Code of conduct: [.github/CODE_OF_CONDUCT.md](./.github/CODE_OF_CONDUCT.md)
- Security policy: [.github/SECURITY.md](./.github/SECURITY.md)
- Support: [.github/SUPPORT.md](./.github/SUPPORT.md)

## License

This project is licensed under the [MIT License](./LICENSE).
