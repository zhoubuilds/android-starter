# Foundation 设计文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-17 | whisper | 明确共享视觉样式与 Kit 实时 Insets 工具的分层 |
| 2026-09-17 | whisper | 统一设置与演示页面的连续分段按钮基础样式 |
| 2026-09-15 | whisper | 增加原生 SemiBold 并建立顶层页面标题层级 |
| 2026-09-15 | whisper | 保留两套上游字体的原始轮廓和横向度量 |
| 2026-09-15 | whisper | 保持 Noto CJK 原始字形比例并使用居中字框 |
| 2026-09-14 | whisper | 使用 Starter Mono 建立中英文等宽字体排版 |
| 2026-09-14 | whisper | 同步 Project 模块与应用设置页面归属 |
| 2026-09-14 | whisper | 同步首页功能模块的实现状态 |
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
主题默认随系统明暗模式切换, app 也可通过 AppCompat 固定为浅色或深色; 三种模式均不从壁纸动态取色.
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

全局字体使用 Starter Mono 1.003. 它以 JetBrains Mono 2.304 Regular / SemiBold 为基础, 补充
Noto Sans Mono CJK SC 2.004 的对应 CJK 字形, 并使用独立名称标识衍生字体. Foundation 打包原生 400 和 600 TTF,
覆盖 Material 3 的 Display、Headline、Title、Body、Label 及对应 Emphasized 排版槽位. 顶层页面标题固定使用
原生 600, 其它字体选择和斜体继续由 Android 按排版语义解析. 字号、行高和组件语义继续继承 Material 3,
等宽字体字距统一为 `0`. 页面继续引用
`textAppearanceTitleLarge`、`textAppearanceBodyLarge`、`shapeAppearanceCornerMedium` 等语义属性,
不直接引用字体文件.

JetBrains Mono 提供拉丁字符、数字、符号和编程连字; Noto 提供内置 CJK 字形, 不再依赖厂商系统字体回退.
两套字体保留原始轮廓和横向度量: 拉丁字符 advance width 为 600 units, CJK 字符为 1000 units.
应用 UI 优先保证自然字形与间距, 不承诺终端或代码编辑器所需的中英文 `2:1` 字符网格. 两个上游字体和 Starter Mono
均依据 SIL OFL 1.1 分发, 来源、修改、哈希、许可和署名保存在 `third_party/starter-mono/`.

普通 TextView, Material 控件及其禁用状态需要保持与共享颜色角色一致. 文本 ColorStateList 的维护应覆盖
浅色和深色, 不把默认紫灰配色遗漏在固定品牌主题中. 自定义 View 使用主题属性消费颜色, 不依赖 app 资源.
4 个反色文字和文本选择辅助色由同一生成器派生, 不计入 49 个标准颜色角色.

跨页面连续分段选择统一使用 `Foundation.Widget.SegmentedButton` 与
`Foundation.Widget.SegmentedButtonGroup`. 两者继承经典 Material 3 的 OutlinedButton 和 ToggleGroup,
由组控制首尾外侧圆角与内部直角, 选择变化只影响语义颜色, 不使用 Expressive 的选中态形变.
具体按钮尺寸、页面间距和文本自动缩放仍由消费页面按内容维护.

主题本身不负责窗口 Insets. Activity 使用 AndroidX 的 edge-to-edge 接入, 通过 Kit `applyWindowInsetsPadding()`
复用实时 padding 处理, 按实际布局选择底部职责与键盘避让. Foundation 维护共享视觉样式, 不重复定义通用 Insets 工具;
系统栏图标明暗与背景相匹配.
平台资源在 `values-v27` 中配置 `windowLightNavigationBar`, 在 `values-v29` 中关闭平台 Force Dark,
使应用自行提供的深色配色保持有效. API 24 至 26 的主题导航栏保留深色背景以匹配平台浅色图标.

## 5. 来源与替换

种子和算法参数保存在 [`design/brand/theme-seed.properties`](../../design/brand/theme-seed.properties),
生成入口为 [`scripts/GenerateTheme.java`](../../scripts/GenerateTheme.java). 工具使用 Material `1.14.0`
官方 AAR 中的 Color Utilities 实现, 将结果写入版本化的 Android 颜色资源, 运行时不再生成颜色.

替换品牌时同步修改输入, 重新生成日夜配色, 检查主题绑定和对比度. 图标设计输入及各平台资源由 app 维护,
见 [App 开发文档](../app/development.md). `feature:function`、`feature:project` 和 app 设置页面均通过
Foundation 共享主题消费语义资源.
