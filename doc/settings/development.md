# Settings 开发文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-13 | whisper | 接入 Project API 仓库 Capability |
| 2026-10-13 | whisper | 建立 Settings 模块结构、公开面和验证规则 |

本文补充 `feature:settings` 的维护约束. 通用规则见 [代码规范](../application/code-style.md) 和
[开发规范](../application/development.md), 设计边界见 [Settings 设计文档](design.md).

## 1. 模块结构

```text
feature/settings/
|- build.gradle.kts
`- src/main/
   |- java/com/whisper/feature/settings/
   |  |- SettingsFragment.kt
   |  |- AboutStarterActivity.kt
   |  `- AppAppearancePreferences.kt
   `- res/
      |- drawable/
      |- layout/
      |  |- settings_fragment.xml
      |  `- settings_about_activity.xml
      |- values/settings_strings.xml
      `- values-en/settings_strings.xml
```

资源统一使用 `settings_` 前缀. Fragment 使用 Kit 的 ViewBinding 委托, Binding 只在 View 生命周期内访问.
About 页面使用 Activity ViewBinding, 并通过 Kit Insets 扩展处理系统栏与刘海.

## 2. 依赖与公开面

模块直接依赖 Project API、Aster runtime、Foundation、ConstraintLayout 和 Material, 通过 Foundation 获得公开的
Kit 与 AppCompat 能力. 不依赖 app 或任何 feature 实现模块.

`SettingsFragment` 必须为 public, 供 app 组合根创建. `AppAppearancePreferences.initialize()` 必须为 public, 供
`StarterApplication` 在首个 Activity 前恢复颜色模式. 偏好查询、写入和 `AppThemeMode` 保持 internal.
`AboutStarterActivity` 需要由 Android Framework 实例化, 但在模块 Manifest 中保持 `exported=false`.
这些入口不是稳定的跨模块业务契约.

## 3. 外观与语言

颜色模式先写入模块持有的 SharedPreferences, 再调用 AppCompat 切换模式, 确保 Activity 重建读取到最新选择.
初始化只恢复颜色模式, 不持有 Application 或 Activity. app 必须在 `StarterApplication.onCreate()` 的其它页面创建前调用入口.

应用语言继续由 `AppCompatDelegate.setApplicationLocales()` 管理. 空语言列表表示跟随系统, 不能根据当前资源语言反推用户选择.
支持语言及系统 Locale 声明由 app 的 `locales_config.xml` 和 Manifest metadata 决定.

## 4. 页面与资源

设置页使用 Foundation 的共享主题和连续分段按钮样式. 页面专用文案、布局与图标全部留在本模块, 不把资源放回 app.
About 页面从当前宿主查询应用名称、图标和版本, 避免对 app 包名、资源或构建配置建立反向依赖.
仓库入口使用 `ProjectRepositoryCapability` 按名称解析; 未注册实现时隐藏该信息项, 已注册时显示完整地址并交给
系统浏览器打开. Settings 不读取 Project 的 DTO、数据库、Repository、ViewModel 或实现类.

模块内部 About 跳转使用显式 Intent. 如果其它 feature 需要进入 Settings 或 About, 应先建立导航契约,
不能直接依赖 `feature:settings` 的 Activity 类型.

## 5. 保护范围

当前 Settings 源码没有单独声明的 `@aegis` 保护标记. Aster、Foundation、Kit 和 AppCompat 的既有契约不因本模块调用而改变.

## 6. 验证

```bash
./gradlew :feature:settings:testDebugUnitTest :feature:settings:lintDebug \
  :feature:settings:bundleDebugAar :app:compileDebugKotlin :app:assembleDebugAndroidTest \
  --no-configuration-cache
```

单元测试覆盖颜色模式持久化值解析. App instrumented test 覆盖设置 Tab 重建、About 往返和共享分段按钮样式.
同时验证 Aster 能力解析和 About 仓库地址展示. 涉及实际颜色模式或语言切换时, 还应在指定设备或模拟器验证
Activity 重建、进程重启以及中英文资源切换.
