# MahjongPaper

`MahjongPaper` 是 `MahjongCraft` 的 Paper 插件化重写脚手架，目前主要基于：

- Paper 的 `ItemDisplay` 与 `TextDisplay`
- 搭配资源包使用的 `ItemMeta#setItemModel(...)`
- 使用 PacketEvents 处理牌点击交互数据包

## 当前状态

这个仓库目前已经包含一个可游玩的 Paper 版日麻基础移植：

- 创建牌桌并加入牌桌
- 四人立直麻将牌山生成，支持赤宝牌
- 发牌、摸牌、打牌
- 立直、自摸、荣和、吃、碰、明杠、暗杠、加杠流程
- 通过 `mahjong-utils` 进行役种 / 符 / 番 / 点数结算
- 振听检查、抢杠、岭上开花、四风连打、四家立直、四开杠、九种九牌
- 荒牌流局时支持流局满贯判定
- 基于显示实体的牌桌渲染与隐藏信息手牌显示
- 本人可见正面手牌，其他玩家可见背面手牌
- 基于 PacketEvents 的点击打牌交互
- 自动结算界面与可点击反应提示
- 开局前规则配置与大厅摘要显示
- 空位自动补本地 Bot
- 观战模式，包含基于数据包的私有 overlay 与私有手牌/正面可见性控制
- 按玩家本地化的 HUD、回合提示与结算消息
- 基于 MiniMessage 的玩家消息，默认提供 `zh-CN`，英文作为回退
- 命令输出与结算界面的本地化数字格式化
- 默认使用 H2 持久化对局历史，也可切换到 MariaDB，并在启动时自动建表
- 支持导出 CraftEngine 资源包，并通过 CraftEngine 提供桌子碰撞与实体裁剪
- 持久化大厅式牌桌，服务器重启后仍可恢复，并在对局开始前先显示空桌

目前它还没有和上游 `MahjongCraft` 完全功能对齐。最大的差距主要在于：原模组更完整的客户端交互体验、更细的牌桌动画与表现，以及对所有上游边界行为的逐项一致性验证。

## 命令

- `/mahjong help`：显示游戏内命令帮助
- `/mahjong create`：在当前位置创建新牌桌
- `/mahjong botmatch [hanchan|tonpuu]`：创建并启动一桌 4 Bot 测试对局，然后自动进入观战
- `/mahjong mode <tonpuu|hanchan>`：应用雀魂风格的东风战或半庄战预设
- `/mahjong join <tableId>`：作为玩家加入现有牌桌
- `/mahjong leave`：离开当前牌桌或退出观战
- `/mahjong list`：查看当前活动牌桌及其位置
- `/mahjong spectate <tableId>`：以观战者身份旁观牌桌
- `/mahjong unspectate`：停止观战当前牌桌
- `/mahjong addbot`：为一个空座位补入 Bot
- `/mahjong removebot`：在开局前移除一个 Bot
- `/mahjong rule [key] [value]`：在开局前查看或修改规则
- `/mahjong start`：四个座位坐满后开始对局
- `/mahjong state`：显示当前牌桌和牌局状态
- `/mahjong riichi <index>`：宣告立直并打出指定索引的牌
- `/mahjong tsumo`：满足条件时宣告自摸
- `/mahjong ron`：对他人打出的牌宣告荣和
- `/mahjong pon`：在当前反应窗口宣告碰
- `/mahjong minkan`：在当前反应窗口宣告大明杠
- `/mahjong chii <tileA> <tileB>`：使用手牌中的两张牌宣告吃
- `/mahjong kan <tile>`：使用指定牌种宣告暗杠或加杠
- `/mahjong skip`：跳过当前反应机会
- `/mahjong kyuushu`：宣告九种九牌流局
- `/mahjong settlement`：重新打开当前牌桌最近一次结算界面
- `/mahjong render`：强制刷新牌桌显示实体
- `/mahjong clear`：清理当前牌桌的显示实体
- `/mahjong forceend [tableId]`：管理员命令，强制中止对局并将牌桌恢复到等待状态
- `/mahjong deletetable [tableId]`：管理员命令，立即删除牌桌

当前新建牌桌默认使用雀魂风格规则：四人立直麻将、起始点数 25000、目标点数 30000、启用赤宝牌、启用食断，终局采用无乌马，顺位点为 `+15/+5/-5/-15`。

## 构建

```powershell
.\gradlew.bat build
```

由于在 `plugin.yml` 中声明了依赖，服务器运行时需要安装 PacketEvents。

当前构建产物为 thin jar。运行时依赖由 [`paper-plugin.yml`](./src/main/resources/paper-plugin.yml) 中声明的 Paper 插件加载器解析；`plugin.yml` 仍然保留用于命令和权限注册。

数据库配置位于 [`config.yml`](./src/main/resources/config.yml)。默认数据库类型是 `h2`，如果需要可以切换 `database.type` 为 `mariadb`。每一局结算会异步写入 `round_history` 和 `round_player_result`。

当前分支推荐的运行组合：

- Paper
- PacketEvents
- CraftEngine

即使没有 CraftEngine，MahjongPaper 仍然可以运行；但按当前实现，接入 CraftEngine 才能获得更完整的表现。桌子碰撞会导出成 CraftEngine furniture，显示实体裁剪也会桥接到 CraftEngine 的 tracked-entity culling，而不再使用 MahjongPaper 早期那套本地裁剪服务。

## 资源包

匹配的资源包位于 [`resourcepack`](./resourcepack)。

它使用 1.21.11 风格的 `pack.mcmeta` 格式，采用 `min_format` / `max_format`，当前资源包版本为 `75.0`。

在目前的原型里，插件使用 `mahjongcraft` 命名空间，并把牌面显示绑定到如下路径：

- `mahjongcraft:mahjong_tile/m1`
- `mahjongcraft:mahjong_tile/east`
- `mahjongcraft:mahjong_tile/back`

## CraftEngine

如果服务器安装了 CraftEngine，MahjongPaper 默认会在启动时把一份可直接使用的 CraftEngine bundle 导出到 `plugins/CraftEngine/resources/mahjongpaper`。

导出的 bundle 包含：

- `pack.yml`
- `configuration/items/mahjong_tiles.yml`
- `resourcepack/assets/mahjongcraft/...`

导出的物品配置现在还会额外包含 `mahjongpaper:table_hitbox`，MahjongPaper 会把它作为 CraftEngine furniture 用来承载牌桌碰撞体积。

当前 CraftEngine 集成包括：

- 自定义牌物品，例如 `mahjongpaper:east`
- 基于 CraftEngine furniture 的牌桌碰撞
- 基于 CraftEngine tracked `Cullable` 实体的显示实体裁剪

当 CraftEngine 存在且其自定义物品可用时，牌桌显示和结算界面中的牌会优先使用导出的 CraftEngine 物品 ID，例如 `mahjongpaper:east`；如果不可用，则回退为直接使用 `item_model` 的物品显示方式。

注意：如果你希望 MahjongPaper 的显示实体裁剪真正生效，需要在 CraftEngine 自己的配置里开启 entity culling。MahjongPaper 已经不再提供旧的 `clientOptimization.entityCulling` 独立配置段。

## 上游参考

- `MahjongCraft`：玩法逻辑与资源来源
- `Paper`：显示实体与 `item_model` API
- `PacketEvents`：交互数据包桥接
