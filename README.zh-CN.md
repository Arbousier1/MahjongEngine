# MahjongPaper

> 本项目完全由 AI 创作。

英文说明见 [README.md](./README.md)。

`MahjongPaper` 是 `MahjongCraft` 的 Paper 插件重写版本，当前主要基于：

- Paper 显示实体
- `ItemMeta#setItemModel(...)` 与配套资源包
- CraftEngine 自定义物品、家具交互和实体剔除

## 当前功能

当前分支已经具备可游玩的立直麻将基础流程：

- 可持久化的大堂式牌桌，重启后可恢复
- 空桌创建、东南西北固定座位、点击入座、点击准备
- 4 个座位坐满且全部准备后自动开局
- 可补 Bot，且 Bot 默认视为已准备
- 开局前可直接离桌；开局后会在当前一局结束后离桌
- 发牌、摸牌、打牌、立直、自摸、荣和、吃、碰、明杠、暗杠、加杠
- 通过 `mahjong-utils` 进行和牌与结算
- 观战、私有手牌显示、HUD 覆盖层和本地化提示
- 基于 CraftEngine 的座位 / 牌桌交互与 bundle 导出
- 默认使用 H2 持久化对局历史，可选 MariaDB

## 指令概览

- `/mahjong help`：显示游戏内帮助
- `/mahjong create`：在当前位置创建一个空牌桌
- `/mahjong botmatch [hanchan|tonpuu]`：创建一桌 4 Bot 测试对局并进入观战
- `/mahjong mode <tonpuu|hanchan>`：在下一局开始前应用预设规则
- `/mahjong join <tableId>`：加入牌桌
- `/mahjong leave`：开局前直接离开；开局后标记为本局结束后离开
- `/mahjong list`：查看活动牌桌及位置
- `/mahjong start`：切换当前座位的准备状态
- `/mahjong spectate <tableId>`：观战牌桌
- `/mahjong unspectate`：结束观战
- `/mahjong addbot`、`/mahjong removebot`：在开局前增减 Bot
- `/mahjong rule [key] [value]`：查看或修改下一局生效的规则
- `/mahjong state`：查看当前牌桌摘要
- `/mahjong riichi <index>`、`/mahjong tsumo`、`/mahjong ron`、`/mahjong pon`、`/mahjong minkan`、`/mahjong chii <tileA> <tileB>`、`/mahjong kan <tile>`、`/mahjong skip`、`/mahjong kyuushu`：对局中的动作指令
- `/mahjong settlement`：重新打开最近一次结算界面
- `/mahjong render`、`/mahjong clear`、`/mahjong inspect`：渲染维护与调试
- `/mahjong forceend [tableId]`：管理员强制结束当前对局
- `/mahjong deletetable [tableId]`：管理员删除牌桌

管理员目标解析规则：

- 优先使用你显式传入的 `tableId`
- 未传时，先尝试你当前所在或正在观战的牌桌
- 如果你不在任何牌桌中，则回退到你附近最近的牌桌

## 大厅与准备流程

- `/mahjong create` 只会创建空桌，不会自动把创建者加入牌局
- 玩家通过东南西北悬浮字附近的交互加入固定风位
- 同一套悬浮字交互也用于开局前切换准备状态
- 只有 4 个座位坐满且全部准备后，才会自动开始
- 一局结束或整场结束后，都需要再次准备，才会开始下一局

## 构建

```powershell
.\gradlew.bat build
```

当前 [plugin.yml](./src/main/resources/plugin.yml) 将 CraftEngine 声明为必需依赖，因此运行时需要安装 CraftEngine。

## 配置

默认配置文件位于 [src/main/resources/config.yml](./src/main/resources/config.yml)。

当前推荐的配置结构：

- `database.connection`：数据库类型与 MariaDB 连接目标
- `database.credentials`：MariaDB 用户名与密码
- `database.h2`：本地 H2 配置
- `database.pool`：连接池参数
- `tables.persistence`：持久牌桌文件配置
- `integrations.craftengine`：CraftEngine 导出与交互偏好
- `debug`：调试日志

说明：

- 插件会在启动时一次性读取配置
- 修改 `config.yml` 后需要重启服务器或插件进程
- 旧版扁平配置键目前仍然兼容，可平滑迁移

## CraftEngine

安装 CraftEngine 后，MahjongPaper 会把 bundle 导出到：

- `plugins/CraftEngine/resources/mahjongpaper`

导出内容包括：

- `pack.yml`
- `configuration/items/mahjong_tiles.yml`
- `resourcepack/assets/mahjongcraft/...`

当前 CraftEngine 集成主要覆盖：

- 自定义麻将牌物品
- 牌桌与座位 hitbox 家具
- tracked entity 剔除桥接
- 牌桌交互事件路由

## 资源包

配套资源包位于 [resourcepack](./resourcepack)。

## 上游参考

- `MahjongCraft`
- `MahjongPlay`: <https://github.com/7yunluo/MahjongPlay>
- `Paper`
- `CraftEngine`
