# MahjongPaper（中文）

> This project was created entirely by AI.  
> 本项目由 AI 生成与维护。

English documentation: [README.md](./README.md)

`MahjongPaper` 是基于 Paper 的麻将插件，当前支持：

- 日麻（Riichi）
- 国标麻将（GB）
- 四川麻将（Sichuan，持续开发中）

## 指令概览

- `/mahjong mode <MAJSOUL_TONPUU|MAJSOUL_HANCHAN|GB|SICHUAN>`：切换规则预设
- `/mahjong rule [key] [value]`：查看或修改开局前规则
- `/mahjong state`：查看当前牌桌状态
- `/mahjong riichi` `/mahjong tsumo` `/mahjong ron` `/mahjong pon` `/mahjong minkan` `/mahjong chii` `/mahjong kan` `/mahjong skip`：对局动作

## 四川麻将实现（中英对照 + 代码对应）

- `规则档位` / `Rule profile`
  四川使用独立档位：仅万筒条、无字牌花牌、禁吃（chii disabled）。
  代码：[`GbRuleProfile.java`](./src/main/java/top/ellan/mahjong/table/core/round/GbRuleProfile.java)

- `定缺（当前自动）` / `Missing suit (auto for now)`
  开局后自动为每位玩家选择定缺门（当前按最少张数，平手按 M>P>S）。
  代码：[`GbTableRoundController.java`](./src/main/java/top/ellan/mahjong/table/core/round/GbTableRoundController.java) 中 `assignSichuanMissingSuits`、`autoSelectSichuanMissingSuit`

- `强制定缺出牌` / `Forced missing-suit discard`
  在手里仍有定缺门牌时，只允许打该门。
  代码：[`GbTableRoundController.java`](./src/main/java/top/ellan/mahjong/table/core/round/GbTableRoundController.java) 中 `canSelectHandTile`、`canDiscardBySichuanMissingSuit`

- `胡牌限制` / `Win restrictions`
  胡牌路径要求“缺一门”且“已打光定缺门”；听牌（ting）也会按同样规则过滤。
  代码：[`GbTableRoundController.java`](./src/main/java/top/ellan/mahjong/table/core/round/GbTableRoundController.java) 中 `satisfiesSichuanWinRestrictions`、`evaluateFanResponse`、`evaluateTing`

- `血战到底骨架` / `Blood battle skeleton`
  已胡玩家退出本局轮转，按四川流程累计赢家直到结束条件触发。
  代码：[`GbTableRoundController.java`](./src/main/java/top/ellan/mahjong/table/core/round/GbTableRoundController.java) 中 `recordSichuanWins`、`finishSichuanBloodBattle`

## 测试对应

- [`GbTableRoundControllerTest.kt`](./src/test/kotlin/top/ellan/mahjong/table/core/round/GbTableRoundControllerTest.kt)
  - `sichuan profile disables chii reactions`
  - `sichuan discard must follow selected missing suit`
  - `sichuan tsumo uses local hu evaluation`
  - `sichuan tsumo fails when selected missing suit is not cleared`

## 构建

```powershell
.\gradlew.bat build
```
