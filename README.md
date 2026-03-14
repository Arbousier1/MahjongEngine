# MahjongPaper

`MahjongPaper` is a Paper plugin rewrite scaffold for `MahjongCraft` that uses:

- Paper `ItemDisplay` and `TextDisplay`
- `ItemMeta#setItemModel(...)` with a matching resource pack
- PacketEvents entity interaction packets for tile clicking

## Status

This repository currently includes a playable Paper-based Riichi Mahjong port foundation:

- table creation and joining
- 4-seat riichi wall generation with red fives
- dealing, drawing and discarding
- riichi, tsumo, ron, chii, pon, minkan, ankan and kakan flows
- yaku / fu / han / score settlement through `mahjong-utils`
- furiten checks, chankan, rinshan kaihou, suufon renda, suucha riichi, suukaikan and kyuushu kyuuhai
- nagashi mangan handling on exhaustive draw
- display-entity table rendering with hidden-information hands
- owner-only face-up hand rendering plus public face-down hand rendering
- PacketEvents click-to-discard interaction
- automatic settlement inventory UI and clickable reaction prompts
- pre-round rule configuration and lobby summaries
- local filler bots for unattended seats
- spectator mode with packet-gated private overlays and private hand/front visibility control
- per-viewer localized HUD overlays, turn prompts and settlement messaging
- MiniMessage-based player messaging with `zh-CN` / fallback English localization
- locale-aware number formatting in command and settlement surfaces
- H2-backed round history persistence by default, with optional MariaDB support and startup schema creation

It is still not fully at feature parity with upstream `MahjongCraft`. The largest remaining gaps are the original mod's more specialized client UX surface, additional board choreography/animation polish, and broader end-to-end parity verification against every upstream interaction edge case.

## Commands

- `/mahjong help`: show the in-game command help with explanations
- `/mahjong create`: create a new table at your position
- `/mahjong join <tableId>`: join an existing table as a player
- `/mahjong leave`: leave your current table or stop spectating
- `/mahjong list`: show active tables and their locations
- `/mahjong spectate <tableId>`: watch a table without taking a seat
- `/mahjong unspectate`: stop spectating the current table
- `/mahjong addbot`: fill one empty seat with a bot
- `/mahjong removebot`: remove one bot before the round starts
- `/mahjong rule [key] [value]`: view or change table rules before starting
- `/mahjong start`: start the round when 4 seats are filled
- `/mahjong state`: show the current table and round state
- `/mahjong riichi <index>`: declare riichi and discard the chosen tile index
- `/mahjong tsumo`: claim a self-draw win if your hand is ready
- `/mahjong ron`: claim a win on another player's discard
- `/mahjong pon`: call pon on the current reaction window
- `/mahjong minkan`: call an open kan on the current reaction window
- `/mahjong chii <tileA> <tileB>`: call chii with the listed two tiles from your hand
- `/mahjong kan <tile>`: declare ankan or kakan with the given tile kind
- `/mahjong skip`: pass the current reaction opportunity
- `/mahjong kyuushu`: declare nine terminals and honors abortive draw
- `/mahjong settlement`: reopen the latest settlement UI for this table
- `/mahjong render`: force a table display refresh
- `/mahjong clear`: remove current display entities for the table

## Build

```powershell
.\gradlew.bat build
```

The plugin expects the PacketEvents plugin to be installed on the server because it is declared as a dependency in `plugin.yml`.
The project now also ships [`paper-plugin.yml`](./src/main/resources/paper-plugin.yml) for Paper-native plugin metadata and dependency loading; `plugin.yml` is still kept for command and permission registration.
Database settings live in [`config.yml`](./src/main/resources/config.yml); the default database type is `h2`, and you can switch `database.type` to `mariadb` if needed. Round settlements are written asynchronously to `round_history` and `round_player_result`.

## Resource Pack

The matching pack is in [`resourcepack`](./resourcepack).
It is authored in the 1.21.11-style `pack.mcmeta` format using `min_format` / `max_format` with resource pack version `75.0`.

For the current prototype the plugin uses the `mahjongcraft` namespace and binds tile displays to paths like:

- `mahjongcraft:mahjong_tile/m1`
- `mahjongcraft:mahjong_tile/east`
- `mahjongcraft:mahjong_tile/back`

## Upstream References

- `MahjongCraft`: gameplay and assets source
- `Paper`: display entities and `item_model` API
- `PacketEvents`: interaction packet bridge
