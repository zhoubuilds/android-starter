# App 开发文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
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
| 图标设计输入与预览 | `design/brand/icon.json` 及同目录 SVG / PNG |
| 图标生成工具 | `scripts/generate-brand-icon.mjs` |
| 品牌种子配置 | `design/brand/theme-seed.properties` |
| 应用和 Activity 声明 | `app/src/main/AndroidManifest.xml` |
| 组件初始化 | `StarterApplication` |
| 域名和构建配置 | 根目录 `app-config.toml` |
| Retrofit, OkHttp 和请求头装配 | app 的 `network` 包 |

当前首页仍为已有测试页面. Function/About 模块和 GitHub 网络示例尚未接入; 扩展这些功能时应同步更新
本组文档的实现状态和接入步骤.

## 2. 主题维护

* 保持 `Theme.Material3Expressive.DayNight.NoActionBar` 的主题继承, 使用现有 Material Components
  `1.14.0`, 不将无关依赖升级混入品牌或样式变更.
* 品牌种子为 `#006A86`, 由官方 `SchemeTonalSpot` 生成固定浅色和深色语义色. 共享配置和资源生成规则
  见 [Foundation 开发文档](../foundation/development.md), 不在 app 维护第二套颜色表.
* app 的 `Theme.Androidstarter` 继承 `Foundation.Theme`. 应用入口或平台专用配置留在 app, 跨页面主题,
  颜色和设计 token 放在 foundation. 不为 Starter 品牌修改 Architecture, Kit 或 Aster 的公共契约.
* 页面和组件引用主题语义属性, 不硬编码品牌色. 字体, 形状和组件状态优先继承 Material 默认样式.
* 不调用 `DynamicColors.applyToActivitiesIfAvailable()` 或接入动态颜色 overlay. 浅色和深色跟随系统
  明暗模式, 不从壁纸获取颜色.
* 当前 MainActivity 已调用 `enableEdgeToEdge()` 并处理系统栏 Insets. 后续 Activity 继续按屏幕布局处理
  Insets, 不用主题色或隐藏系统栏规避遮挡. 系统栏图标明暗应与实际背景匹配.

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

## 4. 初始化与保护范围

app 继续承担 `Aster.initialize()` 和 `ApiFactory.install()` 的组合根职责. 网络工厂每进程只能成功安装
一次, 不通过重复安装来刷新页面, 恢复请求或切换运行期环境.

当前 app 源码没有单独声明的 `@aegis` 保护类型. 调用下层 API 不代表可以修改下层契约; 涉及公共能力时
仍须检查源码标记及 [Architecture 开发文档](../architecture/development.md) 和
[Aster 开发文档](../aster/development.md) 的保护范围.

## 5. 后续模块接入约束

以下规则用于后续实施, 当前没有对应 feature 模块:

* 首先新增 `:feature:function` 与 `:feature:about`, 由 app 引入并完成最终装配, 两个 feature 不反向依赖 app.
* 声明 Aster Activity 的模块依次应用 Android library, KSP 和 Aster 插件, 配置唯一 segment 和 compiler
  依赖. Activity 仍需在模块 Manifest 中声明.
* 入口使用 `/function/home` 与 `/about/home`; 跨模块只依赖必要的路由和参数契约, 不直接引用 Activity
  实现类. 后续契约增长时再评估独立 API 模块.
* 需要 instrumentation tests 时按 [Aster 使用文档](../aster/usage.md) 向生产 Variant 配置 processor,
  避免 androidTest 生成同名 Registry.

About 的 GitHub API 使用普通 suspend 返回类型, 避免 `BusinessFlowCallAdapterFactory` 将原生 JSON
错误解析为 `ApiResponse<T>`. Repository 映射成功, HTTP 错误, 网络错误和空字段; 协程取消必须继续传播.
不伪造 GitHub 未返回的业务响应码. `description` 等可空字段必须保留真实可空性.

默认 Endpoint 由 app 的构建配置提供. 接口只声明相对路径, 不读取 app `BuildConfig` 或硬编码完整域名.
公开仓库请求无需 Token; GitHub 匿名 REST API 的常规配额为每个来源 IP 每小时 60 次, 多台设备可能共享
出口配额. 页面应提供手动刷新和失败重试, 使用缓存减少重复请求, 离线时仍显示静态应用信息和已有数据.
遇到限流时依据 HTTP 状态和响应头提示, 避免自动连续重试.

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

后续新增 feature 时扩大验证到 Aster Registry 生成与 merged manifest, 两个入口导航及返回行为. 新增
GitHub 请求时覆盖响应解析, 错误与限流, 取消传播, 重试, 缓存和离线展示.
