# Architecture 使用文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                              |
|-----------------|---------|---------------------------------------|
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

业务请求可以用 `ArchitectureBusiness<T, M>` 表达加载、成功和失败状态。架构层只承载状态, 不解释具体业务错误,
也不假设应用公共响应字段结构。

推荐用法:

```kotlin
val stateFlow: Flow<ArchitectureBusiness<UserInfo, BusinessMetadata>> = repository
    .loadUser()
    .withLoading()
```

应用公共层可以用 typealias 固定统一元信息类型, 例如
`typealias Business<T> = ArchitectureBusiness<T, BusinessMetadata>`。业务模块使用 `Business<T>` 即可,
不需要重复声明 `M`。

创建状态时优先使用 companion 工厂函数, 例如 `Business.success(data)`、`Business.error(exception)` 和
`Business.loading()`。

成功状态脱壳为业务数据时, 可以按需传入 `BusinessMetaProcessor` 显式消费成功元信息:

```kotlin
val successFlow: Flow<ArchitectureBusiness.Success<UserInfo, BusinessMetadata>> =
    outcomeFlow.consumeError(errorProcessor)
val dataFlow: Flow<UserInfo> = successFlow.consumeSuccessMeta(metaProcessor)
```

不需要处理成功元信息时, 可以直接调用 `successFlow.consumeSuccessMeta()`。

当服务端返回业务错误时, 使用 `BusinessException` 保存错误信息摘要。错误码、提示信息、追踪字段或恢复动作所需的其它元信息应放在业务公共层定义的
`M` 中。

## 3. Architecture UI

页面需要统一处理进度和消息时, 可以复用 Architecture UI 状态:

```text
ArchitectureActivity / ArchitectureFragment
    -> ArchitectureViewModel
    -> ArchitectureUiState
```

页面侧只读取 `ArchitectureUiState`, 需要更新时通过可变状态接口增加或减少 pending task, 或发送页面消息。

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
