# MahjongPaper Wiki

本页整理 MahjongPaper 当前支持的三种玩法模式，以及插件在服务器中的安装、配置和日常使用方式。它面向两类读者：玩家可以照着开桌、入座、操作；服主可以照着部署、授权、排查。

## 快速概览

| 项目 | 当前行为 |
| --- | --- |
| 服务端 | Paper/Folia，当前构建面向 Paper API `1.21.11` |
| Java | Java 21 |
| 必需依赖 | `CraftEngine` |
| 主命令 | `/mahjong` |
| 普通权限 | `mahjongpaper.command`，默认所有玩家可用 |
| 管理权限 | `mahjongpaper.admin`，默认 OP 可用 |
| 默认模式 | `MAJSOUL_HANCHAN`，雀魂风格半庄立直麻将 |
| 可选模式 | `MAJSOUL_TONPUU`、`MAJSOUL_HANCHAN`、`GB`、`SICHUAN` |
| 配置文件 | `plugins/MahjongPaper/config.yml` |
| CraftEngine 导出目录 | `plugins/CraftEngine/resources/mahjongpaper` |

## 安装与首次启动

1. 准备 Paper 或 Folia 服务端，并确保运行环境是 Java 21。
2. 安装 `CraftEngine`。MahjongPaper 在插件描述中把 CraftEngine 声明为必需依赖，因此服务端运行时必须存在。
3. 把 MahjongPaper 的 jar 放入服务器 `plugins` 目录。
4. 启动服务器。插件会生成 `plugins/MahjongPaper/config.yml`，并在检测到 CraftEngine 后导出资源 bundle。
5. 确认 `plugins/CraftEngine/resources/mahjongpaper` 中出现导出的资源。该目录通常包含 `pack.yml`、`configuration/items/mahjong_tiles.yml` 和 `resourcepack/assets/mahjongcraft/...`。
6. 修改配置后可使用 `/mahjong reload` 重载配置并重渲染活动牌桌；安装依赖、替换 jar 或调整服务端级资源时，仍建议完整重启服务器。

## 牌桌生命周期

一张牌桌从创建到结束大致是这个流程：

1. 管理员使用 `/mahjong create` 在当前位置创建空牌桌。
2. 玩家点击东、南、西、北四个座位悬浮标签入座，也可以使用 `/mahjong join <table_id>` 加入。
3. 开局前可使用 `/mahjong mode <mode>` 切换玩法，或用 `/mahjong rule <key> <value>` 调整部分规则。
4. 缺人时可用 `/mahjong addbot` 补 Bot。Bot 默认视为已准备。
5. 玩家点击自己的座位悬浮标签，或使用 `/mahjong start` 切换准备状态。
6. 四个座位坐满并全部准备后，牌局自动开始。
7. 一局结束后弹出结算界面；可用 `/mahjong settlement` 重新打开最近一次结算。
8. 一局或整场结束后，玩家需要再次准备，才会开始下一局或新一场。

开局前离开会立即退座。开局后使用 `/mahjong leave` 会标记为本局结束后离开。

## 三种玩法模式

### 模式一：雀魂风格立直麻将

使用方式：

- `/mahjong mode MAJSOUL_HANCHAN`：默认半庄，东 1 到南 4。
- `/mahjong mode MAJSOUL_TONPUU`：东风战，东 1 到东 4。

核心规则：

- 使用万、筒、索、字牌和三枚赤宝牌。
- 默认起点 `25000`，返还目标 `30000`。
- 至少 1 番起胡，并且必须有役。
- 默认开启食断、多家荣和、雀魂风格杠宝牌揭示时机。
- 门清听牌且满足条件时可立直，立直后只能打刚摸到的牌。
- 支持自摸、荣和、吃、碰、明杠、暗杠、加杠、九种九牌。

常用命令：

| 操作 | 命令 |
| --- | --- |
| 立直并打出指定手牌序号 | `/mahjong riichi <hand_index>` |
| 自摸 | `/mahjong tsumo` |
| 荣和 | `/mahjong ron` |
| 吃 | `/mahjong chii <tile_a> <tile_b>` |
| 碰 | `/mahjong pon` |
| 明杠 | `/mahjong minkan` |
| 暗杠或加杠 | `/mahjong kan <tile>` |
| 九种九牌流局 | `/mahjong kyuushu` |
| 放弃反应 | `/mahjong skip` |

例子：你门清听牌，使用 `/mahjong riichi 13` 宣告立直并打出第 13 号手牌。之后若自摸，可以用 `/mahjong tsumo` 结算；如果别人打出你的和牌，可以在反应窗口用 `/mahjong ron`。

### 模式二：国标麻将

使用方式：

- `/mahjong mode GB`

核心规则：

- 使用万、筒、索、风牌、三元牌和花牌。
- 花牌会公开补花，并从牌墙后方补牌。
- 8 番起胡，番数不足 8 番不能和牌。
- 支持吃、碰、明杠、暗杠、加杠、抢杠、荣和、自摸。
- 番种、听牌和合法和牌判断由内置 JNI bridge 调用 vendored `GB-Mahjong` 规则库完成。
- MahjongPaper 负责牌桌流程、反应窗口、结算 UI、玩家提示和持久化。

玩法重点：

- 国标不是泛泛的“中国麻将”模式，而是尽量对齐 `GB-Mahjong` 对国标规则的解释。
- 低番手牌不能胡，需要围绕 8 番门槛规划手牌。
- 结算界面会显示总番数、番种明细和点数变化。

例子：`混一色` 6 番加 `碰碰和` 6 番，总计 12 番，满足 8 番门槛，可以胡。只有 `碰碰和` 6 番时，番数不足，不能胡。

### 模式三：四川麻将

使用方式：

- `/mahjong mode SICHUAN`

当前实现采用血战到底方向：

- 只使用万、筒、索三门序数牌，共 108 张。
- 不使用字牌、花牌、赤宝牌。
- 胡牌必须缺一门，也就是手牌和副露中最多只保留两门花色。
- 四川模式不允许吃牌，主要操作是碰、杠、荣和、自摸。
- 玩家胡牌后退出本局继续结算，剩余玩家继续打，直到只剩一名未胡玩家或牌墙耗尽。
- 一局最多产生 3 名和牌者。
- 番数上限为 5 番，计分单位按 `2^番数` 计算。

当前支持的主要番种：

| 番种 | 番数 | 说明 |
| --- | --- | --- |
| 平胡 | 1 | 基础和牌牌型 |
| 对对胡 | 1 | 全刻子或杠子 |
| 清一色 | 2 | 全部牌只来自一门花色 |
| 七对 | 2 | 七个对子 |
| 龙七对 | 3 | 七对中有 1 个四张相同的“根” |
| 双龙七对 | 4 | 七对中有 2 个根 |
| 豪华龙七对 | 5 | 七对中有 3 个根，达到封顶 |
| 将对 | 叠加或折算 | 全部牌为 2、5、8，按当前牌型由引擎合并计算 |
| 根 | 额外加番 | 普通牌型中每个四张相同计 1 根 |
| 金钩钓 | 额外加番 | 对对胡单钓将 |
| 海底、杠上花、杠上炮、抢杠胡 | 额外加番 | 按对应和牌方式结算 |

额外结算：

- 暗杠、明杠、加杠会产生杠分。
- 荒牌时会处理花猪和查叫相关点数。
- 末段牌墙存在可胡情况时，插件会限制跳过可胡机会。

例子：你缺索，做成清一色七对，其中一组牌是四张相同的筒子。它会按 `清一色` 加 `龙七对` 组合计到 5 番封顶，按 32 倍单位结算。

## 玩家操作指南

### 查找和进入牌桌

| 目的 | 操作 |
| --- | --- |
| 查看附近或活动牌桌 | `/mahjong list`，需要管理员权限 |
| 加入指定牌桌 | `/mahjong join <table_id>` |
| 点击入座 | 点击座位悬浮标签 |
| 观战 | `/mahjong spectate <table_id>` |
| 退出观战 | `/mahjong unspectate` |
| 离开座位 | `/mahjong leave` |
| 打开牌桌面板 | `/mahjong table [table_id]` |

### 准备和开始

| 目的 | 操作 |
| --- | --- |
| 切换准备状态 | 点击自己的座位标签，或使用 `/mahjong start` |
| 查看当前桌状态 | `/mahjong state` |
| 查看当前规则 | `/mahjong rule` 或 `/mahjong table` |
| 切换模式 | 桌主/管理员在 `/mahjong table` 或 `/mahjong mode <MAJSOUL_TONPUU|MAJSOUL_HANCHAN|GB|SICHUAN>` 中调整 |

建议只在开局前或两局之间切换模式。牌局进行中切换模式容易让玩家误解当前局的实际规则。

### 打牌和反应

自己的回合可以点击手牌打出。别人打出牌后，如果你能吃、碰、杠或胡，插件会给出反应提示，使用对应命令提交。

牌名参数使用内部牌名的小写形式，例如：

| 牌 | 参数 |
| --- | --- |
| 1 万 | `m1` |
| 赤 5 万 | `m5_red` |
| 9 筒 | `p9` |
| 3 索 | `s3` |
| 东 | `east` |
| 中 | `red_dragon` |

例子：

```text
/mahjong chii m3 m4
/mahjong kan p5
```

### 结算和段位

| 目的 | 命令 |
| --- | --- |
| 重新打开最近结算 | `/mahjong settlement` |
| 查看雀魂风格段位 | `/mahjong rank` |
| 查看模式排行榜 | `/mahjong leaderboard [RIICHI|GB|SICHUAN]` |

段位系统依赖数据库和 `ranking.enabled: true`。如果数据库未启用，`/mahjong rank` 会提示不可用。

## 服主管理指南

### 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `mahjongpaper.command` | true | 允许使用 `/mahjong` 主命令 |
| `mahjongpaper.admin` | OP | 允许创建牌桌、测试桌、维护渲染、强制结束、删除牌桌、重载配置 |

管理员命令：

| 命令 | 用途 |
| --- | --- |
| `/mahjong create` | 在当前位置创建牌桌 |
| `/mahjong botmatch [hanchan|tonpuu]` | 创建 4 Bot 立直测试桌并进入观战 |
| `/mahjong render` | 重新渲染当前牌桌 |
| `/mahjong inspect` | 显示渲染锚点和方向诊断 |
| `/mahjong clear` | 清理当前牌桌展示实体 |
| `/mahjong forceend [table_id]` | 强制结束当前或指定牌桌 |
| `/mahjong deletetable [table_id]` | 删除当前或指定牌桌 |
| `/mahjong reload` | 重载配置并重渲染活动牌桌 |

`forceend` 和 `deletetable` 的目标解析顺序是：显式传入的 `table_id`，玩家当前所在或观战的牌桌，最后是玩家附近最近的牌桌。

### 桌主与牌桌面板

`/mahjong create` 创建的牌桌会记录创建者为桌主，但不会自动让创建者入座。桌主或管理员可使用 `/mahjong table [table_id]` 打开牌桌控制面板，统一管理：

- 查看桌主、座位、准备和规则概况。
- 打开规则 GUI，切换模式或调整规则。
- 开局前添加/移除 Bot。
- 在 4 人满员且全部准备后手动开始。
- 刷新展示实体。
- 删除尚未开始的牌桌。
- 使用 `/mahjong table owner <玩家名> [table_id]` 把桌主转让给已经入座的在线玩家。

普通玩家也可以打开面板查看状态、准备、离桌、查看规则或结算，但不能修改规则、Bot 或删除牌桌。若桌主离开座位，桌主会转交给下一位真人玩家；重启后旧桌若没有记录桌主，第一位真人入座会成为桌主。

### 配置重点

配置文件位于 `plugins/MahjongPaper/config.yml`。当前主要配置块如下：

| 配置块 | 用途 |
| --- | --- |
| `database` | 数据库总开关、失败策略、连接类型 |
| `database.connection` | MariaDB/MySQL 地址、端口、库名和连接参数 |
| `database.credentials` | MariaDB/MySQL 用户名和密码 |
| `database.h2` | 本地 H2 数据库路径和参数 |
| `database.pool` | 数据库连接池大小和超时 |
| `tables.startupRebuildBatchSize` | 启动时分批恢复牌桌展示的批量大小 |
| `tables.allowFreeMoveDuringRound` | 是否允许牌局中自由移动 |
| `tables.persistence` | 牌桌持久化文件开关和文件名 |
| `gameRooms` | 棋牌室系统——牌桌的空间容器，创建限制、进出提醒、离开倒计时 |
| `ranking` | 雀魂风格段位系统开关和房间档位 |
| `integrations.craftengine` | CraftEngine bundle 导出、物品、家具和兼容性设置 |
| `debug` | 调试日志分类 |

配置会在插件加载时读取。使用 `/mahjong reload` 可以重载配置、重建 CraftEngine 桥接并刷新活动牌桌。

### 数据库与段位

默认配置使用 H2 本地数据库，适合小服或测试。需要跨服、长期统计或外部备份时，可以把 `database.connection.type` 调整为 MariaDB/MySQL，并填写连接信息。

`ranking.enabled: true` 时，插件会保存雀魂风格段位数据。玩家可用 `/mahjong rank` 查看自己的段位、点数和名次统计。

### CraftEngine 与资源

MahjongPaper 使用 CraftEngine 处理以下内容：

- 自定义麻将牌物品。
- 牌桌和座位家具 hitbox。
- 家具交互事件路由。
- tracked entity culling 兼容。
- 可选的 PacketEvents 兼容映射注入，用于部分反作弊环境。

常用配置：

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `integrations.craftengine.exportBundleOnEnable` | `true` | 启动时导出 MahjongPaper 的 CraftEngine bundle |
| `integrations.craftengine.bundle.folder` | `mahjongpaper` | 导出到 CraftEngine `resources` 下的文件夹名 |
| `integrations.craftengine.items.preferCustomItems` | `true` | 优先使用 CraftEngine 自定义物品 |
| `integrations.craftengine.items.riichiTileItemIdPrefix` | `mahjongpaper:` | 立直牌物品 ID 前缀 |
| `integrations.craftengine.items.gbTileItemIdPrefix` | `mahjongpaper:` | 国标和四川牌物品 ID 前缀 |
| `integrations.craftengine.furniture.preferHitboxInteraction` | `true` | 优先使用家具 hitbox 交互 |
| `integrations.craftengine.furniture.tableFurnitureId` | `mahjongpaper:table_visual` | 牌桌家具 ID |
| `integrations.craftengine.furniture.seatFurnitureId` | `mahjongpaper:seat_chair` | 座位家具 ID |

如果牌桌交互异常，优先确认 CraftEngine 已加载、bundle 已导出、资源包已正确生成并下发给玩家。

## 命令速查

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/mahjong help` | 玩家 | 显示帮助 |
| `/mahjong join <table_id>` | 玩家 | 加入牌桌 |
| `/mahjong leave` | 玩家 | 离开座位或退出观战 |
| `/mahjong spectate <table_id>` | 玩家 | 观战牌桌 |
| `/mahjong unspectate` | 玩家 | 退出观战 |
| `/mahjong table [table_id]` | 玩家 | 打开牌桌控制面板 |
| `/mahjong table owner <玩家名> [table_id]` | 桌主/管理员 | 转让桌主给已入座玩家 |
| `/mahjong mode <mode>` | 桌主/管理员 | 应用模式预设 |
| `/mahjong rule [key] [value]` | 玩家；修改需桌主/管理员 | 查看规则 GUI，或修改开局前规则 |
| `/mahjong start` | 玩家 | 切换准备状态 |
| `/mahjong state` | 玩家 | 查看当前状态 |
| `/mahjong riichi <hand_index>` | 玩家 | 立直专用 |
| `/mahjong kyuushu` | 玩家 | 立直专用九种九牌 |
| `/mahjong tsumo` | 玩家 | 自摸 |
| `/mahjong ron` | 玩家 | 荣和 |
| `/mahjong pon` | 玩家 | 碰 |
| `/mahjong minkan` | 玩家 | 明杠 |
| `/mahjong chii <tile_a> <tile_b>` | 玩家 | 吃，四川模式不可用 |
| `/mahjong kan <tile>` | 玩家 | 暗杠或加杠 |
| `/mahjong skip` | 玩家 | 放弃当前反应 |
| `/mahjong settlement` | 玩家 | 打开最近结算 |
| `/mahjong rank` | 玩家 | 查看段位 |
| `/mahjong leaderboard [mode]` | 玩家 | 查看分模式排行榜 |
| `/mahjong addbot` | 桌主/管理员 | 开局前添加 Bot |
| `/mahjong removebot` | 桌主/管理员 | 开局前移除 Bot |
| `/mahjong create` | 管理员 | 创建牌桌 |
| `/mahjong botmatch [MAJSOUL_HANCHAN|MAJSOUL_TONPUU|GB|SICHUAN]` | 管理员 | 创建 4 Bot 测试桌 |
| `/mahjong list` | 管理员 | 列出活动牌桌 |
| `/mahjong render` | 管理员 | 强制刷新牌桌展示 |
| `/mahjong inspect` | 管理员 | 渲染诊断 |
| `/mahjong clear` | 管理员 | 清除展示实体 |
| `/mahjong forceend [table_id]` | 管理员 | 强制结束牌桌 |
| `/mahjong deletetable [table_id]` | 管理员 | 删除牌桌 |
| `/mahjong reload` | 管理员 | 重载配置 |

## 棋牌室系统

棋牌室是牌桌的空间容器，用于限制牌桌的创建位置、提供进出提醒和对局中离开倒计时。

### 核心功能

| 功能 | 说明 |
| --- | --- |
| 牌桌创建限制 | `gameRooms.restrictNewTables: true` 时，新牌桌只能在棋牌室内创建；棋牌室外的已有牌桌仍可正常使用 |
| 进出提醒 | `gameRooms.enterExitMessages: true` 时，玩家进出棋牌室会收到提示消息 |
| 离开倒计时 | 对局中的玩家离开棋牌室后开始倒计时（默认 60 秒），超时则强制结束对局；玩家返回棋牌室则取消倒计时 |

### 倒计时警告节奏

- 前段：每 15 秒提醒一次（如 60s、45s、30s、15s）
- 最后 10 秒：10、8、6、5、4、3、2、1 逐秒倒计时

### 配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `gameRooms.enabled` | `true` | 启用棋牌室系统 |
| `gameRooms.restrictNewTables` | `true` | 限制新牌桌只能在棋牌室内创建 |
| `gameRooms.enterExitMessages` | `true` | 进出棋牌室时显示提示 |
| `gameRooms.leaveCountdownSeconds` | `60` | 离开倒计时秒数（最小 5） |
| `gameRooms.defaultRadius` | `10` | 中心点创建棋牌室的默认半径 |
| `gameRooms.defaultHeight` | `8` | 中心点创建棋牌室的默认高度 |
| `gameRooms.file` | `game-rooms.yml` | 棋牌室持久化文件 |

棋牌室数据保存在 `plugins/MahjongPaper/game-rooms.yml` 中。

### 创建棋牌室

目前棋牌室通过编辑 `plugins/MahjongPaper/game-rooms.yml` 手动创建。文件格式如下：

```yaml
rooms:
  my-room:                          # 棋牌室 ID（小写字母、数字、下划线、短横线）
    name: "我的棋牌室"               # 显示名称
    world: world                     # 世界名称
    minX: -50                        # 区域最小 X
    minY: -10                        # 区域最小 Y
    minZ: -50                        # 区域最小 Z
    maxX: 50                         # 区域最大 X
    maxY: 20                         # 区域最大 Y
    maxZ: 50                         # 区域最大 Z
    owner: "玩家UUID"                # 可选，所有者 UUID
```

**创建步骤**：

1. 停止服务器或确保没有活跃牌桌。
2. 打开 `plugins/MahjongPaper/game-rooms.yml`。
3. 在 `rooms:` 下添加一个新条目，按上面的格式填写 ID、名称、世界名和区域坐标。
4. 保存文件，重启服务器或使用 `/mahjong reload` 重载。

**确定区域坐标的方法**：

- 站在你想设为棋牌室的一个角落，记下 `/minecraft:tp ~ ~ ~` 显示的坐标作为 `minX/minY/minZ`。
- 走到对角线的另一个角落，记下坐标作为 `maxX/maxY/maxZ`。
- 两个角定义一个长方体（AABB），牌桌中心必须在这个长方体内才算"在棋牌室内"。

**示例**：创建一个以 (0, 64, 0) 为中心、半径 15、高度 10 的棋牌室：

```yaml
rooms:
  main-hall:
    name: "主厅"
    world: world
    minX: -15
    minY: 60
    minZ: -15
    maxX: 15
    maxY: 69
    maxZ: 15
```

> 后续版本将添加游戏内命令和选区工具来简化棋牌室的创建流程。

## 常见问题

### 为什么我不能创建牌桌？

`/mahjong create` 需要 `mahjongpaper.admin`。普通玩家只能加入、观战和进行牌局操作。此外，如果棋牌室系统已启用且 `gameRooms.restrictNewTables` 为 `true`，则牌桌只能在棋牌室内创建，在棋牌室外尝试创建会提示"必须在棋牌室内"。

### 为什么国标胡不了？

国标模式要求至少 8 番。番数不足时，即使牌型完成也不能和牌。

### 为什么四川不能吃？

当前四川模式按血战到底方向实现，不开放吃牌。能用的主要副露操作是碰和杠。

### 为什么 `/mahjong rank` 不可用？

段位系统需要数据库服务可用，并且 `ranking.enabled` 为 `true`。

### 为什么座位或牌桌点不动？

优先检查 CraftEngine 是否加载成功、MahjongPaper bundle 是否导出、资源包是否正确下发。然后使用 `/mahjong render` 重渲染牌桌；需要进一步定位时使用 `/mahjong inspect` 查看锚点和方向。

### 修改配置后要重启吗？

普通配置可以先尝试 `/mahjong reload`。如果更换了插件 jar、CraftEngine 本体、服务端版本或资源包生成方式，建议完整重启。
