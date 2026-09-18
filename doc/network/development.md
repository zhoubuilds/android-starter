# Network 开发文档

## 修订记录

| 修订时间（CST） | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-16 | whisper | 明确集中创建入口与 GitHub Endpoint 构建配置 |
| 2026-09-16 | whisper | 统一组件解析规则并移除重复占位配置与空壳实现 |
| 2026-09-16 | whisper | 统一组件实例池并明确并发创建与复用约束 |
| 2026-09-16 | whisper | 明确网络管理器内部组件与构建配置的持有规则 |
| 2026-09-15 | whisper | 记录 GitHub API 级拦截器的显式映射规则 |
| 2026-09-01 | whisper | 明确 ApiFactory 重复安装失败约束 |
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

feature/project/.../data/network/
|- GithubEndpointRoutingInterceptor.kt
|- GithubProjectApi.kt
`- GithubSystemParametersInterceptor.kt
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

`StarterNetworkComponentManager(application: Application)` 直接使用框架的 `ROUTING_PLACEHOLDER_BASE_URL`, 创建公共
Header provider/interceptor 与两个 GitHub 拦截器. GitHub `User-Agent` 使用 `BuildConfig.APPLICATION_ID` 和
`BuildConfig.VERSION_NAME`. `Application` 仅供公共 Header 获取包名与按请求读取资源 Locale, 不缓存启动时的语言.

默认 OkHttp 配置只负责调用 `resolveInterceptor()` 并挂载公共 Header 拦截器. 公开的 API 拦截器解析方法委托同一
私有重载; 默认链只传组件类型, API 入口额外提供名称用于失败诊断. 由解析入口统一决定生命周期,
不在默认配置中直接调用实例池或重复构造组件.

`componentPool` 按组件声明类型保存实例, `resolve*` 当前统一调用 `getOrCreateComponent()` 获取, 不传构造 lambda.
该入口使用 `ConcurrentHashMap.computeIfAbsent()`, 缺项时调用 `createComponentInstance()`;
集中创建方法显式装配带依赖组件, 未匹配类型由 `createComponentReflectively()` 无参反射创建.
同一管理器中同一声明类型只成功创建一次, 多个 API 共享同一实例; 创建失败不写入实例池.
`apiName` 和 `componentName` 仅用于反射失败时提供诊断上下文, 不参与缓存 key; 不同管理器持有独立实例池.

组件工厂必须短小, 不执行网络或其它阻塞操作, 不递归读写同一个实例池. 不使用可能在竞争时重复执行工厂的 `getOrPut()`.
通过声明类型的 `java.cast()` 校验返回实例. 新增带依赖组件时扩展 `createComponentInstance()` 的分支,
在其中显式构造或从 DI 获取, 不新增逐个持有实例的字段.
Interceptor、OkHttpCustomizer 和 RetrofitCustomizer 的实例来源与生命周期都由对应 `resolve*` 决定.
无依赖且符合无参构造约定的组件可以反射创建; 需要配置、Provider 或特殊生命周期的组件使用显式工厂或 DI.
模板当前选择管理器级复用, 不是要求所有实现采用同一种生命周期. 调整生命周期时必须在解析入口明确策略并验证.
显式注册不是权限边界, 不要求 RetrofitCustomizer 为了获准执行而单独注册.

需要运行时 token、租户或时间戳时，在 app 的具体拦截器中按请求解析；实现层可以向该子类注入 Provider，
但 Provider 不属于 Architecture 契约。不要在 API 构建期发起网络请求。

API 注解应引用稳定契约或 marker, 由 app 将其映射到具体实现。当 API 位于 `*-api` 模块时, 不得引用 `*-impl` 类型。
审查 Customizer 时检查实际 Builder 修改及公共策略是否保留, 不以是否显式注册代替行为审查.

Project 示例中的 `GithubEndpointRoutingInterceptor(endpoint: HttpUrl)` 和
`GithubSystemParametersInterceptor(userAgent: String)` 同时作为 API 声明类型与具体实现.
`StarterNetworkComponentManager.createComponentInstance()` 为它们注入实际 Endpoint 和应用 `User-Agent`, 不对这两个
带依赖类型使用无参反射, 也不增加仅转发配置的 app 子类. 前者负责将占位 origin 替换为 GitHub,
后者负责所有 GitHub 请求共用的 `Accept`、API 版本和 `User-Agent`; API 方法不得重复声明这些 Header.

GitHub Endpoint 在 `app-config.toml` 的 `[default]` 中通过 `buildConfig.GITHUB_API_ENDPOINT` 配置,
由 Prism 生成 `BuildConfig.GITHUB_API_ENDPOINT`, 仅在 app 装配时解析为 `HttpUrl` 并注入.
它是构建配置, 修改后需要重新构建安装; 不提供运行时切换. 当前 Endpoint 路由只读取 scheme、host 和 port,
配置中的 path 不会作为 API 路径前缀, query 和 fragment 也不会替代原请求值.

当前固定 GitHub REST API `2026-03-10` 版本. 调整版本前先检查
[官方支持版本与结束支持日期](https://docs.github.com/en/rest/about-the-rest-api/api-versions), 阅读对应 breaking changes,
再同步实现、测试与 Project/App/Network 文档.

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

`StarterNetworkComponentManagerInstrumentedTest` 使用真实 Application 验证占位 base URL、公共 Header、
GitHub 组件映射、URL 改写、请求头、跨 API 复用和并发首次解析. 构建并安装 app 与 AndroidTest 后, 在指定模拟器运行:

```bash
adb -s emulator-5554 shell am instrument -w \
  -e class com.whisper.starter.network.StarterNetworkComponentManagerInstrumentedTest \
  com.whisper.starter.test/androidx.test.runner.AndroidJUnitRunner
```

## 7. 维护检查项

* Architecture 不得读取 app BuildConfig 或写入真实域名。
* Foundation 不得解释环境 flavor，也不得内置业务鉴权。
* Starter 默认 Retrofit 直接使用 `NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL`, 不重复配置或规范化该常量.
* GitHub Endpoint 由应用 TOML 生成 `BuildConfig.GITHUB_API_ENDPOINT` 后由 app 注入, 不在管理器中重复硬编码;
  业务模块不读取 app 构建配置.
* 示例使用 HTTPS 占位回退并默认禁用 cleartext; 实际项目需要本地 HTTP 时由 app 增加范围受限的例外.
* 不得加入信任所有证书、主机名或占位签名实现。
* API 注解中不要从 `*-api` 模块反向引用 `*-impl` 类型。
* 可选拦截器使用 API 注解增量声明, 不放入默认配置后再为部分 API 设计排除规则。
* 不使用 Customizer 增删普通拦截器; 组件顺序由 interceptor 注解明确表达。
* `EndpointRoutingInterceptor` 默认不使用目标 Endpoint 的 path; 需要网关 path 前缀时覆写 `buildTargetUrl()` 并添加边界测试。
* `UseRetrofitCustomizer` 可以由显式工厂或无参反射解析, 不得绕过已安装的证书、鉴权和公共拦截器.
* 同一 Retrofit API 内出现不同网络组合时拆分接口, 不引入方法级排除或覆盖逻辑。
* 新增请求 Header 前检查服务端契约和隐私合规要求。
* `ApiFactory.install()` 在每个进程内只能成功调用一次; 重复安装必须立即抛出 `IllegalStateException`,
  不得替换已安装的组件管理器或 API 缓存, 也不得作为错误恢复或环境切换协议.
