# Changelog

## 0.9.0-beta.1 - 2026-06-15

Test release for single-jar Paper 1.21.11 through 26.2 compatibility, Sichuan flow validation, and render/network performance hardening.

中文更新日志:

- 单 jar 多版本兼容: 默认发布包固定为 Java 21 字节码与 `api-version: 1.21.11`, 用同一个 jar 面向 Paper 1.21.11 到 26.2, 避免为 26.x 单独打包。
- Paper 兼容验证: 升级 paperweight 到 2.0.0-SNAPSHOT, 默认使用 Paper 1.21.11 开发包构建, CI 额外使用 Paper 26.2 开发包做源码/API 兼容测试。
- 26.1.2 实服验证: 在 Paper 26.1.2 build 70 + CraftEngine 26.6.2 上完成启动冒烟测试, MahjongPaper 正常加载、初始化 H2 数据库、完成 GB 原生规则预热并导出 CraftEngine 资源包。
- Adventure 兼容: Adventure 依赖改为 `compileOnly`, 并对齐 Paper 1.21.11 到 26.2 当前 API 使用的 Adventure 4.26.1, 避免插件 jar 内携带冲突的 Adventure 运行时。
- 运行库更新: 更新 MariaDB、MySQL、H2、HikariCP、Kotlin 与 kotlinx-serialization 等运行依赖, 降低新 Paper/Java 环境下的兼容风险。
- 性能与网络优化: 渲染区域刷新先检查预算再生成实体规格, 减少无效渲染计算、展示实体重刷和潜在发包压力。
- 可见性同步优化: 减少私有/排除可见玩家集合比较时的临时集合分配, 降低高频展示实体同步的内存抖动。
- 旁观者覆盖层优化: 只扫描 `viewer-overlay:` 区域, 不再每次复制并过滤全部渲染区域 key。
- 区块刷新优化: 牌桌 3x3 区块加载检查改为直接查询 `World#isChunkLoaded`, 避免刷新循环中创建临时集合。
- 四川麻将规则验证: 增加血战到底流程测试, 确认点炮胡后对局继续, 已胡玩家不再行动, 第三位玩家胡牌后结算整手牌。
- 国标/四川测试稳定性: 修正暗杠补牌测试牌山、明杠优先级测试手牌和测试用碰牌状态注入方式, 避免测试误判。
- 结算 UI 测试修复: 补充当前玩法变体 mock, 保持立直结算 UI 集成测试与多玩法接口一致。
- 发布护栏: 新增单 jar 兼容测试, 自动检查打包后的 `plugin.yml` / `paper-plugin.yml` 固定最低 API, 并校验字节码目标。
- 文档与 CI: 更新贡献文档中的 26.2 验证命令, CI 同时覆盖 1.21.11 默认发布目标和 26.2 高版本 API 兼容目标。

English Release Notes:

- Single-jar compatibility: release artifacts stay on Java 21 bytecode with `api-version: 1.21.11`, so one jar targets Paper 1.21.11 through 26.2.
- Paper compatibility: upgraded paperweight to 2.0.0-SNAPSHOT, kept the default build on the 1.21.11 dev bundle, and added CI/API validation against the 26.2 dev bundle.
- Server smoke test: verified startup on Paper 26.1.2 build 70 with CraftEngine 26.6.2; MahjongPaper enabled, initialized H2, warmed the GB native bridge, and exported its CraftEngine bundle.
- Adventure alignment: Adventure dependencies are now compile-only and aligned to Paper's current 4.26.1 API line to avoid shipping conflicting Adventure runtime classes.
- Runtime dependency refresh: updated database, pool, Kotlin, and serialization libraries for newer Paper/Java compatibility.
- Render/network performance: render-region updates now check budgets before building entity specs, reducing unnecessary render work, display respawns, and packet pressure.
- Visibility performance: reduced temporary set allocation while reconciling private/excluded display viewers.
- Viewer overlay and chunk checks: overlay cleanup scans only viewer-overlay regions, and table-area chunk checks avoid temporary neighborhood sets.
- Sichuan rules validation: added blood-battle flow coverage where discard win continues the hand and the hand settles after the third winner.
- Test hardening: stabilized GB/Sichuan round tests, settlement UI variant mocks, and added single-jar descriptor/classfile compatibility checks.

## 0.8.0-beta.1 - 2026-04-01

Test release for post-0.7.5 architecture and gameplay fixes.

Version list after `0.7.5`:

- `0.8.0-beta.1` (this release)
- No other tagged versions were published between `0.7.5` and this beta.

中文更新日志（简体）:

- 会话状态架构重构：引入 `SessionState` 抽象，统一会话生命周期、规则切换、渲染刷新、轮转控制与延迟离桌处理，降低 `MahjongTableSession` 的耦合复杂度。
- 机器人策略分层：新增 `BotStrategy` / `BotStrategyFactory`，将立直与国标 AI 行为拆分为 `RiichiBotStrategy` 与 `GbBotStrategy`，便于后续扩展四川规则与变体特化策略。
- 跨规则回合防卡死：修复机器人出牌重试逻辑，针对“不可选手牌位”“出牌失败后仍在当前回合”等边界场景添加恢复路径，避免回合卡住。
- 国标出牌可选性联动：新增 `canSelectHandTile(...)` 对外校验通道，机器人不再盲打非法索引，确保与 UI/规则限制一致。
- 听牌计算稳定性增强：`RiichiPlayerState` 增加 `shanten` 失败降级策略，在主策略抛出 `NoSuchElement` 等异常时自动回退到稳定全量扫描策略，减少异常中断。
- JNI 桥接与文档同步：调整国标 JNI 相关包结构与文档，统一本地规则网关、预热与调用边界，提升维护可读性。
- 数据与性能测试补强：补充/强化 H2 与 MariaDB 跨方言一致性测试、GB 原生网关与预热服务测试、渲染协调器指标与性能基准测试。
- 加杠渲染修复（重点）：加杠牌不再“抬高叠放”，改为“与目标牌同平面并向桌心内移”，修复牌面突出桌边的视觉问题。
- 渲染回归用例更新：`TableRendererTest` 从“高度差断言”改为“同平面 + 距桌心更近断言”，并补齐距离计算辅助函数，确保 `dev` 分支可编译通过。

English Release Notes:

- Session-state architecture refactor: introduced `SessionState` to centralize lifecycle, rule mutation, render refresh, round-flow control, and deferred-leave handling with lower `MahjongTableSession` coupling.
- Bot strategy layering: added `BotStrategy` and `BotStrategyFactory`, splitting variant logic into `RiichiBotStrategy` and `GbBotStrategy` for cleaner extension toward Sichuan/variant-specific AI.
- Cross-variant turn-stall prevention: fixed bot turn retry/recovery paths for edge cases like non-selectable discard slots and failed discard retries while the bot is still the current actor.
- GB discard legality alignment: exposed `canSelectHandTile(...)` so bot discards obey the same hand-index legality constraints as player UI/rule validation.
- Shanten stability improvements: `RiichiPlayerState` now promotes to fallback full-scan strategies when primary shanten evaluation fails (notably `NoSuchElement` class failures), reducing runtime interruption risk.
- JNI bridge and docs alignment: updated GB JNI package/layout boundaries and documentation for clearer warmup, gateway, and native invocation responsibilities.
- Test and reliability coverage expansion: added/updated cross-dialect DB consistency tests, GB native warmup/gateway tests, and render coordinator/performance benchmark coverage.
- Added-kan render fix (key): `kakan` tile placement is now planar and shifted inward toward table center, instead of being raised/stacked above the claimed tile.
- Render regression assertions updated: tests now validate same-plane placement plus center-inward movement, and include the helper needed for stable `dev` branch compilation.

## 0.7.5 - 2026-03-31

Riichi declaration behavior and rule-alignment release.

- Enforced post-riichi tsumogiri behavior: after declaring riichi, only the freshly drawn tile can be discarded.
- Updated hand-tile selection rules so UI interaction also restricts riichi players to the drawn tile.
- Aligned declaration-ron handling with standard riichi rules: if the declaration tile is ron'ed, the riichi deposit is refunded and that riichi declaration is cancelled.
- Added/updated regression tests covering riichi discard constraints, controller tile-selection behavior, and declaration-ron deposit refund handling.

## 0.7.4 - 2026-03-31

Table direction and release stability update.

- Switched table display direction mapping to the intended riichi-style counterclockwise seat flow.
- Aligned wall/hand/meld rendering orientation so the in-game east/south/west/north placement now matches the configured direction model.
- Fixed table bounds anchoring so the table body follows tile-layout direction changes instead of staying in the old position.
- Updated riichi and render regression tests to lock the new orientation behavior.
- Stabilized GB `minkan` priority test assertions by filtering flower-display rows from meld-count checks.

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
