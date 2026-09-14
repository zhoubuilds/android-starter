# Foundation 开发文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-11 | whisper | 区分普通图标品牌色与启动器主题图标着色职责 |
| 2026-09-11 | whisper | 建立共享主题资源, 品牌生成和验证规则 |

本文补充 foundation 的维护约束. 通用规则见 [代码规范](../application/code-style.md) 和
[开发规范](../application/development.md), 模块边界见 [Foundation 设计文档](design.md).

## 1. 维护范围

| 内容 | 维护位置 |
| --- | --- |
| 品牌种子和生成参数 | `design/brand/theme-seed.properties` |
| 颜色生成工具 | `scripts/GenerateTheme.java` |
| 浅色语义色 | `foundation/src/main/res/values/foundation_colors.xml` |
| 深色语义色 | `foundation/src/main/res/values-night/foundation_colors.xml` |
| 共享主题 | `foundation/src/main/res/values/foundation_theme.xml` |
| 平台主题覆盖 | `foundation/src/main/res/values-v27/` 与 `values-v29/` |
| App 主题入口与应用身份 | app 模块 |

foundation 不读取 app 的构建配置, 不持有网络 Endpoint 或 feature 页面. 业务响应映射和状态处理按
[Network 开发文档](../network/development.md) 和 [Architecture 开发文档](../architecture/development.md)
维护, 主题调整不扩大到这些逻辑.

## 2. 颜色生成与资源规则

* 统一使用 `foundation_` 资源前缀并由 Gradle `resourcePrefix` 约束. style 资源同样遵守前缀校验,
  公开主题为 `Foundation.Theme`, 内部使用 `Foundation.Theme.Base` 和 `Foundation.Theme.Platform`.
* 品牌种子 `#006A86`, `SchemeTonalSpot`, 对比度级别 `0.0` 和 Material Components `1.14.0` 构成生成输入.
  使用对应官方 AAR 内的 Color Utilities, 不将本机 JAR 或缓存路径写入版本化配置.
* 修改品牌输入后重新生成完整的 49 个颜色角色. 浅色与深色的角色集合保持一致, fixed 角色在两种模式中
  取值相同. 不单独手调输出使其脱离生成规则.
* 颜色资源是工程源码的一部分. 设计输入和工具一起保存, 不把依赖 AAR, JAR, APK 或本机临时产物纳入版本控制.
  完整生成命令见 [Foundation 使用文档](usage.md#3-替换品牌).
* Views 只绑定依赖实际公开的主题属性. 没有对应属性的 tint, scrim, shadow 角色保留为资源, 不增加同名
  自定义 attr 来模拟 Material 实现.
* 不在 Application, Activity 或主题中接入壁纸动态色. 深色模式使用 `values-night`, 不通过替换 seed 实现.
* 不将主题语义色用于启动图标. 普通图标保持独立且固定的品牌素材色, monochrome 图层由 app 提供,
  其重新着色由启动器控制, 不改变 Foundation 的日夜配色或动态取色策略.

## 3. 样式维护

`Foundation.Theme` 保持 `Theme.Material3Expressive.DayNight.NoActionBar` 继承关系.
字体, 形状, 动效和组件状态优先使用上游默认值, 仅为当前应用的真实差异增加覆盖.

生成器在 49 个 Material 颜色角色之外派生 4 个反色文字与文本选择辅助色. 它们供基础文本 ColorStateList 使用,
不作为额外的 Material 标准颜色角色计数.

检查普通文本控件的默认文本色, 链接色, 提示色和禁用色是否使用本应用语义色. 某些 Material 默认
ColorStateList 直接引用默认色板, 不能只根据 `colorOnSurface` 已绑定就认定所有文本已接入品牌.
覆盖状态色时保留 `state_enabled` 和必要 alpha, 不把 ColorStateList 简化为丢失状态的常量.

布局和自定义 View 读取主题属性, 例如 `?attr/colorOnSurface`, `?attr/colorOutline` 和
`?attr/colorSurfaceContainer`. 页面引用公开 Material 字体和形状属性, 不反向引用 app 资源.

系统栏与 Insets 处理属于 Activity 的窗口接入. 主题提供匹配的背景与默认明暗设置, 不修改受保护的
ArchitectureActivity 生命周期或通用 API 来注入品牌.
`android:windowLightNavigationBar` 从 API 27 起可用, 覆盖放入 `values-v27`, 不能放在 `values-v26`.
`values-v29` 配置 `android:forceDarkAllowed="false"`, 避免自动深色处理覆盖已提供的日夜主题.

## 4. 保护范围

当前 foundation 源码没有单独声明的 `@aegis` 保护标记. 下层 Architecture, Kit 和其它模块的保护范围仍然
适用; 调用受保护 API 不代表可以修改它. 改动业务协议或公共能力前检查源码和对应模块文档.

## 5. 验证

主题变更至少验证 foundation 资源合并以及 app 的 Debug / Release 资源链接:

```bash
./gradlew :foundation:assembleDebug :app:assembleDebug :app:assembleRelease --no-configuration-cache
git diff --check
```

app 的 [`StarterThemeInstrumentedTest`](../../app/src/androidTest/java/com/whisper/starter/StarterThemeInstrumentedTest.kt)
覆盖合并后的日夜语义色对比度, 基础文字的禁用与反色状态, 以及 MainActivity 的固定品牌和明暗模式.
连接设备或模拟器后可执行:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.whisper.starter.StarterThemeInstrumentedTest \
  --no-configuration-cache
```

对生成输入和结果检查以下条件:

* 重新运行生成工具后没有意外差异; 49 个颜色角色在浅色和深色中齐全, 引用均可解析.
* Material 主题绑定指向当前生成色, 不残留默认或旧品牌色; fixed 角色跨明暗模式一致.
* 内容色与对应背景色的对比度符合使用场景. 普通文本以 4.5:1 为基线, 大字和必要非文本元素以 3:1 为基线,
  禁用状态按 Material 组件规则检查, 不把禁用状态作为普通文本直接比较.

通过设备或模拟器切换浅色和深色, 检查文本, 按钮, 输入框, 弹窗, 系统栏和自定义 View. 在支持动态取色的
系统更换壁纸, 确认应用语义色保持不变. 构建通过不表示这些视觉检查已经完成; 交付时明确实际覆盖的平台和
未完成的检查. 业务或状态逻辑变化时按对应模块文档扩大测试, 不为纯资源变更增加无关测试框架.
