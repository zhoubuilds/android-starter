# App 开发文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-13 | whisper | 接入 Project 仓库 Capability 与 Aster 初始化 |
| 2026-10-13 | whisper | 将设置页面与外观偏好迁移到 Settings feature |
| 2026-09-17 | whisper | 限定 Debug 序列化运行时约束以兼容 Room 迁移测试 |
| 2026-09-17 | whisper | 接入共享分段样式、Kit Insets 和版本化 Room 迁移校验 |
| 2026-09-17 | whisper | 修正应用语言 Header 来源并约束首页 Insets 的单次处理 |
| 2026-09-16 | whisper | 同步集中组件创建职责与 GitHub Endpoint 配置来源 |
| 2026-09-16 | whisper | 统一组件解析与生命周期决策, 移除重复占位地址配置 |
| 2026-09-16 | whisper | 将网络组件单例统一交由管理器实例池持有 |
| 2026-09-16 | whisper | 明确仅传 Application 的网络管理器初始化约束 |
| 2026-09-15 | whisper | 显式映射 GitHub Endpoint 与系统参数拦截器 |
| 2026-09-15 | whisper | 规范顶层标题、项目重试和设置分区样式 |
| 2026-09-14 | whisper | 使用 Foundation 提供的全局 Starter Mono 排版 |
| 2026-09-14 | whisper | 固定设置分段控件的连续形状与选中行为 |
| 2026-09-14 | whisper | 增加数据库版本 1 到 2 的真实迁移验证要求 |
| 2026-09-14 | whisper | 接入三 Tab、多 Activity、颜色模式和应用语言设置 |
| 2026-09-14 | whisper | 装配 GitHub API、Room 数据库与 Habitat Dao 提供链路 |
| 2026-09-14 | whisper | 接入底部 Tab 与两个 feature Fragment |
| 2026-09-14 | whisper | 补充系统 Splash 向量资源, 动画维护和冷启动回归要求 |
| 2026-09-11 | whisper | 接入 monochrome 启动器主题图标并更新生成与验证规则 |
| 2026-09-11 | whisper | 记录固定品牌图标对 monochrome 建议检查的局部抑制 |
| 2026-09-11 | whisper | 明确传统 WebP 与 Adaptive Icon 的资源目录约束 |
| 2026-09-11 | whisper | 更新固定品牌主题, Foundation 分层及原创 S 图标维护规则 |
| 2026-09-11 | whisper | 建立 Starter 图标, 主题和示例装配维护规则 |

本文补充 app 的维护约束. 通用规则见 [代码规范](../application/code-style.md) 和
[开发规范](../application/development.md), 设计边界见 [App 设计文档](design.md).

## 1. 维护范围

| 内容 | 维护位置 |
| --- | --- |
| 应用名称 | `app/src/main/res/values/strings.xml` |
| 共享固定配色和主题 | `foundation/src/main/res/` |
| App 主题入口 | `app/src/main/res/values/themes.xml` |
| 启动图标 | `app/src/main/res/` 中的 drawable 和 mipmap 资源 |
| 系统 Splash 绑定 | `app/src/main/res/values-v31/themes.xml` |
| 系统 Splash 图标 | `app/src/main/res/drawable/ic_splash_screen.xml` 与 `ic_splash_screen_vector.xml` |
| Splash 动画与时长 | `app/src/main/res/animator/splash_accent_alpha.xml` 与 `values/integers.xml` |
| 图标设计输入与预览 | `design/brand/icon.json` 及同目录 SVG / PNG |
| 图标生成工具 | `scripts/generate-brand-icon.mjs` |
| 品牌种子配置 | `design/brand/theme-seed.properties` |
| 应用和 Activity 声明 | `app/src/main/AndroidManifest.xml` |
| 首页 Tab 组合 | `MainActivity` 与 `activity_main.xml` |
| 应用设置、外观偏好与 About 页面 | `feature:settings` |
| Project 跨模块仓库契约 | `feature:project-api` |
| 应用语言声明 | `app/src/main/res/xml/locales_config.xml` 与 AppCompat Locale metadata |
| 组件初始化 | `StarterApplication` |
| 应用构建配置 | 根目录 `app-config.toml` |
| GitHub Endpoint 配置 | `app-config.toml` 的 `buildConfig.GITHUB_API_ENDPOINT` |
| Retrofit 占位 base URL | `NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL` |
| Retrofit、OkHttp、具体 Endpoint 和请求头装配 | app 的 `network` 包 |
| RoomDatabase 装配 | app 的 `database` 包 |

当前首页由 app 维护底部 Tab, 页面内容分别由 Function、Project 和 Settings Fragment 提供. GitHub 网络模型与缓存逻辑归
Project, 应用设置与应用信息归 Settings, app 维护 Endpoint、唯一 RoomDatabase 和进程初始化顺序.

## 2. 主题维护

* 保持 `Theme.Material3Expressive.DayNight.NoActionBar` 的主题继承, 使用现有 Material Components
  `1.14.0`, 不将无关依赖升级混入品牌或样式变更.
* 品牌种子为 `#006A86`, 由官方 `SchemeTonalSpot` 生成固定浅色和深色语义色. 共享配置和资源生成规则
  见 [Foundation 开发文档](../foundation/development.md), 不在 app 维护第二套颜色表.
* app 的 `Theme.Androidstarter` 继承 `Foundation.Theme`. 应用入口或平台专用配置留在 app, 跨页面主题,
  颜色和设计 token 放在 foundation. 不为 Starter 品牌修改 Architecture, Kit 或 Aster 的公共契约.
* 页面和组件引用主题语义属性, 不硬编码品牌色或单独指定字体. Starter Mono 字体族和 Material 3 Typography 由
  Foundation 统一提供, 顶层目的地 Toolbar 使用 `Foundation.TextAppearance.TopLevelTitle` 的原生 600;
  字号、行高、形状和组件状态优先继承 Material 默认样式.
* 设置页的主题和语言选择直接使用 Foundation 的 `Foundation.Widget.SegmentedButtonGroup` 与
  `Foundation.Widget.SegmentedButton`, 保持连续分段轮廓. 不使用
  Expressive 的选中态 shape state list, 避免选择变化时改变分段边角和组内间距.
* 设置页不同配置区之间保持 24dp 垂直间距, 不添加贯穿内容区的分隔线. Project 错误态的重试操作使用
  OutlinedButton 和错误语义色, 不退化为弱提示式 TextButton.
* 不调用 `DynamicColors.applyToActivitiesIfAvailable()` 或接入动态颜色 overlay. 颜色模式默认跟随系统,
  也允许用户固定浅色或深色; 三种模式均使用 Foundation 的固定配色, 不从壁纸获取颜色.
* 四个 Activity 均调用 `enableEdgeToEdge()` 并使用 Kit `applyWindowInsetsPadding()` 处理实时系统栏与刘海 Insets.
  MainActivity 使用 `bottom = false`, 底部继续由 BottomNavigationView 处理; 普通详情处理四边,
  FunctionDemoActivity 加入 IME 并声明 `adjustResize`. 不用主题色或隐藏系统栏规避遮挡.
  页面在设置初始 padding 后安装一次扩展, 不覆盖其 listener; API 28 刘海由根 View 完整处理, 不分摊给嵌套 View.
  系统栏图标明暗仍由 Activity 的 edge-to-edge 接入与实际背景匹配.
* 交互组件使用 Material 默认状态和最小 48dp 触控目标. 手机布局以 16dp 水平间距、24dp 图标和主题文字样式为基线;
  只有具体组件规范或内容密度需要时才偏离, 并在布局中说明理由.

## 3. 图标维护

图标的设计输入与生成工具一并备份在工程中. 调整原创 S 的几何形状或配色时, 由同一输入同步生成预览,
传统图标和 Adaptive Icon, 不单独手改一个平台版本. 两个色块保持水平对齐.

Adaptive Icon 的关键识别部分保留在中央安全圆内, 不将启动器遮罩提前烘焙到前景或背景图层中.
普通图标使用固定品牌色, 不依赖 `values-night` 颜色变化. 两份 Adaptive Icon XML 均引用
`drawable/ic_launcher_monochrome.xml`, 供支持的启动器按用户设置重新着色.

monochrome 使用与前景一致的 108 viewport 和安全区域, 主体及两个水平色块均保留为单色不透明轮廓.
生成工具同步维护彩色前景, 单色图层和引用.

修改图标时同步检查:

* Manifest 的 `android:icon` 和 `android:roundIcon` 均能解析到正确资源.
* API 24 / 25 按屏幕密度解析 `mipmap-mdpi` 至 `mipmap-xxxhdpi` 中的传统 WebP 图标.
* API 26+ 的前景和背景完整, 在圆形及其它启动器遮罩下不裁掉主体.
* VectorDrawable 放在 `drawable` 目录, `mipmap-anydpi-v26` 中只保留 Adaptive Icon XML.
* 密度资源与 Adaptive Icon 的向量图层保持同一设计, 不残留旧图标.
* 浅色和深色下普通图标保持同一品牌配色, 轮廓清晰, 不存在意外边框或透明度问题.
* API 33+ 的单色图层与普通图标保持相同位置和尺度, 两个水平色块不会因去色而丢失.

Android 13+ 需要启动器支持并由用户启用主题图标, 才使用 monochrome 进行壁纸或系统主题着色.
该设置不改变应用内的固定配色, 不应添加 `DynamicColors` 注册或动态颜色 overlay.

`design/brand/icon-monochrome.svg` 是实际单色 Adaptive Icon 图层的设计备份, 与 Android 单色资源一起生成.
`icon-play-store.png` 保留完整正方形背景, 不将启动器的圆角遮罩烘焙到商店素材中.
生成命令和可选栅格工具依赖见 [App 使用文档](usage.md#3-替换应用品牌).

### 3.1 系统 Splash 维护

API 31+ 通过 `android:windowSplashScreenAnimatedIcon` 直接引用 `ic_splash_screen`, 不用单独的启动
Activity 承载系统 Splash. 该 AnimatedVectorDrawable 与 `ic_splash_screen_vector` 及
`design/brand/icon-splash.svg` 由 `generate-brand-icon.mjs` 生成, 图标几何继续来自 `design/brand/icon.json`.
手动维护 `animator/splash_accent_alpha.xml`, `values/integers.xml` 中的时长和 `values-v31/themes.xml`
中的系统绑定, 不在生成输出上单独修改形状.

保持以下关系:

* 底图为 288dp, viewport 为 108, 与彩色前景使用相同的几何和位置. 石墨背景填满画布, 遮罩由系统应用.
* 两片电青 target 共用 `splash_accent_alpha`, 仅动画化 `fillAlpha`, 从 `0.65` 到 `1`.
* `splash_icon_animation_duration` 统一为 180ms, animator 和 `windowSplashScreenAnimationDuration`
  引用同一整数资源. 该时长不是 app 最短展示或等待时间, 实际显示取决于系统与启动进度.
* 调整 Splash 时分别检查 launcher, Adaptive Icon 和 monochrome 资源, 避免混淆不同平台入口的图层用途.
* 不为播放动画阻塞 Application 初始化, 延迟 Activity 绘制或增加等待逻辑. 共享日夜配色继续由 Foundation
  提供, Splash 配置留在 app.

## 4. 初始化与保护范围

app 当前承担颜色模式、Aster、数据库、Habitat、`ApiFactory.install()` 和 Quill writer 的组合根职责.
`StarterApplication` 先调用 Settings 的 `AppAppearancePreferences.initialize()` 应用已保存的颜色模式, 再调用
`Aster.initialize()` 发布最终 Manifest 中发现的 Registry. 数据组件顺序必须保持为
`StarterDatabase.initialize()`、`HabitatFactory.initialize()`、`ApiFactory.install()`, 避免 Habitat 生成的 Dao provider
在数据库实例可用前被消费. Kit 不持有全局 Application, app 不需要执行 Kit 初始化.
网络工厂每进程只能成功安装一次, 不通过重复安装来刷新页面, 恢复请求或切换运行期环境.

网络安装使用 `ApiFactory.install(StarterNetworkComponentManager(this))`. 管理器只接收 `Application`, 默认 Retrofit
直接使用 `NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL`, 不再读取 `BuildConfig.API_HOST` 或自行规范化占位地址.
默认 OkHttp 配置只调用 `resolveInterceptor` 取得公共 Header 拦截器, 与 API 注解解析共用同一内部入口,
不在配置方法中重复构造组件或访问实例池. 公共 Header 的 Locale 在每次请求时优先读取
`AppCompatDelegate.getApplicationLocales()` 的首选项; 跟随系统时回退 `ClientInfoUtils.getPrimaryAppLocale(context)`.
Android 24-32 的 Application 资源不会随 AppCompat 显式语言选择更新, 不能只读取该资源作为应用语言;
也不缓存 Locale 或依赖异步持久化完成后才生效. Kit 工具继续表达传入 Context 的资源 Locale, 不改变其通用契约.

组件的 `resolve*` 方法决定实例复用或每次创建. 当前统一调用 `getOrCreateComponent()` 从实例池获取,
不传构造 lambda; 池内缺项时由 `createComponentInstance()` 集中构造带依赖组件, 未匹配类型交给
`createComponentReflectively()` 无参反射创建, 包括符合构造约定的 `RetrofitCustomizer`.
当前无参兜底与三个拦截器均按声明类型在当前管理器中延迟创建并复用, 创建失败不写入缓存,
不为每个单例增加成员字段. 共享实例必须满足并发使用要求,
有可变调用状态的组件应按实际作用域调整解析策略. 实例池的并发和工厂约束见
[Network 组件解析](../network/development.md#4-组件解析).

`StarterNetworkComponentManager` 直接创建 Project 提供的 `GithubEndpointRoutingInterceptor(endpoint)` 和
`GithubSystemParametersInterceptor(userAgent)`, 分别注入 `BuildConfig.GITHUB_API_ENDPOINT` 与由构建配置中应用 ID、版本组成的
`User-Agent`. 这两个组件需要应用配置, 使用显式工厂, 不额外建立 app 子类. GitHub 的固定协议 Header 归 Project
拦截器维护. 组件是否显式注册不能代替实际行为审查; Customizer 调整 client、callFactory 或其它网络配置时,
仍需确保公共 Header、路由和网络策略不会被意外绕过.

`GITHUB_API_ENDPOINT` 在根目录 `app-config.toml` 的 `[default]` 中声明, 当前为 `https://api.github.com/`,
由 Prism 生成 app 的 `BuildConfig`. 修改后需要重新构建安装, 不作为运行时设置; feature 不读取 app 构建配置.
当前路由只使用 Endpoint 的 scheme、host 和 port, 原请求 path、query 和 fragment 保持不变.

当前 app 源码没有单独声明的 `@aegis` 保护类型. 调用下层 API 不代表可以修改下层契约; 涉及公共能力时
仍须检查源码标记及对应模块开发文档的保护范围.

## 5. Feature 组合约束

当前 feature 遵守以下规则:

* `:feature:function`、`:feature:project` 与 `:feature:settings` 由 app 引入并完成最终装配, 三个 feature 不反向依赖 app.
* Feature 实现模块不互相依赖. Settings 和 Project 只共同依赖 `:feature:project-api`; API 模块声明仓库 Capability,
  不持有页面、资源、网络模型或实现类型.
* `MainActivity` 使用 add/show/hide 切换三个根 Fragment, 避免 Tab 往返时重复创建页面并丢失 View 状态.
* Fragment 是 app 组合根直接使用的实现入口, 不声明路由字符串、Capability 或跨 feature 参数契约.
* Function 的组件详情和 Settings 的“关于 Starter”使用独立且未导出的 Activity. 模块内部显式 Intent 不需要注册 Aster;
  任何外部模块可访问的页面仍必须先在对应 `*-api` 中声明稳定路由.
* Project 使用 `project.info.repository` 注册仓库入口 Capability. Settings 只依赖 Project API 和 Aster runtime,
  不引用 Project 实现; 其它跨模块能力沿用相同 API/实现分离规则.
* 演示项目允许为了展示 Android Starter 能力引入较完整的分层, 但不要在需求出现前创建空 API 模块或无消费者契约.

Project 的 GitHub API 使用普通 suspend 返回类型, 避免 `BusinessFlowCallAdapterFactory` 将原生 JSON
错误解析为 `ApiResponse<T>`. Repository 映射成功, HTTP 错误, 网络错误和空字段; 协程取消必须继续传播.
不伪造 GitHub 未返回的业务响应码. `description` 等可空字段必须保留真实可空性.

接口只声明相对路径及 API 级网络组件类型, 不读取 app `BuildConfig` 或硬编码完整域名. Project 的 GitHub Endpoint
拦截器使用 app 注入的地址改写占位 origin, GitHub 系统参数拦截器统一提供 `Accept: application/vnd.github+json`、
`X-GitHub-Api-Version: 2026-03-10` 和 app 注入的 `User-Agent`. 公开仓库请求无需 Token;
GitHub 匿名 REST API 的常规配额为每个来源 IP 每小时 60 次, 多台设备可能共享
出口配额. 页面应提供手动刷新和失败重试, 使用缓存减少重复请求, 离线时仍显示静态应用信息和已有数据.
遇到限流时依据 HTTP 状态和响应头提示, 避免自动连续重试.

`StarterDatabase` 是当前唯一 RoomDatabase, version 为 2. Project 的 Entity 和 Dao 是数据库装配输入, 由 Habitat
生成 provider 并在运行时按类型取得. 新增 Entity、修改列或提高数据库版本时必须提供迁移, 扩展持久化测试, 并确认
Debug/Release 两个变体的 Room 实现、Habitat registry 和 merged manifest 都能生成.

版本 2 将旧的 `about_github_project` 表重命名为 `project_github_project`, 使用显式 `Migration(1, 2)` 保留已经缓存的仓库数据.
不得删除该迁移或用 destructive migration 替代, 除非已经证明所有版本 1 安装都不再需要升级.

app 使用与 runtime/compiler 同版本的稳定 Room Gradle 插件, 通过 `room.schemaDirectory("$projectDir/schemas")`
和 `exportSchema = true` 导出 `app/schemas/<数据库全限定名>/<版本>.json`. 插件自动将快照作为 AndroidTest assets,
不放入正式 app assets. 快照属于需版本化的数据库契约; 不手改当前生成结果, 不覆盖旧版本以掩盖 schema 变化.
Entity 变化时按实际兼容影响提升数据库版本, 提供显式迁移并新增快照.

`1.json` 不是历史留存导出: 2026-09-17 依据原迁移测试中已记录的版本 1 建表与唯一索引 SQL 重建等价 Room 模型,
由 Room 2.8.4 编译器生成, 已使用 SQLite PRAGMA 核对 13 列、空值、主键和唯一索引一致. `2.json` 直接来自当前生产定义.

Room 2.8.4 的迁移测试依赖 Kotlinx Serialization 1.8.1 的运行时接口. AGP 将 AndroidTest 的共享依赖版本对齐到目标
Debug APK, 因此仅在 `debugImplementation` 添加 `kotlinx-serialization-core` 的最低版本约束, 将已有传递依赖对齐到 1.8.1.
仅给 `androidTestImplementation` 添加版本不能解决目标 APK 固定到旧版本的问题. 该约束不新增 Release 依赖,
也不改变生产网络使用的 Gson 配置; 后续调整 Room 或相关依赖时需重新检查目标和测试两侧运行时兼容性.

## 6. 验证

主题和图标变更至少进行资源链接和受影响变体构建:

```bash
./gradlew :foundation:assembleDebug :app:assembleDebug :app:assembleRelease --no-configuration-cache
git diff --check
```

连接设备或模拟器后运行定向主题测试:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.whisper.starter.StarterThemeInstrumentedTest \
  --no-configuration-cache
```

[`StarterThemeInstrumentedTest`](../../app/src/androidTest/java/com/whisper/starter/StarterThemeInstrumentedTest.kt)
包含日夜语义色对比度, 基础文字禁用与反色状态, MainActivity 固定品牌与明暗模式接入三项测试.

还需通过设备或模拟器覆盖 API 24 / 25 传统图标, API 26+ Adaptive Icon, 系统浅色和深色模式.
在 Android 13+ 支持的启动器中分别关闭和启用主题图标, 检查品牌原色和单色着色效果, 并更换壁纸确认
主题图标按启动器设置重新着色.
在支持动态取色的设备上更换壁纸, 确认应用内语义色不变化. 检查启动器图标, 应用启动, 页面文字对比度,
系统栏明暗与 Insets, 以及现有首页交互.
编译通过不能代替这些界面验证; 未验证的平台或模式应在交付中明确说明.

Splash 修改还需执行冷启动回归:

* 在 API 31+ 真机或模拟器上结束应用进程, 从启动器重新打开, 覆盖浅色和深色模式. 优先复核曾出现模糊的
  OPPO API 34 设备, 再扩大到其它系统实现.
* 使用设备原生尺寸录屏逐帧检查, 同时观察图标进入过程和停稳阶段. 检查边缘清晰度, 系统遮罩, 图标位置
  和尺度, 避免把过渡缩放或录屏二次缩放造成的模糊误认为静态绘制问题.
* 检查两片电青色块的透明度过渡, 以及窗口背景和进入 Activity 时的衔接, 确认没有增加等待或出现第二个
  启动画面. 单独校验动画文件的 180ms 不能证明系统会完整展示同样时长.
* 在 API 30 及以下验证应用仍正常启动. 分别按对应平台能力检查普通图标和主题图标, 确认其显示不受
  Splash 专用资源影响.

截图, 录屏和 A/B 结论只说明实际验证过的设备与系统版本, 不作为所有厂商绘制结果的保证.

当前 feature 变更需要覆盖两个 AAR、app Debug/Release、AndroidTest 编译, 三个底部 Tab、详情 Activity 返回栈和 Activity
重建后的选中项恢复. 设置变更需覆盖颜色模式、应用语言、进程重启恢复和中英文资源. GitHub 数据还需覆盖响应映射、错误与限流、取消传播、重试、缓存新鲜期和离线展示;
Habitat 集成测试验证生成的 Dao provider 能完成 Room 读写. Aster 验证覆盖 Project Registry 生成、最终 merged manifest、
类型安全 Capability 解析及 About 仓库地址展示.

语言 Header 回归应覆盖 API 32 及以下显式应用语言与系统不同、切换后即时请求和恢复跟随系统.
首页 Insets 回归通过真实 View 分发覆盖导航栏位于左侧、右侧和底部, 并确认重复分发不会累加 padding.

数据库迁移测试使用稳定 `MigrationTestHelper` 从版本 1 快照建立真实 SQLite 数据库并插入缓存,
通过生产 `MIGRATION_1_2` 迁移到版本 2 并验证完整 schema, 最后重新使用生产 Room Dao 验证缓存内容保留.
只在全新版本 2 数据库上完成 Dao 往返不能替代迁移测试.
