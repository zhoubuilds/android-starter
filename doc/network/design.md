# Network 设计文档

## 修订记录

| 修订时间（CST） | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-16 | whisper | 集中组件创建并使用 TOML 提供 GitHub Endpoint |
| 2026-09-16 | whisper | 统一组件解析契约并复用框架占位地址 |
| 2026-09-16 | whisper | 使用管理器级实例池统一复用网络组件 |
| 2026-09-16 | whisper | 收敛网络管理器构造入口并内聚应用组件装配 |
| 2026-09-15 | whisper | 使用 API 级拦截器演示 Endpoint 与系统参数装配 |
| 2026-09-01 | whisper | ApiFactory 改为严格单次安装 |
| 2026-08-28 | whisper | 默认禁用 cleartext 网络流量 |
| 2026-08-28 | whisper | 明确 Endpoint 改写后的 Host Header 契约 |
| 2026-08-27 | whisper | 明确网络组件 keep rule 归属 |
| 2026-08-26 | whisper | 明确 API 注解边界与通用拦截器基础实现 |
| 2026-08-26 | whisper | 简化 ApiFactory 安装快照与重复安装恢复 |
| 2026-08-26 | whisper | 网络响应迁移到显式 Business Meta/数据类型 |
| 2026-08-25 | whisper | 迁入通用网络骨架并明确 app 组合根边界 |

本文说明 Starter 网络层的职责划分、创建流程和主要取舍。

## 1. 设计结论

默认域名只由 `app` 知道是合理的。域名来自最终产品、部署环境和构建配置，不是业务模块的固有属性。模板采用以下依赖方向：

```text
feature / repository
    -> API interface
    -> ApiFactory
    -> NetworkComponentManager contract

app
    -> Endpoint 配置与组件依赖
    -> StarterNetworkComponentManager
    -> ApiFactory.install(...)
```

由此得到以下边界：

* `architecture` 固定 API 创建流程、注解语义、组件解析契约和通用 Header / Endpoint 改写机制。
* `foundation` 提供公共响应转换和业务 Flow CallAdapter。
* `app` 决定域名、Converter、超时、请求头内容、安全策略和组件实例来源。
* feature、repository 和 foundation 不读取 app flavor 或 BuildConfig。

`NetworkComponentManager` 的默认配置只包含所有 API 都必须具备的能力。API 注解声明的是相对默认值的有序增量:

* 需要某项能力时声明它, 不需要时直接省略。
* 不设计 `ExcludeInterceptor` 或从默认链中删除可选组件的语义。
* 如果某个 API 需要移除默认能力, 说明该能力不是全局必需, 应迁移为接口级声明。

## 2. 核心流程

```text
ApiFactory.create(UserApi::class)
  -> 读取 API 接口注解
  -> 校验重复声明
  -> configureDefaultOkHttp
  -> @ApplicationInterceptors
  -> @NetworkInterceptors
  -> @UseOkHttpCustomizer
  -> configureDefaultRetrofit
  -> @UseRetrofitCustomizer
  -> Retrofit.create
  -> 缓存 API Proxy
```

默认 Retrofit 配置由 `StarterNetworkComponentManager` 提供：

* 直接使用 `NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL` 作为默认 base URL, 不重复配置占位地址。
* `BusinessFlowCallAdapterFactory` 支持 `Flow<Business<BusinessMetadata, T>>`。
* Gson 负责 `ApiResponse<T>` 转换。
* 每个 API 使用从 baseline client 派生的 OkHttp Builder。

`StarterNetworkComponentManager` 只接收 `Application`, 在内部读取 app `BuildConfig` 并创建、持有公共 Header
和 GitHub 专属拦截器. `Application` 仅用于公共 Header 的包名与每次请求时读取的资源 Locale, 不作为全局 Holder 暴露.
公共 Header、GitHub 拦截器和 Customizer 共用管理器级实例池, 按声明类型延迟创建并跨 API 复用.
默认配置与 API 注解中的拦截器均经过同一解析入口. 解析方法决定生命周期, 当前统一通过实例池获取;
实例池缺项时调用管理器的集中创建入口, 在其中选择依赖注入或无参反射兜底, 调用方不传入构造函数.
不再为每个单例添加成员字段; 不同管理器之间不共享实例, 创建失败也不写入缓存.

## 3. API 声明

普通接口不需要域名注解：

```kotlin
interface UserApi {
    @GET("user/profile")
    fun profile(): Flow<Business<BusinessMetadata, UserProfile?>>
}
```

需要差异化组件时可以声明：

```kotlin
@ApplicationInterceptors(AuthInterceptor::class)
@NetworkInterceptors(NetworkTraceInterceptor::class)
@UseOkHttpCustomizer(UserOkHttpCustomizer::class)
@UseRetrofitCustomizer(UserRetrofitCustomizer::class)
interface UserApi
```

四个注解分为两类入口:

| 注解 | 定位 | 约束 |
| --- | --- | --- |
| `ApplicationInterceptors` | 常规的 API 级请求能力 | 按声明顺序增量添加 |
| `NetworkInterceptors` | 低频的真实网络交换观察能力 | 不承担必须每次执行的业务逻辑 |
| `UseOkHttpCustomizer` | 拦截器无法表达的 OkHttp 高级配置 | 每个 API 最多一个, 不用于增删普通拦截器 |
| `UseRetrofitCustomizer` | Retrofit 高级配置逃生口 | 每个 API 最多一个, 必须保持公共网络策略生效 |

注解只声明类型, 实例由 app 的 `NetworkComponentManager` 解析。声明类型应是稳定契约或 marker;
当 API 位于 `*-api` 模块时, 不得在注解中反向引用 `*-impl` 具体实现。这样可避免 Architecture 反射决定业务组件的依赖和生命周期。

Interceptor、OkHttpCustomizer 和 RetrofitCustomizer 使用相同的实例解析契约: 管理器决定实例来源与生命周期.
无依赖且满足无参构造约定的组件可以使用反射, 需要注入配置、Provider 或特殊生命周期的组件使用显式工厂或 DI.
显式注册是应用可选的装配策略, 不是权限边界; 无论如何创建, Customizer 都必须遵守固定执行顺序和公共网络策略.

混淆规则遵循实例化职责归属。Architecture consumer rules 只保留 `ApiFactory` 读取声明所需的运行时注解信息;
无参反射、DI 容器或其它实例化策略所需的 keep rules 由选择该策略的 app 或实现模块提供。Starter app 保留无参反射兜底,
因此由自身 `proguard-rules.pro` 保活符合该兜底契约的实现类和构造器。

注解作用于整个 Retrofit API 接口。同一接口中的方法需要不同网络组合时, 应拆分为不同 API 接口,
不引入方法级排除或覆盖规则。

## 4. 通用拦截器基础实现

Architecture 提供两类不包含应用值的请求改写机制:

* `RequestHeadersInterceptor` 在每次请求执行时调用子类的 `resolveRequestHeaders(request)`, 并使用覆盖语义写入同名 Header。
* `EndpointRoutingInterceptor` 由子类为当前请求解析目标 Endpoint。默认只替换 scheme、host 和 port,
  保留原 path、query 和 fragment。

Endpoint 改写导致 origin 的 scheme、host 或 port 变化时, 拦截器移除请求中显式设置的 `Host` Header, 由 OkHttp
根据最终 URL 重新生成. 如果只调整 path、query 或 fragment 而 origin 不变, 则保留已有的显式 `Host` Header.

两者都不读取 BuildConfig、flavor、登录态或真实域名。Header 和 Endpoint 值由应用实现或注入的配置提供;
目标 Endpoint 含特殊 path 前缀时, 项目必须覆写 `buildTargetUrl()` 明确路径组合契约。

Starter 的 Project 示例在 `GithubProjectApi` 上声明 GitHub Endpoint 与系统参数两个 application interceptor 类型.
这两个类型自身提供具体拦截行为, app 分别注入 `BuildConfig.GITHUB_API_ENDPOINT` 与应用 `User-Agent`, 不再增加只承担
配置转发的 app 子类. 系统参数拦截器统一提供 GitHub 推荐的 media type、固定 REST API 版本和必需的 `User-Agent`.
这些参数适用于接口内所有 GitHub 请求, 因此不使用方法级 `@Headers` 重复维护.
[GitHub REST 请求头文档](https://docs.github.com/en/rest/using-the-rest-api/getting-started-with-the-rest-api#headers)
要求所有请求包含有效的 `User-Agent`, 并建议使用 `application/vnd.github+json` 与明确的 API 版本.

网络地址命名统一遵守以下语义:

* `Endpoint` 表示 API 的目标服务抽象, 用于 Provider、路由契约和拦截器。
* `BaseUrl` 表示 Retrofit 使用的完整基础地址值。
* `Host` 只表示不含 scheme、port 和 path 的主机名。
* `Gateway` 只在服务端拓扑中确实存在 API Gateway 时使用, 不作为 Endpoint 的通用同义词。

## 5. 默认域名与 API 级路由

Starter 直接使用框架提供的 `NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL`, 不再通过 app BuildConfig
重复声明安全占位地址, 也不内置租户或运行期 Profile. 该值只用于 Retrofit 初始化, 不是 Project 的 GitHub Endpoint.
真实 GitHub Endpoint 在根目录 `app-config.toml` 的 `[default]` 中通过 `buildConfig.GITHUB_API_ENDPOINT` 配置,
当前值为 `https://api.github.com/`. Prism 在构建时生成 app 的 `BuildConfig`; 只有 app 读取并注入路由拦截器,
下层模块不感知 TOML 或构建变体. 修改配置需要重新构建, 不构成运行时设置. 当前路由只使用 Endpoint 的 origin.
虽然示例当前只有 GitHub 一个真实服务, Project 仍有意通过 API 注解和
`EndpointRoutingInterceptor` 展示多 Endpoint 装配链路; 没有声明路由能力的 API 会继续停留在占位 origin 并快速失败.

后续出现更多真实 Endpoint 或动态路由需求时，可在 app 层选择以下扩展方式：

* 为少量特殊 API 使用 `@UseRetrofitCustomizer` 设置另一个 base URL。
* 通过 application interceptor 在请求运行期路由域名。
* 安装能够按 API 类型或运行时 Profile 分发 client 的 `NetworkComponentManager`。

这些扩展仍不要求业务底层感知 flavor。业务只声明稳定的 API 或路由 marker，具体地址继续由 app 解析。

## 6. 安全边界

模板不包含以下参考项目的临时或业务实现：

* 信任全部证书或主机名。
* 租户域名路由。
* Token、401 刷新和业务鉴权。
* 固定签名算法或占位签名值。

生产项目应在 app 组合根接入正式证书、鉴权和日志策略。`RetrofitCustomizer` 在技术上可以替换 client 或 callFactory,
但业务 API 不得借此绕过公共安全配置。确实需要完全独立网络栈的 SDK、埋点或鉴权刷新链路, 应使用专用工厂或 client,
不经过业务 `ApiFactory`。

模板回退地址使用 HTTPS 占位值, app 示例安全配置默认禁止 cleartext; 该策略不在 Architecture 中. 实际项目确需访问
本地 HTTP 服务时, 应由 app 增加只覆盖必要域名和构建环境的受限例外, 不全局放开明文流量.

## 7. 并发与缓存

`ApiFactory` 通过 `Installation` 快照原子绑定 `NetworkComponentManager` 与唯一的 API 缓存, 并串行化 API
首次创建. 首次 `install()` 通过 CAS 原子发布安装快照; 每个进程生命周期内只允许成功安装一次.
任何顺序或并发的重复调用都会立即抛出 `IllegalStateException`, 已安装的组件管理器和缓存保持不变.
重复安装不是错误恢复或运行期环境切换入口. Android 多进程的每个进程持有独立单例, 需在各自的组合根中完成一次安装.

构建期回调不得：

* 调用 `ApiFactory.create()` 或 `ApiFactory.install()`。
* 等待可能创建 API 的线程、Future、协程或回调。
* 与 API 创建路径形成反向锁顺序。

构建锁不保护 OkHttp 运行期。Provider、拦截器和其它请求组件仍需线程安全。
