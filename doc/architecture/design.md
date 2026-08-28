# Architecture 设计文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                          |
|-----------------|---------|-----------------------------------|
| 2026-08-27      | whisper | 拆分单轮与多轮 Business 进度语义  |
| 2026-08-27      | whisper | 明确 BusinessException 身份语义   |
| 2026-08-27      | whisper | 收窄网络 consumer rules 职责      |
| 2026-08-26      | whisper | 收窄 Architecture UI 组件扩展边界 |
| 2026-08-26      | whisper | 调整 Business 进度为状态驱动       |
| 2026-08-26      | whisper | 提供 Header 与 Endpoint 拦截器基础实现 |
| 2026-08-26      | whisper | 整理 Architecture UI Owner 包边界 |
| 2026-08-26      | whisper | 拆分 Architecture UI 状态与 Effect |
| 2026-08-26      | whisper | 使用 Business 领域状态重构数据管线 |
| 2026-08-20      | whisper | 迁移 Aegis 修改保护标记            |
| 2026-07-27      | whisper | 重构业务状态元信息模型            |
| 2026-07-25      | whisper | 新增 architecture 模块设计说明    |

本文记录 `architecture`
模块的职责边界、核心模型和主要设计取舍。网络子系统的详细设计见 [Network 设计文档](../network/design.md)。

## 1. 定位

`architecture` 是项目的技术架构基础模块, 负责提供跨业务复用的底层能力:

* 业务状态模型和 Flow 辅助处理。
* Architecture UI 状态、Effect、组合 Owner 和 Activity / Fragment 基类。
* Retrofit / OkHttp API 创建骨架与业务无关的拦截器基础实现。

它不承载具体业务含义, 也不依赖 `app`、`foundation` 或 `feature:*` 模块。

推荐依赖方向:

```text
app
|- foundation
|- feature:auth-impl -> feature:auth-api
|- feature:user-impl -> feature:user-api
`- architecture

foundation / feature:* -> architecture
architecture -> 三方基础库
```

## 2. 非目标

`architecture` 不计划承担以下职责:

* 具体登录态、Token、三方凭证和用户业务。
* 业务模块之间的能力注册和发现, 该职责由 Aster 承担。
* 完整依赖注入容器。
* 统一页面导航栈或 Fragment 路由。
* 业务错误码的语义解释。
* 应用级网络域名、证书、鉴权 Header 和序列化策略的最终决策。

## 3. 分层模型

### 3.1 业务状态链路

业务数据状态用 `Business<M, D>` 表达:

```text
Business
|- Loading
`- Outcome
   |- Success(meta, data)
   `- Failure(exception, meta, data)
```

`M` 表示主要载荷之外的完整元信息类型, `D` 表示主要业务载荷类型。Architecture 只根据状态分流并原样透传 `M` 和 `D`,
不假设其中是否存在 `code`、`message` 或其它字段。成功和失败状态都保留 Meta 与主要载荷, 避免响应数据在进入 ViewModel 前丢失。

`Loading` 是无载荷单例, 可直接交给 UI 观察, 也可以由 `consumeLoading` 消费并交给抽象进度处理器. 单轮与多轮进度使用不同契约:

* 常用的 `withBusinessProgress` / `consumeLoading` 将一次 Flow 收集视为一轮业务操作. 开始收集时开始进度, 收集正常结束、异常或取消时
  完成进度. `withBusinessProgress` 不消费或解释 Loading / Outcome, 外部仍可观察完整状态; `consumeLoading` 只额外过滤 Loading.
* `withBusinessProgressCycles` / `consumeLoadingCycles` 用于同一次收集中包含多轮状态的顺序流. 每轮首次 Loading 开始进度, 后续 Outcome
  完成下游发送后结束; 同轮重复 Loading 不重复开始, Loading 后异常、取消或 Flow 结束时也会恢复当前进度. 没有 Loading 的 Outcome
  不触发多轮进度.

合法的单轮 Flow 通常依次发送 Loading、一个 Outcome 后结束, 因此两种契约在该场景下具有相同的回调顺序. 单轮入口不承担多轮状态机,
多轮入口也不作为普通请求的默认写法. 并发操作应在各自 Flow 上分别处理进度后再聚合计数.
进度回调应保持同步且不抛异常; 防御性处理仍会在开始回调失败后尝试配对完成, 并在管线已有异常或取消原因时将完成回调异常
保留为 suppressed exception, 不覆盖原始失败.

`Outcome` 只表示已经完成的 `Success` 或 `Failure`, 便于 Flow 操作符逐步收窄类型。`BusinessException` 是普通异常类,
用于标记 `foundation` 根据应用协议主动判定的业务失败; 网络、HTTP 和解析异常继续保留自身类型。Architecture 只提供这个类型边界,
不解释具体错误码或 Meta。同文案的不同异常实例保持独立身份, 不使用值相等语义合并两次失败。具体协议解释和
`Business<M, D>` 的构造由 `foundation` 负责；不使用 typealias 隐藏 Meta 类型。

### 3.2 Architecture UI

Architecture UI 将页面级持续状态和一次性行为拆分建模:

* `ActiveOperationCountUiState` 通过 `StateFlow<Int>` 提供正在进行的操作数量及后续更新。
* `NoticeUiEffect` 通过不重放的 `SharedFlow<NoticeUiModel>` 提供一次性页面通知。
* 两个接口可以作为窄契约独立使用, 不需要彼此感知。
* `ArchitectureUiOwner` 组合两种能力, 为大部分同时需要进度和通知的页面提供便捷入口。
* `MutableArchitectureUiOwner` 和 `DefaultArchitectureUiOwner` 提供更新协议与默认实现。

`ArchitectureUiState` 不再作为同时持有持续状态和一次性通知的容器。UiState 只表达可恢复的当前状态, UiEffect 负责不可恢复的一次性行为。
Owner 可以组合两种能力, UI 绑定位置则继续依赖两个窄契约。

`ArchitectureActivity` 与 `ArchitectureFragment` 分别聚合并绑定操作数量状态与通知 Effect, 不要求来源名义上实现 Owner。
`ArchitectureUiComponent` 只负责生命周期收集、来源聚合和分发, 不持有 `Context` 或其它具体渲染依赖。实现层通过两个
`protected abstract` 回调完成渲染并自行持有所需依赖; 公开 `bind()` 不允许覆写, 以保护重复绑定和生命周期不变量。
`ArchitectureViewModel` 实现只读 `ArchitectureUiOwner` 契约, 为常规 ViewModel 提供便捷组合。业务进度和错误处理协议由 Architecture 定义,
具体实现由 `foundation` 的 `BusinessViewModel` 提供。成功 Meta 处理属于按需能力。

### 3.3 Network foundation

网络层负责 API 创建骨架和业务无关的请求改写机制:

```text
API 接口注解
    -> ApiFactory
    -> NetworkComponentManager
    -> OkHttpClientFactory
    -> Retrofit
```

架构层读取 API 接口上的声明并保持执行顺序。`RequestHeadersInterceptor` 和
`EndpointRoutingInterceptor` 只提供公共 Header 注入与 Endpoint 改写算法, 不持有真实 Header、域名、网关或环境值。
应用层通过具体拦截器子类和 `NetworkComponentManager` 提供真实值与取值策略。Provider 如有需要，也只属于实现层。
Architecture consumer rules 只保护运行时网络声明的读取, 不替实现层规定反射构造、DI 或组件生命周期策略。

## 4. 模块边界

`architecture` 允许依赖 AndroidX、Material、OkHttp 和 Retrofit 等技术库。业务模块可以依赖
`architecture`, 但 `architecture` 不能反向依赖业务模块。

横向业务能力应拆到 `feature` 下的独立业务模块。例如登录鉴权使用:

```text
:feature:auth-api
:feature:auth-impl
```

不要把登录态、Token 刷新或三方凭证放入 `architecture`; 这些能力需要业务契约和应用层生命周期管理。

## 5. 命名原则

现阶段采用按领域聚合的包结构:

```text
model.domain
model.ui.notice
exception
extension
processor
network
ui.effect
ui.owner
ui.state
viewmodel
```

业务状态模型、UI 通知模型、UI 状态 / Effect 契约、管线操作与处理协议分别归入 `model.domain`、`model.ui.notice`、
`ui.state` / `ui.effect`、`extension` 和 `processor`, 异常类型归入 `exception`。

关键命名约定:

* `BusinessErrorProcessor<M>`、`BusinessMetaProcessor<M>` 和 `BusinessProgressProcessor` 保留 `Processor`,
  表示业务状态处理协议；前两者保持 Meta 类型, Architecture 不将其擦除为 `Any?`。
* 单轮业务进度使用简洁的 `withBusinessProgress` / `consumeLoading`; 多轮状态进度使用带 `Cycles` 后缀的对应入口.
* `Business<M, D>` 表示领域业务数据状态, 不绑定网络或应用级公共响应字段。
* `BusinessException` 表示服务端业务错误包装, 架构层只承载错误信息摘要。
* `ActiveOperationCountUiState` 只表示正在进行的操作数量状态, `NoticeUiEffect` 只表示一次性通知,
  `ArchitectureUiOwner` 负责常用组合。
* Architecture UI 统一使用 `ArchitectureUi` 和 `activeOperation` 术语, 不混用 `backendTask`、`pendingTask`、
  `workingCount`、`workCount` 或 `loadingCount`; Flow 属性保留 `Flow` 后缀。
* 三个 `ArchitectureUiOwner` 类型保持在 `architecture.ui.owner`, 表达状态与 Effect 的组合、更新和默认实现；
  `architecture.viewmodel` 只保留真正继承 AndroidX `ViewModel` 的 `ArchitectureViewModel`。

## 6. 演进原则

新增能力前先判断它是否属于稳定架构抽象:

* 如果能力依赖具体业务数据, 优先放入业务模块或 `foundation`。
* 如果能力需要对象图、作用域或构造注入, 优先接入 DI, 不扩展架构基类。
* 如果能力只服务单个页面或 feature, 不上升到 `architecture`。
* 如果能力会影响既有 API 契约、可观察行为或明确实现约束, 需要先检查源码中是否存在 `@aegis`。
