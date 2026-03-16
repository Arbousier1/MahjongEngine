# MahjongPaper

英文说明见 [README.md](./README.md)。

`MahjongPaper` 是 `MahjongCraft` 的 Paper 插件重写版本，目前主要基于：

- Paper 的 `ItemDisplay` 和 `TextDisplay`
- 配套资源包使用的 `ItemMeta#setItemModel(...)`
- CraftEngine 提供的家具交互与实体剔除

## 最新说明

- `/mahjong start` 现在用于切换准备状态；4 家坐满且全部准备后才会自动开始。
- 东南西北悬浮字支持固定座位加入和准备交互。
- `/mahjong inspect` 会显示桌体锚点和棒子方向的私有调试粒子。
- 一局结束后不会直接开始下一局，所有玩家需要重新准备。
- `config.yml` 的配置会在插件启动时统一读取；修改后请重启服务器进程让新配置生效。

## 当前状态

当前仓库已经包含一个可游玩的 Paper 版日麻基础移植，覆盖：

- 牌桌创建、加入与观战
- 固定东南西北座位与点击加入/准备流程
- 四人立直麻将牌山生成与赤宝牌
- 发牌、摸牌、打牌
- 立直、自摸、荣和、吃、碰、明杠、暗杠、加杠
- 通过 `mahjong-utils` 进行役种、符、番、点数结算
- 振听、抢杠、岭上开花、四风连打、四家立直、四开杠、九种九牌
- 荒牌流局时的流局满贯判断
- 基于显示实体的牌桌渲染与隐藏信息处理
- 本人正面手牌、其他玩家背面手牌
- 基于 CraftEngine 的点击打牌交互
- 自动结算界面与可点击反应提示
- 开局前规则配置与大厅摘要
- 本地 Bot 补位
- 观战模式与私有 HUD / 手牌可见性控制
- 多语言 HUD、回合提示和结算消息
- 默认 H2 持久化，可选 MariaDB
- CraftEngine bundle 导出、桌面 hitbox 与实体剔除桥接
- 可持久化的大厅式牌桌，重启后仍可恢复

## 指令

- `/mahjong help`：显示游戏内帮助
- `/mahjong create`：在当前位置创建新牌桌
- `/mahjong botmatch [hanchan|tonpuu]`：创建并启动一桌 4 Bot 测试对局，然后进入观战
- `/mahjong mode <tonpuu|hanchan>`：应用雀魂风格的东风战或半庄战预设
- `/mahjong join <tableId>`：作为玩家加入牌桌
- `/mahjong leave`：离开当前牌桌或退出观战
- `/mahjong list`：查看活动牌桌及其位置
- `/mahjong spectate <tableId>`：以观战者身份旁观牌桌
- `/mahjong unspectate`：退出当前观战
- `/mahjong addbot`：为一个空位补入 Bot
- `/mahjong removebot`：在开局前移除一个 Bot
- `/mahjong rule [key] [value]`：查看或修改牌桌规则
- `/mahjong start`：切换准备状态；4 家全部准备后自动开始
- `/mahjong state`：查看当前牌桌和牌局状态
- `/mahjong riichi <index>`：宣告立直并打出指定索引的牌
- `/mahjong tsumo`：在满足条件时宣告自摸
- `/mahjong ron`：对他人打出的牌宣告荣和
- `/mahjong pon`：在当前反应窗口宣告碰
- `/mahjong minkan`：在当前反应窗口宣告大明杠
- `/mahjong chii <tileA> <tileB>`：用手牌中的两张牌宣告吃
- `/mahjong kan <tile>`：宣告暗杠或加杠
- `/mahjong skip`：跳过当前反应机会
- `/mahjong kyuushu`：宣告九种九牌流局
- `/mahjong settlement`：重新打开最近一次结算界面
- `/mahjong render`：强制刷新牌桌显示
- `/mahjong inspect`：发送桌体与棒子方向调试信息
- `/mahjong clear`：移除当前牌桌显示实体
- `/mahjong forceend [tableId]`：管理员强制结束当前对局
- `/mahjong deletetable [tableId]`：管理员立即删除牌桌

## 构建

```powershell
.\gradlew.bat build
```

运行时需要安装 CraftEngine，因为它已在 `plugin.yml` 中声明为依赖。

当前产物是 thin jar。运行时依赖由 [paper-plugin.yml](./src/main/resources/paper-plugin.yml) 中声明的 Paper 插件加载器解析；`plugin.yml` 仍保留用于指令和权限注册。

数据库配置位于 [config.yml](./src/main/resources/config.yml)。默认数据库类型为 `h2`，如有需要可切换 `database.type=mariadb`。每一局结算会异步写入 `round_history` 和 `round_player_result`。

## 资源包

配套资源包位于 [resourcepack](./resourcepack)。

## CraftEngine

安装 CraftEngine 后，MahjongPaper 默认会在启动时把 bundle 导出到 `plugins/CraftEngine/resources/mahjongpaper`。

当前 CraftEngine 集成包括：

- 自定义麻将牌物品，如 `mahjongpaper:east`
- 牌桌 hitbox 家具
- 基于 CraftEngine tracked `Cullable` 的显示实体剔除

## 上游参考

- `MahjongCraft`
- `Paper`
- `CraftEngine`
