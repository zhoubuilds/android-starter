# Network 开发文档

## 修订记录

| 修订时间（CST） | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-08-28 | whisper | 默认禁用 cleartext 网络流量 |
| 2026-08-27 | whisper | 调整网络组件 keep rule 归属 |
| 2026-08-26 | whisper | 同步 API 注解约束与 Architecture 拦截器实现 |
| 2026-08-26 | whisper | 简化 ApiFactory 安装快照并移除代际状态 |
| 2026-08-26 | whisper | CallAdapter 迁移到 Business<M, D> |
| 2026-08-25 | whisper | 记录 Starter 网络实现、测试和维护边界 |

本文面向 Network 维护者。设计取舍见 [设计文档](design.md)，业务接入见 [使用文档](usage.md)。

## 1. 源码结构

```text
architecture/.../architecture/network/
|- ApiFactory.kt
|- OkHttpClientFactory.kt
|- annotation/
|  |- ApplicationInterceptors.kt
|  |- NetworkInterceptors.kt
|  |- UseOkHttpCustomizer.kt
|  `- UseRetrofitCustomizer.kt
|- interceptor/
|  |- EndpointRoutingInterceptor.kt
|  `- RequestHeadersInterceptor.kt
`- component/
   |- NetworkComponentManager.kt
   |- OkHttpCustomizer.kt
   `- RetrofitCustomizer.kt

foundation/.../foundation/network/
`- BusinessFlowCallAdapterFactory.kt

app/.../starter/network/
|- StarterNetworkComponentManager.kt
|- StarterRequestHeadersInterceptor.kt
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

固定顺序不表示后置入口可以随意推翻前置契约:

* 默认配置只放所有 API 都必须具备的能力。
* 两类 interceptor 注解只表达有序增量, 不提供排除或删除默认值的语义。
* OkHttp customizer 不用于增删普通拦截器。
* Retrofit customizer 是受信任的高级入口; 当前 Builder API 技术上能替换 client 或 callFactory, 维护者必须保证公共安全策略仍然生效。

## 3. Business Flow CallAdapter

当 API 返回 `Flow<Business<BusinessMetadata, T>>` 时，CallAdapter 告诉 Retrofit 实际响应类型是 `ApiResponse<T>`，每次收集时 clone 原始 Call，并依次产生：

```text
Loading
Success(meta, data)
```

或：

```text
Loading
Failure(exception, meta, data)
```

业务失败响应会保留完整 Meta 和 data。网络异常、HTTP 非成功响应和空响应体没有可解析的业务响应，因此转换为
`Failure(exception, BusinessMetadata.EMPTY, null)`；协程取消继续向上传播。普通 suspend API 不受该 CallAdapter 影响。

## 4. 组件解析

模板 app 对 API 注解声明的拦截器和 Customizer 提供无参构造反射兜底，并缓存实例。反射只是无依赖组件的便利回退,
不是业务组件生命周期协议。实际项目接入 DI 后, 应在 `resolve*` 方法中显式映射:

```kotlin
override fun resolveInterceptor(
    apiClass: KClass<*>,
    interceptorClass: KClass<out Interceptor>,
): Interceptor = when (interceptorClass) {
    AuthInterceptor::class -> authInterceptor
    else -> superOrReflection(apiClass, interceptorClass)
}
```

需要运行时 token、租户或时间戳时，在 app 的具体拦截器中按请求解析；实现层可以向该子类注入 Provider，
但 Provider 不属于 Architecture 契约。不要在 API 构建期发起网络请求。

API 注解应引用稳定契约或 marker, 由 app 将其映射到具体实现。当 API 位于 `*-api` 模块时, 不得引用 `*-impl` 类型。
`UseRetrofitCustomizer` 属于特权入口, 必须覆写 `resolveRetrofitCustomizer()` 显式映射并审查实现, 不依赖无参反射回退。

`RequestHeadersInterceptor` 是默认链中的公共 Header 模板，app 子类通过 `resolveRequestHeaders(request)` 提供实际值。
API 级路由拦截器继承 `EndpointRoutingInterceptor`,
并由 app 提供目标 Endpoint。对请求的常规修改顺序是公共 Header、Endpoint 路由、鉴权、签名; 签名必须看到最终 URL 和 Header。

## 5. 混淆规则

`architecture/consumer-rules.keep` 只保留 `ApiFactory` 读取 API 声明所需的运行时注解属性、默认值和注解类型,
不规定 interceptor / customizer 的实例化方式。

Starter app 当前通过 `StarterNetworkComponentManager` 反射调用无参构造器, 因此由 `app/proguard-rules.pro` 使用
`-keepclasseswithmembers` 同时保活具备无参构造器的组件实现类和该构造器。只使用 `-keepclassmembers` 不足以防止实现类整体被裁剪。
接入 DI 或改为显式映射后, 应在 app 中删除或收窄这些通用反射规则。新增其它反射创建协议时也由选择该协议的实现模块提供规则。

keep rule 调整必须使用启用 R8 的 application 变体验证最终合并配置和产物。仅构建未开启混淆的 Release 不能证明规则有效。

## 6. 验证命令

修改 Network 后至少执行：

```bash
./gradlew :architecture:testDebugUnitTest \
  :foundation:testDebugUnitTest \
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
* Foundation 不得解释环境 flavor，也不得内置业务鉴权。
* app 的 `API_HOST` 必须是合法 HTTP(S) URL；Manager 会补齐 path 末尾的 `/`。
* 示例使用 HTTPS 占位回退并默认禁用 cleartext; 实际项目需要本地 HTTP 时由 app 增加范围受限的例外.
* 不得加入信任所有证书、主机名或占位签名实现。
* API 注解中不要从 `*-api` 模块反向引用 `*-impl` 类型。
* 可选拦截器使用 API 注解增量声明, 不放入默认配置后再为部分 API 设计排除规则。
* 不使用 Customizer 增删普通拦截器; 组件顺序由 interceptor 注解明确表达。
* `EndpointRoutingInterceptor` 默认不使用目标 Endpoint 的 path; 需要网关 path 前缀时覆写 `buildTargetUrl()` 并添加边界测试。
* `UseRetrofitCustomizer` 必须由 app 显式映射并审查, 不得绕过已安装的证书、鉴权和公共拦截器。
* 同一 Retrofit API 内出现不同网络组合时拆分接口, 不引入方法级排除或覆盖逻辑。
* 新增请求 Header 前检查服务端契约和隐私合规要求。
* `ApiFactory.install()` 正常情况下只调用一次；重复安装的最后快照只用于尽力恢复, 不得作为环境切换协议。
