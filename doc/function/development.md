# Function 开发文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-17 | whisper | 统一详情 Insets 并明确目录原生点击与旁路手势的边界 |
| 2026-09-17 | whisper | 改用 Foundation 分段基础样式并保留页面尺寸配置 |
| 2026-09-17 | whisper | 使用 Kit 间距装饰器维护目录项间距与末项留白 |
| 2026-09-15 | whisper | 维护分割线装饰器匹配、布局切换与状态恢复 |
| 2026-09-14 | whisper | 使用独立 Activity 承载组件详情 |
| 2026-09-14 | whisper | 拆分 RecyclerView 目录与组件详情页 |
| 2026-09-14 | whisper | 建立 Function 模块维护规则 |

本文补充 `feature:function` 的维护约束. 通用规则见 [代码规范](../application/code-style.md) 和
[开发规范](../application/development.md), 设计边界见 [Function 设计文档](design.md).

## 1. 模块结构

```text
feature/function/
|- build.gradle.kts
`- src/main/
   |- java/com/whisper/feature/function/
   |  |- FunctionFragment.kt
   |  |- FunctionCatalogAdapter.kt
   |  |- FunctionDemoActivity.kt
   |  `- recyclerview/
   |     |- RecyclerViewDemoActivity.kt
   |     |- RecyclerLayoutMode.kt
   |     `- RecyclerDividerDemoAdapter.kt
   `- res/
      |- drawable/
      |- layout/
      |  |- function_catalog_fragment.xml
      |  |- function_demo_activity.xml
      |  |- function_recycler_activity.xml
      |  `- function_recycler_item.xml
      |- values/function_strings.xml
      `- values-en/function_strings.xml
```

资源统一使用 `function_` 前缀. Fragment 使用 Kit 的 ViewBinding 委托, Binding 只在 `onViewCreated()` 至
`onDestroyView()` 之间访问. 控件文案放入 string 资源, 颜色使用 Foundation 主题语义属性.

## 2. 依赖与公开面

模块直接依赖 Foundation 和 Material, 通过 Foundation 获得其公开的 Architecture、Kit 与 Quill 能力.
不要依赖 app 或 Project.

`FunctionFragment` 当前必须为 public, 供 app 组合根创建. 详情 Activity 需要由 Android Framework 实例化,
但在 Manifest 中保持 `exported=false`. 这些实现入口不表示稳定的跨模块导航契约. 当前模块不应用 Aster 或 KSP;
后续需要公开路由或 Capability 时, 在对应 `*-api` 模块建立契约后再接入.

## 3. 扩展示例

演示首页固定为 RecyclerView 目录, 目录项通过模块内部显式 Intent 打开详情 Activity. 新增项目时扩展 `FunctionDemo`
和 adapter 绑定; 当详情差异显著增大时再拆分 Activity 或内部 Fragment, 不把复杂逻辑继续堆入单个 `when`.
模块内部导航不需要经过 Aster, 但对外开放时必须先建立 `*-api` 路由契约.

目录项之间的 8dp 间距和末项后的 8dp 留白由 Kit `RegularItemSpaceDecoration` 统一提供, 不在 item 根 View
设置列表间距 margin. RecyclerView 自身的页面 padding 保持独立, 不重复计入装饰器.

目录通过 item 原生 `setOnClickListener` 导航, 保留键盘、无障碍和 `performClick()` 激活能力. Kit 的
`addOnItemChildClickListener` 是 MotionEvent 旁路观察工具, 适合独立手势命中演示, 不能作为这些入口的替代,
也不应与原生监听同时执行同一导航动作.

详情 Activity 统一使用 Kit `applyWindowInsetsPadding()` 避让系统栏与刘海. 普通控件详情额外加入 IME,
Manifest 声明 `adjustResize`, 继续由现有 NestedScrollView 保证键盘弹出后输入区域可达. 页面不复制 Insets listener,
也不在 Foundation 增加只转发调用的包装.

每个交互示例至少覆盖默认状态、用户操作后的状态更新和配置变化恢复. 涉及 Kit 复杂手势、无障碍或低版本绘制时,
复用 Kit 已有测试结论, 并为 Function 页面补充必要的集成验证, 不在演示代码中重新实现算法.

### 3.1 RecyclerView 分割线

* 分割线尺寸由页面转换为非负 px, 颜色从当前主题读取. 不修改 Kit 装饰器公开契约或内部算法.
* Linear/Grid 与 Staggered 使用各自匹配的 DividerDecoration, 每次切换先移除旧实例, 不叠加内部间距.
* 更换 LayoutManager 时重建静态 Adapter, 避免沿用旧 item 尺寸和 LayoutParams, 并保留首个可见位置.
  配置变化后立即失效 decoration inset; 当前演示不执行异步列表更新.
* Adapter 实现 `StaggeredFullSpanProvider`, 并将同一查询结果用于绑定 `isFullSpan`. 当前全部 item 为普通 span.
* Bundle 保存布局模式、方向与分割线开关, RecyclerView 自身保存滚动布局状态. 首次装配不主动调用
  `scrollToPosition(0)`, 避免覆盖恢复的 LayoutManager 状态; 仅运行时切换保留可见位置.
  三种布局均使用 24 项确定性数据.
* 分段组直接使用 `Foundation.Widget.SegmentedButtonGroup`; `Function.Widget.SegmentedButton` 继承
  `Foundation.Widget.SegmentedButton`, 只补充 8dp 水平内边距、单行和 12-14sp 自动缩放, 不复制形状配置.
  控件保持至少 48dp 触控尺寸, 中英文文本过长时按可用空间自动缩小到 12sp.

## 4. 验证

```bash
./gradlew :feature:function:assembleDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-configuration-cache
```

仅在用户指定模拟器上检查范围拖动、分格输入、线条绘制、列表滚动、浅深色模式和 Tab 往返状态.
app 的 `RecyclerViewDemoInstrumentedTest` 覆盖目录返回、六种布局/方向组合、重复开关不叠加装饰器,
以及重建后的选中项与滚动位置恢复. 不使用会枚举全部连接设备的 connected 测试任务.
