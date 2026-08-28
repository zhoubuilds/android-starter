# Network 设计文档

## 修订记录

| 修订时间（CST） | 修订人 | 修订说明 |
| --- | --- | --- |
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
    -> BuildConfig.API_HOST
    -> StarterNetworkComponentManager
    -> ApiFactory.install(...)
```

由此得到以下边界：

* `architecture` 固定 API 创建流程、注解语义、组件解析契约和通用 Header / Endpoint 改写机制。
* `foundation` 提供公共响应转换和业务 Flow CallAdapter。
* `app` 决定域名、Converter、超时、请求头内容、安全策略和组件实例来源。
* feature、repository 和 foundation 不读取 flavor 或 `BuildConfig.API_HOST`。

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

* `BuildConfig.API_HOST` 作为 base URL。
* `BusinessFlowCallAdapterFactory` 支持 `Flow<Business<BusinessMetadata, T>>`。
* Gson 负责 `ApiResponse<T>` 转换。
* 每个 API 使用从 baseline client 派生的 OkHttp Builder。

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
| `UseRetrofitCustomizer` | Retrofit 高级配置逃生口 | 只使用 app 显式映射并审查的受信任实现 |

注解只声明类型, 实例由 app 的 `NetworkComponentManager` 解析。声明类型应是稳定契约或 marker;
当 API 位于 `*-api` 模块时, 不得在注解中反向引用 `*-impl` 具体实现。这样可避免 Architecture 反射决定业务组件的依赖和生命周期。

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

两者都不读取 BuildConfig、flavor、登录态或真实域名。Header 和 Endpoint 值由 app 的具体子类提供;
目标 Endpoint 含特殊 path 前缀时, 项目必须覆写 `buildTargetUrl()` 明确路径组合契约。

网络地址命名统一遵守以下语义:

* `Endpoint` 表示 API 的目标服务抽象, 用于 Provider、路由契约和拦截器。
* `BaseUrl` 表示 Retrofit 使用的完整基础地址值。
* `Host` 只表示不含 scheme、port 和 path 的主机名。
* `Gateway` 只在服务端拓扑中确实存在 API Gateway 时使用, 不作为 Endpoint 的通用同义词。

## 5. 单域名与多域名

Starter 默认只有一个 `API_HOST`，不内置租户、Profile 或动态路由。对于多数应用，这是最小且可验证的默认值。

出现真实的多域名需求后，可在 app 层选择以下扩展方式：

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

模板的本地回退地址是 HTTP，因此 app 示例安全配置暂时允许 cleartext；该策略不在 Architecture 中。生产项目切换 HTTPS 后应在 app 中关闭 cleartext。

## 7. 并发与缓存

`ApiFactory` 通过 `Installation` 快照原子绑定 `NetworkComponentManager` 与该次安装专属的 API 缓存，并串行化 API
首次创建。正常运行只安装一次；发生连续重复安装时, 最后一次原子写入的快照生效。调用方已经持有或正在创建的旧 API 允许继续使用旧快照,
但不会写入最新安装的缓存。重复安装只提供尽力恢复, 不构成运行期环境切换能力。

构建期回调不得：

* 调用 `ApiFactory.create()` 或 `ApiFactory.install()`。
* 等待可能创建 API 的线程、Future、协程或回调。
* 与 API 创建路径形成反向锁顺序。

构建锁不保护 OkHttp 运行期。Provider、拦截器和其它请求组件仍需线程安全。
