# Network 开发文档

## 修订记录

| 修订时间（CST） | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-08-25 | whisper | 记录 Starter 网络实现、测试和维护边界 |

本文面向 Network 维护者。设计取舍见 [设计文档](design.md)，业务接入见 [使用文档](usage.md)。

## 1. 源码结构

```text
architecture/.../architecture/network/
|- ApiFactory.kt
|- OkHttpClientFactory.kt
|- annotation/
|  |- Interceptors.kt
|  |- NetworkInterceptors.kt
|  |- OkHttpCustomizer.kt
|  `- RetrofitCustomizer.kt
`- component/
   |- NetworkComponentManager.kt
   |- OkHttpCustomizer.kt
   `- RetrofitCustomizer.kt

common/.../common/network/
|- BusinessFlowCallAdapterFactory.kt
`- interceptor/
   |- RequestHeadersInterceptor.kt
   `- RequestHeadersProvider.kt

app/.../starter/network/
|- StarterNetworkComponentManager.kt
`- StarterRequestHeadersProvider.kt
```

旧的 `architecture.net`、`@BaseUrl`、`AppGlobal` 和信任所有主机名实现已经删除。

## 2. 固定执行顺序

```text
configureDefaultOkHttp
  -> application interceptors
  -> network interceptors
  -> OkHttp customizer
  -> configureDefaultRetrofit
  -> Retrofit customizer
  -> Retrofit.build
```

`configureDefaultRetrofit()` 接收已经完成 API 级 OkHttp 配置的 Builder。Retrofit customizer 最后执行，因此可以覆盖前面的 client 绑定。

## 3. Business Flow CallAdapter

当 API 返回 `Flow<Business<T>>` 时，CallAdapter 告诉 Retrofit 实际响应类型是 `ApiResponse<T>`，每次收集时 clone 原始 Call，并依次产生：

```text
Loading
Success(data, metadata)
```

或：

```text
Loading
Error(exception, optionalData, metadata)
```

网络异常、HTTP 非成功响应和空响应体会转换为业务 Error；协程取消继续向上传播。普通 suspend API 不受该 CallAdapter 影响。

## 4. 组件解析

模板 app 对 API 注解声明的拦截器和 Customizer 提供无参构造反射兜底，并缓存实例。实际项目接入 DI 后，建议在 `resolve*` 方法中显式映射：

```kotlin
override fun resolveInterceptor(
    apiClass: KClass<*>,
    interceptorClass: KClass<out Interceptor>,
): Interceptor = when (interceptorClass) {
    AuthInterceptor::class -> authInterceptor
    else -> superOrReflection(apiClass, interceptorClass)
}
```

需要运行时 token、租户或时间戳时，向拦截器注入 Provider，在 `intercept()` 执行时读取，不要在 API 构建期发起网络请求。

## 5. 混淆规则

`architecture/consumer-rules.keep` 保留运行时注解属性，以及被反射调用的 interceptor / customizer 无参构造器。新增不同的反射创建协议时需要同步更新规则并验证 Release。

## 6. 验证命令

修改 Network 后至少执行：

```bash
./gradlew :architecture:testDebugUnitTest \
  :common:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:compileDebugKotlin \
  --configuration-cache
```

准备发布时还需要验证：

```bash
./gradlew test lint assembleDebug assembleRelease --configuration-cache
```

涉及真实请求时，应使用 MockWebServer 或测试服务验证最终 URL、Header、Converter、取消和错误转换链路。

## 7. 维护检查项

* Architecture 不得读取 app BuildConfig 或写入真实域名。
* Common 不得解释环境 flavor，也不得内置业务鉴权。
* app 的 `API_HOST` 必须是合法 HTTP(S) URL；Manager 会补齐 path 末尾的 `/`。
* 示例为本地 HTTP 回退保留 cleartext；生产项目使用 HTTPS 后应在 app 安全配置中关闭。
* 不得加入信任所有证书、主机名或占位签名实现。
* API 注解中不要从 `*-api` 模块反向引用 `*-impl` 类型。
* 新增请求 Header 前检查服务端契约和隐私合规要求。
