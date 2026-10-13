# Settings 设计文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-13 | whisper | 建立独立 Settings feature 与应用设置边界 |

## 1. 定位

`feature:settings` 提供 Starter 的应用设置与应用信息入口. 当前承载颜色模式、应用语言和“关于 Starter”页面,
属于面向用户的应用功能, 不属于 app 组合根的实现细节.

Settings 可以继续容纳通知偏好、隐私与协议、诊断、反馈和检查更新等应用级设置. 用户头像、登录、账号安全、会员、
收藏或订单等用户领域内容不归本模块; 出现这些能力时应建立独立的 Account 或其它业务 feature.

## 2. 组合边界

`SettingsFragment` 由 app 组合根直接创建, 与 Function、Project 一起构成三个顶层页面. app 保留 Application、
MainActivity、最终主题入口、应用语言声明和其它平台级装配; Settings 维护设置页面、偏好读写和 About 页面.

`StarterApplication` 在首个 Activity 创建前调用 `AppAppearancePreferences.initialize()` 恢复颜色模式. 该方法是 Settings
提供给组合根的最小初始化入口, 不使 Settings 反向依赖 app. 应用语言的选择界面归 Settings, AppCompat Locale metadata
与 `locales_config.xml` 仍归最终 app.

## 3. 页面与导航

设置页使用 Foundation 的连续分段样式选择颜色模式和应用语言, 并提供 About 入口. About 页面通过最终宿主的
`PackageManager` 读取应用图标、名称和版本, 不读取 app `R` 或 `BuildConfig`.

`AboutStarterActivity` 是模块内部未导出 Activity, 当前由设置页通过显式 Intent 打开. `SettingsFragment` 和外观初始化入口
只服务 app 组合, 当前不作为跨 feature 路由或 Capability 契约.

## 4. 演进原则

* 新增内容应继续围绕应用设置和应用信息, 不把“我的”页面中的所有业务入口堆入 Settings.
* app 只组合页面和调用必要初始化入口, 不重新持有 Settings 的布局、文案或偏好实现.
* 共享视觉样式归 Foundation, 通用 Android 工具归 Kit, Settings 只维护页面级使用方式.
* 出现跨 feature 导航或能力消费时, 单独设计稳定契约, 不让其它 feature 直接依赖本实现模块.
