# Architecture 使用文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                              |
|-----------------|---------|---------------------------------------|
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

当服务端返回业务错误时, 使用 `BusinessException` 保存错误信息摘要。错误码、提示信息、追踪字段或恢复动作所需的其它元信息应放在业务公共层定义的
`M` 中。公开 API 应直接写出 `Business<BusinessMetadata, UserInfo>` 等真实类型, 不使用 typealias 隐藏 Meta 绑定。

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

业务侧通过:

```kotlin
val api: UserApi = ApiFactory.create(UserApi::class)
```

`ApiFactory` 内部已经缓存 API 实例, 业务侧不要再做一层静态缓存。

## 5. 放置建议

* 登录态、Token、刷新和三方凭证放入 `feature:auth-api` / `feature:auth-impl`。
* 用户资料、登录注册页面等垂直业务放入 `feature:user-*`。
* 跨业务但仍带业务语义的工具放入 `foundation`。
* 业务和应用语义无关的 Android 通用工具放入 `kit`。
* 稳定、业务无关、可被多个模块复用的技术能力才放入 `architecture`。
