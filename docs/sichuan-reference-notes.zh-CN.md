# 川麻参考项目笔记

## 参考对象

- `quiet98k/Blood-On-Mahjong`
- `Suiban-p/majiangCalculate`
- `zpjshiwo77/Mahjong`

## 适合借鉴的点

- `quiet98k/Blood-On-Mahjong` 的项目结构清晰，规则、状态、持久化、WebSocket、前端页面分层明确。
- `quiet98k/Blood-On-Mahjong` 的川麻规则代码集中在 `server/utils`，适合作为重构时的模块划分参考。
- `Suiban-p/majiangCalculate` 对血战到底、血流成河、带根、查大叫、一炮多响都有完整的产品化流程描述，适合拿来补测试用例和边界清单。
- `zpjshiwo77/Mahjong` 的算番逻辑很轻，适合快速理解基础番数叠加和带根处理。

## 不适合直接照搬的点

- `quiet98k/Blood-On-Mahjong` 是完整 Web 应用，包含 Nuxt、Socket.IO、MongoDB、Redis、Kubernetes，不适合直接嵌进当前 Java/Kotlin 插件。
- `zpjshiwo77/Mahjong` 只覆盖单局算番演示，规则范围太窄，不能替代完整川麻流程。
- `Suiban-p/majiangCalculate` 偏前端小程序计分器，更像规则说明和交互样板，不是可直接复用的服务端引擎。

## 对当前仓库的建议

- 保留现有 `SichuanHuEvaluator` 作为最小胡牌判断层。
- 逐步对照 `quiet98k/Blood-On-Mahjong` 补齐血战到底流程、杠分、查叫、多人胡、结束条件等测试。
- 如果后续要做规则引擎重构，先把川麻结算抽成独立接口，再决定是自研实现还是借鉴外部项目的算法拆分。
- 目前不建议直接引入外部 Node 服务作为运行时依赖。
