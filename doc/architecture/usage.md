# Architecture 使用文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                              |
|-----------------|---------|---------------------------------------|
| 2026-09-01      | whisper | 强调 ApiFactory 每进程单次安装        |
| 2026-08-27      | whisper | 区分单轮与多轮 Business 进度用法      |
| 2026-08-27      | whisper | 说明 BusinessException 类型语义       |
| 2026-08-26      | whisper | 说明 Architecture UI 组件渲染边界     |
| 2026-08-26      | whisper | 说明状态驱动的 Business 进度语义      |
| 2026-08-26      | whisper | 补充 Architecture 网络拦截器基础能力 |
| 2026-08-26      | whisper | 迁移到独立 UI 状态、Effect 与组合 Owner |
| 2026-08-26      | whisper | 迁移到显式 Business Meta/数据类型      |
| 2026-07-30      | whisper | 明确通用 UI 工具使用 Kit              |
| 2026-07-27      | whisper | 重构业务状态元信息模型                |
| 2026-07-25      | whisper | 新增 architecture 模块使用说明        |

本文面向业务开发者, 说明 `architecture` 模块中可直接使用的基础能力。网络 API
接入见 [Network 使用文档](../network/usage.md)。

## 1. 依赖

Android 业务模块按需依赖:

```kotlin
dependencies {
    implementation(project(":architecture"))
}
```

如果模块公开 API 中暴露了 `architecture` 类型, 应改用 `api(project(":architecture"))`, 否则调用方无法编译。

## 2. 业务状态

业务数据可以用 `Business<M, D>` 表达加载、成功和失败状态。`M` 是主要载荷之外的完整 Meta, `D` 是主要业务载荷。
架构层只承载并分流状态, 不解释两种数据的内容。

推荐用法:

```kotlin
val stateFlow: Flow<Business<BusinessMetadata, UserInfo>> = repository
    .loadUser()
    .withLoading()
```

`Business.Loading` 是单例。成功和失败状态显式携带 Meta 与数据:

```kotlin
Business.Success(meta = meta, data = user)
Business.Failure(exception = exception, meta = meta, data = partialUser)
Business.Loading
```

失败状态的 `data` 不是占位字段。服务端在失败响应中返回的主要载荷仍应完整保留, 是否使用由下游业务决定。

成功状态脱壳为业务数据时, 可以按需传入 `BusinessMetaProcessor` 显式消费成功元信息:

```kotlin
val successFlow: Flow<Business.Success<BusinessMetadata, UserInfo>> =
    outcomeFlow.consumeError(errorProcessor)
val dataFlow: Flow<UserInfo> = successFlow.consumeSuccessMeta(metaProcessor)
```

不需要处理成功元信息时, 可以直接调用 `successFlow.consumeSuccessMeta()`。

需要由 ViewModel 处理 Loading 时, 在错误和 Meta 处理之前调用 `consumeLoading()`:

```kotlin
businessFlow
    .consumeLoading(progressProcessor)
    .consumeError(errorProcessor)
    .consumeSuccessMeta(metaProcessor)
```

`consumeLoading()` 面向最常见的单轮请求: 一次 Flow 收集表示一轮业务操作, 开始收集时开始进度, 收集正常结束、异常或取消时完成进度;
同时过滤 `Business.Loading`. 如果外部仍需要观察 Loading, 使用保留全部状态的 `withBusinessProgress()`:

```kotlin
val observableFlow: Flow<Business<BusinessMetadata, UserInfo>> = businessFlow
    .withBusinessProgress(progressProcessor)
```

单轮 Flow 通常发送 Loading、一个 Outcome 后结束. Loading 仍是可观察的领域状态, 只是单轮进度不依赖状态识别.

同一次收集中确实包含多轮 `Loading -> Outcome` 时使用 `withBusinessProgressCycles()` 或 `consumeLoadingCycles()`. 多轮入口由状态驱动:
每轮首次 Loading 开始, Outcome 完成下游发送后结束; 没有 Loading 的 Outcome 不触发进度. 不要为了普通单次请求使用多轮入口.
多个并发请求仍应在各自 Flow 上分别处理进度, 不要先合并原始 Business Flow 再使用单个状态表达多个任务.

实现层根据应用协议主动判定业务失败时, 使用 `BusinessException` 保存错误信息摘要并作为类型标记。下游可以通过
`failure.exception is BusinessException` 将其与网络、HTTP、解析等异常区分。它是保留实例身份的普通异常类, 不是按消息比较的值对象;
不要依赖 `copy()`、解构或值相等语义。错误码、提示信息、追踪字段或恢复动作所需的其它元信息应放在业务公共层定义的 `M` 中,
失败响应中的主要载荷继续放在 `data` 中。公开 API 应直接写出 `Business<BusinessMetadata, UserInfo>` 等真实类型,
不使用 typealias 隐藏 Meta 绑定。

## 3. Architecture UI

页面需要统一处理进度和通知时, 可以复用两个 Architecture UI 窄契约:

```text
BusinessViewModel (foundation)
    -> ArchitectureViewModel
    -> ArchitectureUiOwner (可选组合)
       |- ActiveOperationCountUiState -> StateFlow<Int>
       `- NoticeUiEffect -> SharedFlow<NoticeUiModel>

ArchitectureActivity / ArchitectureFragment
    -> 分别绑定 ActiveOperationCountUiState 与 NoticeUiEffect
```

`ActiveOperationCountUiState` 提供可恢复的正在进行操作数量, `NoticeUiEffect` 通过不重放的共享流发送一次性通知。
`ArchitectureUiOwner` 只组合两种窄能力, 不把通知重新归入 UiState, 也不引入只做接口聚合的 Store。

`ArchitectureActivity` 和 `ArchitectureFragment` 默认把 ViewModel 分别作为两个窄契约绑定, 不要求来源名义上实现 `ArchitectureUiOwner`。
大部分页面不需要手动装配；只消费一种能力的组件也应继续依赖对应窄契约。

具体页面通过无参的 `ArchitectureUiComponent` 实现渲染回调。组件不提供 `Context`; 页面或独立渲染器自行持有所需依赖:

```kotlin
override val architectureUiComponent: ArchitectureUiComponent =
    object : ArchitectureUiComponent() {
        protected override fun onActiveOperationCountChanged(count: Int) {
            renderOperationCount(count)
        }

        protected override fun handleNotice(notice: NoticeUiModel) {
            noticeRenderer.show(notice)
        }
    }
```

`bind()` 由 Activity / Fragment 基类调用且不可覆写。需要调整来源时只覆盖下面两个窄入口, 不替换组件内部生命周期绑定。

页面需要聚合不同来源时, 分别覆盖对应入口:

```kotlin
override fun boundActiveOperationCountUiStates(): Iterable<ActiveOperationCountUiState> =
    listOf(viewModel, uploadViewModel, syncViewModel)

override fun boundNoticeUiEffects(): Iterable<NoticeUiEffect> =
    listOf(viewModel, paymentViewModel)
```

业务 ViewModel 通常通过 `foundation` 的 `BusinessViewModel` 使用 `DefaultArchitectureUiOwner`。业务开始和完成会更新
`activeOperationCountFlow`, 错误通知会发送到 `noticeUiEffectFlow`; 页面只读取这些流, 不直接获取可变 Owner。

## 4. Network

业务 API 不直接构造 Retrofit。应用启动后安装网络组件管理器:

```kotlin
ApiFactory.install(StarterNetworkComponentManager())
```

每个进程必须在首次创建 API 前完成且仅完成一次安装. 重复调用会立即抛出 `IllegalStateException`,
不会替换首次安装的组件管理器或 API 缓存.

业务侧通过:

```kotlin
val api: UserApi = ApiFactory.create(UserApi::class)
```

`ApiFactory` 内部已经缓存 API 实例, 业务侧不要再做一层静态缓存。

Architecture 同时提供 `RequestHeadersInterceptor` 和 `EndpointRoutingInterceptor` 两类抽象请求改写模板。
具体 Header、Endpoint 和运行期选择策略继续由 app 子类提供; Provider 如有需要也只属于实现层;
详细执行顺序和接入方式见 [Network 使用文档](../network/usage.md)。

## 5. 放置建议

* 登录态、Token、刷新和三方凭证放入 `feature:auth-api` / `feature:auth-impl`。
* 用户资料、登录注册页面等垂直业务放入 `feature:user-*`。
* 跨业务但仍带业务语义的工具放入 `foundation`。
* 业务和应用语义无关的 Android 通用工具放入 `kit`。
* 稳定、业务无关、可被多个模块复用的技术能力才放入 `architecture`。
