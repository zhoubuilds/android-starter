# Foundation 设计文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-11 | whisper | 明确启动器主题图标独立于应用内固定配色 |
| 2026-09-11 | whisper | 明确应用基座边界和固定品牌设计系统 |

## 1. 定位

`foundation` 是当前应用的共享基座, 负责应用响应映射, Meta 建模, 领域转换, 业务状态处理和跨页面设计系统.
它实现 Architecture 提供的技术抽象, 可以包含应用约定, 但不读取 app 的 `BuildConfig`, flavor 或网络域名.

当前业务响应和数据管线见 [Network 设计文档](../network/design.md), 通用状态与 UI 契约见
[Architecture 设计文档](../architecture/design.md). 本次主题接入不改变这些契约.

## 2. 依赖与归属

| 内容 | 归属 |
| --- | --- |
| 应用共享配色, 主题和设计 token | foundation |
| 应用名称, 启动图标, Manifest 和最终主题选择 | app |
| 页面布局, 页面文案和专用资源 | app 或对应 feature |
| 通用技术能力, 状态和组件契约 | architecture, kit 等模块 |
| 品牌设计输入, 种子配置和可复现工具 | 工程根目录的 `design/brand/` 和 `scripts/` |

依赖方向为 `app / feature:* -> foundation -> architecture / kit / quill`.
通用模块不反向依赖 foundation 的颜色, 主题或业务模型. 当前不为这套共享资源额外建立独立设计系统模块.

## 3. 固定日夜主题

`Foundation.Theme` 继承 Material Components `1.14.0` 的
`Theme.Material3Expressive.DayNight.NoActionBar`. app 的 `Theme.Androidstarter` 继承共享主题,
未来 feature 可通过应用上下文使用同一套语义属性.

基础资源位于 `foundation/src/main/res/values/foundation_colors.xml` 和 `foundation_theme.xml`,
深色覆盖位于 `values-night/foundation_colors.xml`. library 资源使用 `foundation_` 前缀,
style 资源使用 `Foundation` 前缀, 内部主题为 `Foundation.Theme.Base` 和 `Foundation.Theme.Platform`.
主题随系统明暗模式切换, 不从壁纸动态取色.
app 提供的启动器 monochrome 图层可以由启动器重新着色; 该能力不改变 Foundation 的配色,
也不要求接入 `DynamicColors`.

品牌种子为电青 `#006A86`, 图标配色另外包含石墨 `#1B2025` 和冰银 `#EAF1F5`. 图标原色是品牌素材值,
主题色是按语义角色生成的界面值, 两者不要求逐项相同. 禁止只将 `colorPrimary` 替换为种子色而保留其它
角色的旧颜色.

固定配色使用官方 Material Color Utilities `SchemeTonalSpot`, 对比度级别为 `0.0`. 共保留 49 个颜色角色,
包含 primary, secondary, tertiary, 对应内容色和容器色, inverse, fixed, error, outline, surface 容器,
surface tint, scrim 和 shadow. 在浅色与深色模式中含义相同的 fixed 角色保持一致.

Views 主题绑定使用 Material Components 实际公开的属性. `surfaceTint`, `scrim` 和 `shadow` 等不对应
同名 Views 主题属性的角色作为颜色资源保留, 不伪造 `colorSurfaceTint`, `colorScrim` 或 `colorShadow`
属性. 组件的遮罩, 阴影和表面处理沿用 Material 样式.

## 4. 排版, 形状与状态

字体层级, 行高, 字重, 形状尺寸, 组件状态和 Expressive 动效继承 Material 默认值. 页面引用
`textAppearanceTitleLarge`, `textAppearanceBodyLarge`, `shapeAppearanceCornerMedium` 等语义属性,
按控件语义选择样式. 不复制整套 Material 组件样式或为尚未使用的场景增加自定义控件主题.

普通 TextView, Material 控件及其禁用状态需要保持与共享颜色角色一致. 文本 ColorStateList 的维护应覆盖
浅色和深色, 不把默认紫灰配色遗漏在固定品牌主题中. 自定义 View 使用主题属性消费颜色, 不依赖 app 资源.
4 个反色文字和文本选择辅助色由同一生成器派生, 不计入 49 个标准颜色角色.

主题本身不负责窗口 Insets. Activity 使用 AndroidX 的 edge-to-edge 接入并按实际布局处理系统栏和键盘
Insets, 系统栏图标明暗与背景相匹配.
平台资源在 `values-v27` 中配置 `windowLightNavigationBar`, 在 `values-v29` 中关闭平台 Force Dark,
使应用自行提供的深色配色保持有效. API 24 至 26 的主题导航栏保留深色背景以匹配平台浅色图标.

## 5. 来源与替换

种子和算法参数保存在 [`design/brand/theme-seed.properties`](../../design/brand/theme-seed.properties),
生成入口为 [`scripts/GenerateTheme.java`](../../scripts/GenerateTheme.java). 工具使用 Material `1.14.0`
官方 AAR 中的 Color Utilities 实现, 将结果写入版本化的 Android 颜色资源, 运行时不再生成颜色.

替换品牌时同步修改输入, 重新生成日夜配色, 检查主题绑定和对比度. 图标设计输入及各平台资源由 app 维护,
见 [App 开发文档](../app/development.md). `feature:function` 和 `feature:about` 仍是后续规划, 当前未实现.
