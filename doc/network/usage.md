# Network 使用文档

## 修订记录

| 修订时间（CST） | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-16 | whisper | 增加 GitHub Endpoint TOML 配置并同步集中创建入口 |
| 2026-09-16 | whisper | 同步占位地址与统一组件解析接入方式 |
| 2026-09-16 | whisper | 说明网络组件通过统一实例池按需复用 |
| 2026-09-16 | whisper | 简化组件管理器安装为仅传入 Application |
| 2026-09-15 | whisper | 增加 GitHub Endpoint 与系统参数拦截器示例 |
| 2026-09-01 | whisper | 强调 ApiFactory 每进程严格单次安装 |
| 2026-08-28 | whisper | 默认禁用 cleartext 网络流量 |
| 2026-08-27 | whisper | 说明网络组件 keep rule 归属 |
| 2026-08-26 | whisper | 同步 API 注解约束与 Architecture 拦截器接入 |
| 2026-08-26 | whisper | 明确重复安装的最后快照恢复语义 |
| 2026-08-26 | whisper | API 使用显式 Business Meta/数据类型 |
| 2026-08-25 | whisper | 增加单域名 app 组合根接入示例 |

## 1. 占位地址与目标 Endpoint

Starter 默认 Retrofit 直接使用框架的安全占位地址:

```kotlin
retrofitBuilder.baseUrl(NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL)
```

该常量为 `https://placeholder.invalid/`, 不需要在 `app-config.toml` 中重复声明 `API_HOST`.
Project 通过 API 级 Endpoint 拦截器访问 GitHub, 实际地址由 app 注入, 不写入默认 base URL.
根目录 `app-config.toml` 使用 Prism 生成 app 的 `BuildConfig.GITHUB_API_ENDPOINT`:

```toml
[default]
buildConfig.GITHUB_API_ENDPOINT = "https://api.github.com/"
```

`StarterNetworkComponentManager` 将该值解析为 `HttpUrl` 并注入 GitHub 路由拦截器. 当前只使用 Endpoint 的
scheme、host 和 port, 原请求的 path、query 和 fragment 保持不变; 不通过此值添加 API 路径前缀.
这是构建配置, 修改后需要重新构建安装, 不提供运行时环境切换. 启用环境变体后, 可以由 Prism 为 app 生成不同的
Endpoint 值, 下层模块仍无需感知变体. 具体配置规则见 [Prism 使用文档](../prism/usage.md).

示例回退地址使用不可访问的 HTTPS 占位值, app 的示例网络安全配置默认禁止 cleartext. 实际项目确需访问本地 HTTP
服务时, 应在 `starter_network_security_config.xml` 中增加只覆盖必要域名的受限例外, 不将 base config 改为全局允许.

## 2. 安装组件管理器

Application 启动时完成唯一装配：

```kotlin
ApiFactory.install(StarterNetworkComponentManager(this))
```

管理器使用框架占位地址, 并创建、持有公共 Header 和 GitHub 专属拦截器, 无需在 Application 中逐项装配.
传入的 `Application` 用于公共 Header 的包名和当前资源 Locale; GitHub `User-Agent` 由应用 ID 与版本构建配置生成.
组件按声明类型保存在管理器的统一实例池中, 首次使用时创建, 后续不同 API 使用同一声明类型时复用同一实例.
新增带依赖组件时扩展管理器 `createComponentInstance()` 的分支即可, 具体维护约束见 [组件解析](development.md#4-组件解析).
默认配置中的公共 Header 与 API 注解中的拦截器使用同一解析入口, 缓存与构造逻辑由管理器集中处理.

必须在首次调用 `ApiFactory.create()` 前安装, 且每个进程生命周期内只能成功调用一次. 任何顺序或并发的重复调用都会立即抛出
`IllegalStateException`; 首次安装的组件管理器和 API 缓存保持不变. 不得使用重复安装恢复错误或切换运行期环境.
Android 多进程中每个进程都有独立的 `ApiFactory`, 需在各自的 Application 组合根中完成一次安装.

## 3. 声明 API

使用业务 Flow CallAdapter 时直接返回 `Flow<Business<BusinessMetadata, T>>`：

```kotlin
interface UserApi {

    @GET("user/profile")
    fun profile(): Flow<Business<BusinessMetadata, UserProfile?>>
}
```

接口不声明域名，也不引用 app BuildConfig。Retrofit 实际按 `ApiResponse<UserProfile>` 解析响应，再转成业务状态。

仍可使用普通 suspend API：

```kotlin
@GET("version")
suspend fun version(): ApiResponse<VersionResp>
```

此时 repository 可以通过 `callAsBusinessFlow` 手动转换，适合需要额外数据 transformer 的场景。

## 4. 创建与收集

```kotlin
fun profile(): Flow<Business<BusinessMetadata, UserProfile?>> =
    ApiFactory.create(UserApi::class).profile()
```

ViewModel 可以沿用 Architecture 数据管线：

```kotlin
repository.profile()
    .consumeLoading()
    .consumeError()
    .consumeSuccessMeta()
    .collect { profile ->
        // 更新页面业务状态
    }
```

`ApiFactory` 已缓存 API Proxy，业务侧不要再增加静态 API 单例。

## 5. 公共请求头

Architecture 只提供抽象的 Header 注入模板:

```kotlin
abstract class RequestHeadersInterceptor : Interceptor {
    final override fun intercept(chain: Interceptor.Chain): Response

    protected abstract fun resolveRequestHeaders(
        request: Request,
    ): Map<String, String>
}
```

`RequestHeadersInterceptor` 在每次请求时调用抽象方法，并覆盖同名 Header。Header 集合由 app 的具体子类提供。
模板 app 默认发送平台、包名、语言、应用/API 版本和时间戳，不发送设备唯一标识。实现层可以按需使用 Provider，但它不是 Architecture 契约:

```kotlin
class StarterRequestHeadersInterceptor(
    private val provider: StarterRequestHeadersProvider,
) : RequestHeadersInterceptor() {

    override fun resolveRequestHeaders(request: Request): Map<String, String> =
        provider.currentHeaders()
}
```

需要登录态时，app 的具体拦截器或其 Provider 应依赖稳定的 auth 契约，不应让 Foundation 或 Architecture 依赖具体登录实现。

## 6. API 级定制

默认网络配置只放所有 API 都必须具备的能力。可选的域名路由、鉴权、签名等能力由 API 通过注解按顺序增量声明:

```kotlin
@ApplicationInterceptors(
    ServiceBaseUrlInterceptor::class,
    AuthInterceptor::class,
)
@UseOkHttpCustomizer(UploadTimeoutCustomizer::class)
interface UploadApi
```

使用时遵守以下顺序:

1. 必须每次执行的请求逻辑使用 `@ApplicationInterceptors`。
2. 只有观察真实网络交换时才使用 `@NetworkInterceptors`; 它不在缓存直接命中时执行, 重试或重定向时可能执行多次。
3. 超时、缓存、Dispatcher 等无法由拦截器表达的特殊 OkHttp 配置才使用 `@UseOkHttpCustomizer`。
4. `@UseRetrofitCustomizer` 只用于少量 Retrofit 特殊配置, 不作为常规 API 声明入口。

少一项能力时直接省略对应声明。不使用 Customizer 从 Builder 中删除默认或已声明的普通拦截器,
也不增加 `ExcludeInterceptor` 之类的反向配置。如果某项默认能力需要被部分 API 移除, 应将它改为接口级声明。

注解中的类型应使用稳定契约或 marker。当 API 位于 `*-api` 模块时, 不得引用 `*-impl` 中的具体组件。
app 通过 `resolveInterceptor()`、`resolveOkHttpCustomizer()` 或 `resolveRetrofitCustomizer()` 决定生命周期;
当前统一从实例池获取, 池内缺项时由 `createComponentInstance()` 集中构造, 调用方无需传入构造 lambda.
三类组件均允许对无依赖且符合无参构造约定的实现使用反射回退; 实例来源与生命周期由管理器决定.
需要注入配置、Provider 或特殊生命周期时使用显式工厂或 DI, 不单独要求 `UseRetrofitCustomizer` 必须注册.

模板无参反射依赖 `app/proguard-rules.pro` 中的组件构造器规则。Architecture 只保证 API 声明注解可在运行时读取,
不会替实现层保留构造器。实际项目改用 DI 或全部显式映射后, 应删除或收窄 app 的通用反射规则。

显式注册不构成权限边界, 审查应关注实际行为. Customizer 不应重新管理普通拦截器。
`RetrofitCustomizer` 虽然技术上可以替换 client 或 callFactory, 但业务 API 不得因此绕过 app 提供的证书、
鉴权和公共拦截器。需要完全独立网络栈的链路应使用专用工厂, 不通过业务 `ApiFactory` 创建。

注解作用于整个 API 接口。同一接口中的方法需要不同网络组合时, 将它们拆分为不同 Retrofit API 接口。

## 7. 多域名扩展

不要为了预想中的多域名需求把 flavor 常量下沉到 Foundation。出现明确需求后，在 app 层增加路由拦截器或 Retrofit customizer，并让业务 API 只依赖稳定 marker。域名选择、租户状态和环境配置仍由 app 组合根持有。

Architecture 的 `EndpointRoutingInterceptor` 可以承担通用 URL 改写:

```kotlin
class ServiceEndpointRoutingInterceptor(
    private val endpointProvider: () -> HttpUrl?,
) : EndpointRoutingInterceptor() {

    override fun resolveTargetEndpoint(request: Request): HttpUrl? = endpointProvider()
}
```

默认实现只替换 scheme、host 和 port, 原请求的 path、query 和 fragment 保持不变。如果网关 Endpoint 含 path 前缀,
覆写 `buildTargetUrl()` 并明确测试路径拼接规则。应用组合根再将稳定的 API 声明类型映射到该实例。

Starter 的 Project 示例采用同一套机制:

```kotlin
@ApplicationInterceptors(
    GithubEndpointRoutingInterceptor::class,
    GithubSystemParametersInterceptor::class,
)
internal interface GithubProjectApi
```

两个声明类型自身实现拦截行为. `StarterNetworkComponentManager` 分别通过
`GithubEndpointRoutingInterceptor(endpoint)` 与 `GithubSystemParametersInterceptor(userAgent)` 注入配置,
无需再声明 app 子类. Retrofit 初始请求使用框架占位地址; Endpoint 拦截器改写为 TOML 配置的目标 origin,
当前为 `https://api.github.com/`, 并保留接口相对路径.
系统参数拦截器统一覆盖 `Accept`、`X-GitHub-Api-Version` 和 `User-Agent`.
