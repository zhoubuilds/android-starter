# App 设计文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-13 | whisper | 将设置与应用信息页面迁移到独立 Settings feature |
| 2026-09-17 | whisper | 复用共享分段样式与 Kit Insets 并开启 Room schema 版本化 |
| 2026-09-17 | whisper | 明确应用语言请求头与首页系统栏 Insets 的处理边界 |
| 2026-09-16 | whisper | 将 GitHub Endpoint 归入构建配置并集中创建网络组件 |
| 2026-09-16 | whisper | 统一网络组件解析入口并复用公共占位地址 |
| 2026-09-16 | whisper | 将网络组件创建职责收敛到应用网络管理器 |
| 2026-09-15 | whisper | 使用接口级组件装配 GitHub Endpoint 与系统参数 |
| 2026-09-15 | whisper | 统一顶层标题、错误操作和设置分区视觉层级 |
| 2026-09-14 | whisper | 接入 Foundation 的全局 Starter Mono 排版 |
| 2026-09-14 | whisper | 固定设置分段控件形状并移除选中态形变 |
| 2026-09-14 | whisper | 重构三 Tab 与多 Activity 页面层级并加入应用设置 |
| 2026-09-14 | whisper | 完成项目概览、GitHub 网络请求与 Room/Habitat 缓存装配 |
| 2026-09-14 | whisper | 建立 Function/About Fragment 与底部 Tab 组合边界 |
| 2026-09-14 | whisper | 使用独立向量动画作为系统 Splash 图标并明确资源归属 |
| 2026-09-11 | whisper | 支持启动器主题图标, 保持应用内固定日夜配色 |
| 2026-09-11 | whisper | 传统启动图标改用多密度 WebP, 修正 mipmap 中的向量资源组织 |
| 2026-09-11 | whisper | 确定原创 S 图标和固定品牌配色, 将共享设计系统归入 Foundation |
| 2026-09-11 | whisper | 建立 Starter 图标和主题设计, 明确后续示例模块边界 |

## 1. 定位与当前范围

`app` 是 Starter 示例应用和最终组合根, 负责应用身份, 主题选择, 平台组件声明, 网络环境和应用级组件装配.
`foundation` 维护应用内共享的设计系统. Architecture, Kit, Aster 等通用技术模块不持有 Starter 的品牌色,
页面结构或 GitHub 仓库配置.

当前应用名称为 `Starter`. 首页使用“演示 / 项目 / 设置”三个底部 Tab, 分别由 `feature:function`、
`feature:project` 和 `feature:settings` 提供. 项目页通过 GitHub 公共 API 读取仓库信息,
并使用 Room/Habitat 缓存最近一次成功结果.

## 2. 主题与品牌

应用使用 Material Components `1.14.0` 的 `Theme.Material3Expressive.DayNight.NoActionBar`, 沿用 Android
Views 和 XML 资源. `foundation` 提供 `Foundation.Theme`, app 的 `Theme.Androidstarter` 继承它并作为
Manifest 的主题入口. 全局字体使用 Foundation 提供的 Starter Mono, 顶层目的地标题统一使用原生 600;
字号、行高、形状、组件样式和动效继续继承 Material 3 Expressive 默认值.

品牌素材使用石墨 `#1B2025`, 冰银 `#EAF1F5` 和电青种子 `#006A86`. 固定浅色和深色配色由官方 Material
Color Utilities 的 `SchemeTonalSpot` 按对比度级别 `0.0` 生成. 生成配色覆盖 49 个颜色角色, 包括固定色,
反色, 表面容器, 错误色, tint, scrim 和 shadow. 页面使用语义角色, 不将图标原色直接用于所有控件.

颜色模式默认跟随系统, 用户也可以在设置页固定为浅色或深色. 配色不跟随桌面壁纸,
`StarterApplication` 不注册 `DynamicColors`. 设置页还支持跟随系统、简体中文和英文三种应用语言选择.
共享资源的归属和生成方式见 [Foundation 设计文档](../foundation/design.md).

品牌设计源文件和种子配置备份在 [`design/brand/`](../../design/brand/), 生成工具位于
[`scripts/`](../../scripts/). 素材备份属于工程维护输入, 不随 APK 打包.

## 3. 应用图标

图标是由模块块面组成的原创 S 变形, 使用 6 度倾斜, 小圆角和首尾斜切, 两个色块保持水平对齐.
图标色值独立于日夜主题, 普通图标在浅色和深色模式下均保留固定品牌配色.

图标提供以下平台形式:

| 平台 | 图标形式 |
| --- | --- |
| Android 7.0 / 7.1, API 24 / 25 | `mipmap-mdpi` 至 `mipmap-xxxhdpi` 中的传统 WebP 启动图标 |
| Android 8.0 及以上, API 26+ | 前景和背景分离的 Adaptive Icon, 适配启动器遮罩 |
| Android 13 及以上, API 33+ | 提供 monochrome 图层, 供支持主题图标的启动器重新着色 |

两份 Adaptive Icon XML 均引用 `drawable/ic_launcher_monochrome.xml`. 单色图层与彩色前景使用相同的
108 viewport 和安全区域, 保留主体及两个水平色块的不透明轮廓. `design/brand/icon-monochrome.svg`
保存对应单色 Adaptive Icon 素材.

在 Android 13+ 上, 用户在支持的启动器中启用主题图标后, 启动器根据壁纸和系统主题重新着色.
普通图标继续使用品牌原色. 启动器主题图标与应用内配色分别配置, 不需要为此在 Foundation 或 Application
接入 `DynamicColors`.

### 3.1 系统 Splash 图标

Android 12+, API 31+ 的系统 Splash 使用 app 独立提供的 AnimatedVectorDrawable. OPPO API 34 的冷启动
录屏发现, 之前的静态图标在停稳后边缘仍然模糊; 独立矢量动画资源用于改善这个启动阶段的绘制问题.
系统 Splash 的实际显示由平台实现决定, 不将单个系统的处理路径推论为所有厂商的清晰度保证.

`values-v31/themes.xml` 将 `android:windowSplashScreenAnimatedIcon` 直接绑定到
`drawable/ic_splash_screen.xml`, 其底图为 `drawable/ic_splash_screen_vector.xml`. 底图尺寸为 288dp,
viewport 为 108, 沿用 Adaptive Icon 前景几何, 并提供石墨色满画布背景供系统遮罩裁切.
启动窗口背景使用 `colorSurface`, 随应用的浅色和深色模式变化.

动画只将两片电青色块的 `fillAlpha` 从 `0.65` 变化到 `1`, 时长为 180ms. 两个 target 共用同一 animator,
动画和系统 Splash 时长配置共用 `splash_icon_animation_duration`. app 不新增启动 Activity 或等待逻辑,
也不等待动画结束才进入首页.

Splash 是 app 的应用身份和平台启动资源, 不进入 Foundation 共享主题. 它与桌面 launcher, Adaptive Icon,
monochrome 的用途分别维护; 图标几何仍由同一设计输入生成. 静态设计备份为
[`design/brand/icon-splash.svg`](../../design/brand/icon-splash.svg), 该 SVG 不记录动画时间线.

## 4. 组合根边界

* `app` 直接组合三个 feature Fragment, 装配 Quill、`ApiFactory`、Room 和 Habitat, 持有应用 `BuildConfig`、
  具体网络 Endpoint、应用请求参数、最终主题入口与应用语言平台声明.
* `feature:settings` 持有颜色模式偏好、语言选择页面和应用信息页面, 向 app 暴露启动期外观初始化入口.
* `foundation` 承载应用业务基座, 业务状态处理和共享设计系统, 不读取 app 的构建变体或域名配置.
* `architecture` 保持通用业务状态, UI 和网络创建契约.
* 仅服务示例页面的数据映射和交互放入对应 feature, 不上移为公共技术契约.

`StarterApplication` 只向 `StarterNetworkComponentManager` 传入自身并安装管理器. 网络管理器通过统一解析入口装配
公共 Header、GitHub Endpoint 和系统参数拦截器, 由解析方法决定实例复用或按次创建; 当前三个拦截器使用实例池复用.
实例池缺项时由管理器集中创建组件, 默认配置与解析调用方不再提供构造函数.
`Application` 用于公共 Header 所需的包名和资源 Locale 回退. 每次请求优先读取 AppCompat 当前显式选择的应用语言,
跟随系统时读取 Context 资源语言, 不依赖低版本 Application 资源随 AppCompat 语言选择同步变化. Retrofit 直接使用
`NetworkComponentManager.ROUTING_PLACEHOLDER_BASE_URL`, app 不再重复维护占位地址配置.
真实 GitHub Endpoint 由 `app-config.toml` 的 `buildConfig.GITHUB_API_ENDPOINT` 配置, Prism 生成 app 的
`BuildConfig.GITHUB_API_ENDPOINT` 后由管理器注入. 它是构建时确定的值, 不作为运行时应用设置.

## 5. 示例模块结构

当前使用三个 feature Android library 模块:

```text
app
|- feature:function
|- feature:project
|- feature:settings
`- foundation

feature:* -> foundation / material
```

`function` 提供 RecyclerView 演示目录和独立组件详情 Activity, `project` 提供项目概览, `settings` 提供设置页和
独立的 `AboutStarterActivity`. `MainActivity` 仅作为顶层 Tab 壳, 直接创建并保留三个根 Fragment; 详情页面不继续堆入
MainActivity 的 Fragment 返回栈. 三个 feature 当前互不依赖, 也不声明跨模块路由或 Capability.

直接暴露 Fragment 只服务 app 组合根, 不替代公开业务契约. 后续出现跨模块路由或 Capability 时,
先建立对应的 `*-api` 模块, 由 API 模块声明稳定路径、参数、能力接口和能力名称, 再由实现模块接入 Aster.
不能因为直接写字符串也能完成导航, 就让调用方依赖实现模块中的隐式路径. 作为 Android Starter 演示项目,
可以为了完整展示模块化契约采用比普通小型应用更明确的分层, 但每项机制仍需对应实际演示需求.

Project 使用 [GitHub REST API](https://api.github.com/repos/zhoubuilds/android-starter) 的匿名公开接口,
无需自建服务器或嵌入 Token. GitHub 返回原生 JSON 对象, 不符合当前 `ApiResponse<T>` 的
`code/msg/data` 格式, 因此 project 数据层通过现有 `ApiFactory` 创建普通 suspend API 并自行映射页面状态.
`GithubProjectApi` 通过 `@ApplicationInterceptors` 声明 Endpoint 路由和 GitHub 系统参数类型, app 的
`StarterNetworkComponentManager` 直接创建 Project 提供的具体拦截器, 注入 GitHub Endpoint 和应用标识构成的
`User-Agent`, 不通过空抽象类型与 app 子类重复表达同一组件. Retrofit 保持使用公共占位地址初始化,
真实 GitHub origin 只在请求执行时改写. Project 系统参数拦截器维护 GitHub 共用的 `Accept` 与 API 版本,
并添加 app 注入的 `User-Agent`, 不散落到 API 方法. 该演示复用现有 Architecture 网络装配能力, 不引入新的路由机制.

项目数据成功返回后写入 Room. 首次进入先展示已有缓存; 缓存写入后 15 分钟内视为新鲜, 超时才自动请求;
下拉刷新始终请求网络. 网络失败、服务异常或匿名请求限流时保留已有缓存并显示非阻断提示.

页面遵循 Material 3 Views 组件约定: 使用主题语义色和公开文字样式, 交互目标至少为 48dp, 图标使用 24dp 基线,
手机页面默认使用 16dp 水平内容间距. 模式选择使用连续的单选分段按钮, 左右两端只保留各自外侧圆角, 中间分段不保留
外侧圆角; 选中状态只改变语义颜色, 不改变分段几何形状. 设置分区使用 24dp 留白建立层级, 不使用贯穿内容区的
分隔线. 项目加载失败时使用语义错误色的 OutlinedButton 提供明确重试操作. 顶层目的地使用带标签的
BottomNavigationView, 对应 Toolbar 标题使用 Starter Mono SemiBold 600.

分段按钮共同外观归 Foundation, app 不重复定义形状样式. Activity 通过 Kit `applyWindowInsetsPadding()` 复用实时
系统栏和刘海避让. 首页根布局处理顶部和左右, 底部系统栏仍由 BottomNavigationView 自身处理;
普通详情页处理四边, 输入详情额外选择 IME. 这些页面选择留在调用处, Kit 不持有页面或导航语义.

Room schema 由 app 组合根导出至 `app/schemas/` 并随源码版本化, 供迁移校验使用. 导出配置不改变运行期数据库版本、
表结构或缓存策略; Entity 仍由 Project 提供, 唯一 RoomDatabase 与迁移仍由 app 装配.
