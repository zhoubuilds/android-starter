# App 设计文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-11 | whisper | 支持启动器主题图标, 保持应用内固定日夜配色 |
| 2026-09-11 | whisper | 传统启动图标改用多密度 WebP, 修正 mipmap 中的向量资源组织 |
| 2026-09-11 | whisper | 确定原创 S 图标和固定品牌配色, 将共享设计系统归入 Foundation |
| 2026-09-11 | whisper | 建立 Starter 图标和主题设计, 明确后续示例模块边界 |

## 1. 定位与当前范围

`app` 是 Starter 示例应用和最终组合根, 负责应用身份, 主题选择, 平台组件声明, 网络环境和应用级组件装配.
`foundation` 维护应用内共享的设计系统. Architecture, Kit, Aster 等通用技术模块不持有 Starter 的品牌色,
页面结构或 GitHub 仓库配置.

当前应用名称为 `Starter`. 当前实现包含应用图标和 Material 主题配置, 继续使用已有的测试首页.
`feature:function` 和 `feature:about` 尚未建立, GitHub 仓库请求也尚未接入.

## 2. 主题与品牌

应用使用 Material Components `1.14.0` 的 `Theme.Material3Expressive.DayNight.NoActionBar`, 沿用 Android
Views 和 XML 资源. `foundation` 提供 `Foundation.Theme`, app 的 `Theme.Androidstarter` 继承它并作为
Manifest 的主题入口. 字体层级, 形状, 组件样式和动效继承 Material 3 Expressive 默认值.

品牌素材使用石墨 `#1B2025`, 冰银 `#EAF1F5` 和电青种子 `#006A86`. 固定浅色和深色配色由官方 Material
Color Utilities 的 `SchemeTonalSpot` 按对比度级别 `0.0` 生成. 生成配色覆盖 49 个颜色角色, 包括固定色,
反色, 表面容器, 错误色, tint, scrim 和 shadow. 页面使用语义角色, 不将图标原色直接用于所有控件.

浅色和深色模式跟随系统明暗设置, 配色不跟随桌面壁纸. `StarterApplication` 不注册 `DynamicColors`,
不依赖设备是否支持动态取色. 共享资源的归属和生成方式见 [Foundation 设计文档](../foundation/design.md).

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

## 4. 组合根边界

* `app` 装配 Aster, Kit, Quill 和 `ApiFactory`, 持有应用 `BuildConfig` 和网络 Endpoint.
* `foundation` 承载应用业务基座, 业务状态处理和共享设计系统, 不读取 app 的构建变体或域名配置.
* `architecture` 保持通用业务状态, UI 和网络创建契约.
* 仅服务示例页面的数据映射和交互放入对应 feature, 不上移为公共技术契约.

## 5. 后续示例模块方案

本节是后续方案, 不表示模块或页面已经实现. 优先建立两个 Android library 模块:

```text
app
|- feature:function
|- feature:about
`- foundation

feature:* -> foundation / architecture / kit / aster-runtime
```

`function` 汇集通用 View 和功能示例, `about` 展示应用与仓库信息. 先保持两个模块, 在出现明确的跨模块
契约复用需求后再评估是否拆分 API 和实现模块.

两个模块使用 Aster 的跨模块 Activity 路由, 入口分别为 `/function/home` 和 `/about/home`, segment
分别为 `function` 和 `about`. Aster 不负责 Fragment 路由或统一页面导航栈. 模块内部页面可使用普通
Android 导航, 仅对外暴露必要的入口和参数.

About 计划使用原生页面展示公开仓库信息, 通过浏览器打开
[仓库主页](https://github.com/zhoubuilds/android-starter). 仓库元数据来自
[GitHub REST API](https://api.github.com/repos/zhoubuilds/android-starter), 无需自建服务器或嵌入 Token.
打开网页和原生网络请求是两项独立能力, 网络层示例由后者承担.

GitHub 返回原生 JSON 对象, 不符合当前 `ApiResponse<T>` 的 `code/msg/data` 格式. 后续应由 about
数据层适配响应, 通过现有 `ApiFactory` 创建普通 suspend API, 再转换为页面使用的业务状态. 域名继续由
app 装配, 不修改 Architecture 或 Aster 契约来适应单个示例.
