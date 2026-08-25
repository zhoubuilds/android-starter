# Architecture 设计文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                          |
|-----------------|---------|-----------------------------------|
| 2026-08-20      | whisper | 迁移 Aegis 修改保护标记            |
| 2026-07-27      | whisper | 重构业务状态元信息模型            |
| 2026-07-25      | whisper | 新增 architecture 模块设计说明    |

本文记录 `architecture`
模块的职责边界、核心模型和主要设计取舍。网络子系统的详细设计见 [Network 设计文档](../network/design.md)。

## 1. 定位

`architecture` 是项目的技术架构基础模块, 负责提供跨业务复用的底层能力:

* 业务状态模型和 Flow 辅助处理。
* Architecture UI 状态容器和 Activity / Fragment 基类。
* Retrofit / OkHttp API 创建骨架。

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

业务请求状态用 `ArchitectureBusiness<T, M>` 表达:

```text
Loading / Success / Error
```

架构层只提供状态载体、异常包装和 Flow 扩展。`T` 表示随业务变化的数据类型, `M` 表示业务统一的响应元信息类型。架构层只透传
`M`, 不假设元信息中是否存在 `code`、`message` 或其它字段。

服务端错误通过 `BusinessException` 保存错误信息摘要, 具体错误语义和完整响应元信息交给业务层解释。应用公共层可以通过
typealias 固定 `M`, 对业务模块继续暴露只随接口变化的 `Business<T>`。

### 3.2 Architecture UI

Architecture UI 提供页面级通用状态:

* 待完成任务数量。
* 页面消息。
* Architecture UI 状态的只读暴露和可变更新。

`ArchitectureActivity` 与 `ArchitectureFragment` 负责把页面生命周期与状态绑定起来,
`ArchitectureViewModel` 负责承接常规业务进度和业务错误处理协议, `ArchitectureUiStateOwner`
负责暴露页面级通用状态。成功元信息处理属于按需能力, 不作为 `ArchitectureViewModel` 默认职责。

### 3.3 Network foundation

网络层只负责 API 创建骨架:

```text
API 接口注解
    -> ApiFactory
    -> NetworkComponentManager
    -> OkHttpClientFactory
    -> Retrofit
```

架构层读取 API 接口上的声明并保持执行顺序, 但不创建业务拦截器、域名策略或序列化实例。应用层通过
`NetworkComponentManager` 提供这些对象。

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
business.exception
business.function
business.model
business.processor
network
ui
viewmodel
```

`business` 相关类按业务状态链路聚合, 不再按根级 `model`、`function`、`processor` 分散。

关键命名约定:

* `BusinessErrorProcessor`、`BusinessMetaProcessor` 和 `BusinessProgressProcessor` 保留 `Processor`,
  表示业务状态处理协议, 避免和 Android `Handler` 混淆。
* `ArchitectureBusiness` 表示架构层业务状态载体, 不绑定应用级公共响应字段。
* `BusinessException` 表示服务端业务错误包装, 架构层只承载错误信息摘要。
* Architecture UI 统一使用 `ArchitectureUi` 和 `pendingTask` 术语, 不混用 `workingCount`、
  `workCount` 或 `loadingCount`。
* `ArchitectureViewModel` 和 `ArchitectureUiStateOwner` 保持在 `architecture.viewmodel`, 表达它们是架构组件,
  不归入具体 Activity 或 Fragment 包。

## 6. 演进原则

新增能力前先判断它是否属于稳定架构抽象:

* 如果能力依赖具体业务数据, 优先放入业务模块或 `foundation`。
* 如果能力需要对象图、作用域或构造注入, 优先接入 DI, 不扩展架构基类。
* 如果能力只服务单个页面或 feature, 不上升到 `architecture`。
* 如果能力会影响既有 API 契约、可观察行为或明确实现约束, 需要先检查源码中是否存在 `@aegis`。
