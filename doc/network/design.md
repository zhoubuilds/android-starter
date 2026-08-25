# Network 设计文档

## 修订记录

| 修订时间（CST） | 修订人 | 修订说明 |
| --- | --- | --- |
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

* `architecture` 固定 API 创建流程、注解语义和组件解析契约。
* `foundation` 提供公共响应转换、业务 Flow CallAdapter 和无环境依赖的请求头拦截器。
* `app` 决定域名、Converter、超时、请求头内容、安全策略和组件实例来源。
* feature、repository 和 foundation 不读取 flavor 或 `BuildConfig.API_HOST`。

## 2. 核心流程

```text
ApiFactory.create(UserApi::class)
  -> 读取 API 接口注解
  -> 校验重复声明
  -> configureDefaultOkHttp
  -> @Interceptors
  -> @NetworkInterceptors
  -> @OkHttpCustomizer
  -> configureDefaultRetrofit
  -> @RetrofitCustomizer
  -> Retrofit.create
  -> 缓存 API Proxy
```

默认 Retrofit 配置由 `StarterNetworkComponentManager` 提供：

* `BuildConfig.API_HOST` 作为 base URL。
* `BusinessFlowCallAdapterFactory` 支持 `Flow<Business<T>>`。
* Gson 负责 `ApiResponse<T>` 转换。
* 每个 API 使用从 baseline client 派生的 OkHttp Builder。

## 3. API 声明

普通接口不需要域名注解：

```kotlin
interface UserApi {
    @GET("user/profile")
    fun profile(): Flow<Business<UserProfile?>>
}
```

需要差异化组件时可以声明：

```kotlin
@Interceptors(AuthInterceptor::class)
@NetworkInterceptors(NetworkTraceInterceptor::class)
@OkHttpCustomizer(UserOkHttpCustomizer::class)
@RetrofitCustomizer(UserRetrofitCustomizer::class)
interface UserApi
```

注解只声明类型，实例由 app 的 `NetworkComponentManager` 解析。这样可避免 Architecture 反射决定业务组件的依赖和生命周期。

## 4. 单域名与多域名

Starter 默认只有一个 `API_HOST`，不内置租户、Profile 或动态路由。对于多数应用，这是最小且可验证的默认值。

出现真实的多域名需求后，可在 app 层选择以下扩展方式：

* 为少量特殊 API 使用 `RetrofitCustomizer` 设置另一个 base URL。
* 通过 application interceptor 在请求运行期路由域名。
* 安装能够按 API 类型或运行时 Profile 分发 client 的 `NetworkComponentManager`。

这些扩展仍不要求业务底层感知 flavor。业务只声明稳定的 API 或路由 marker，具体地址继续由 app 解析。

## 5. 安全边界

模板不包含以下参考项目的临时或业务实现：

* 信任全部证书或主机名。
* 租户域名路由。
* Token、401 刷新和业务鉴权。
* 固定签名算法或占位签名值。

生产项目应在 app 组合根接入正式证书、鉴权和日志策略。`RetrofitCustomizer` 可以替换 client 或 callFactory，使用时需要确认不会绕过公共安全配置。

模板的本地回退地址是 HTTP，因此 app 示例安全配置暂时允许 cleartext；该策略不在 Architecture 中。生产项目切换 HTTPS 后应在 app 中关闭 cleartext。

## 6. 并发与缓存

`ApiFactory` 原子发布 `NetworkComponentManager` 与 API 缓存，并串行化 API 首次创建。构建期回调不得：

* 调用 `ApiFactory.create()` 或 `ApiFactory.install()`。
* 等待可能创建 API 的线程、Future、协程或回调。
* 与 API 创建路径形成反向锁顺序。

构建锁不保护 OkHttp 运行期。Provider、拦截器和其它请求组件仍需线程安全。
