# Network 使用文档

## 修订记录

| 修订时间（CST） | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-08-26 | whisper | API 使用显式 Business Meta/数据类型 |
| 2026-08-25 | whisper | 增加单域名 app 组合根接入示例 |

## 1. 配置域名

根目录 `app-config.toml` 使用 Prism 生成唯一的 `BuildConfig.API_HOST`：

```toml
[default]
buildConfig.API_HOST = "https://api.example.com/"
```

模板没有 product flavor 时只需要这一份回退配置。实际项目启用环境变体后，可以让 Prism 为 app 的不同 variant 生成不同 `API_HOST`，下层模块仍无需感知变体。

示例回退地址是本地 HTTP，因此 app 的示例网络安全配置允许 cleartext。生产项目应改用 HTTPS，并在 `starter_network_security_config.xml` 中关闭 cleartext。

## 2. 安装组件管理器

Application 启动时完成唯一装配：

```kotlin
ApiFactory.install(
    StarterNetworkComponentManager(
        apiHost = BuildConfig.API_HOST,
        requestHeadersProvider = StarterRequestHeadersProvider(this),
    )
)
```

必须在首次调用 `ApiFactory.create()` 前安装。重复安装只用于错误恢复，不是切换环境的常规 API。

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

Foundation 只定义：

```kotlin
fun interface RequestHeadersProvider {
    fun currentHeaders(): Map<String, String>
}
```

Header 集合由 app 提供。模板默认发送平台、包名、语言、应用/API 版本和时间戳，不发送设备唯一标识。项目可以替换 Provider：

```kotlin
val provider = RequestHeadersProvider {
    buildMap {
        put("Platform", "android")
        sessionTokenProvider.currentToken()?.let { put("Authorization", "Bearer $it") }
    }
}
```

需要登录态时，Provider 应依赖稳定的 auth 契约，不应让 Foundation 或 Architecture 依赖具体登录实现。

## 6. API 级定制

优先使用 `@Interceptors` 声明必须执行的请求逻辑。只有观察真实网络交换时才使用 `@NetworkInterceptors`。

```kotlin
@Interceptors(AuthInterceptor::class)
@OkHttpCustomizer(UploadTimeoutCustomizer::class)
interface UploadApi
```

模板 Manager 默认只能反射创建无参组件。带依赖的组件应由 app 在 `resolveInterceptor()`、`resolveOkHttpCustomizer()` 或 `resolveRetrofitCustomizer()` 中显式返回。

## 7. 多域名扩展

不要为了预想中的多域名需求把 flavor 常量下沉到 Foundation。出现明确需求后，在 app 层增加路由拦截器或 Retrofit customizer，并让业务 API 只依赖稳定 marker。域名选择、租户状态和环境配置仍由 app 组合根持有。
