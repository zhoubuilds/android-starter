# Function 设计文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-17 | whisper | 复用 Foundation 连续分段按钮设计样式 |
| 2026-09-15 | whisper | 增加 RecyclerView 分割线与布局切换演示 |
| 2026-09-14 | whisper | 将组件详情迁移为模块内部独立 Activity |
| 2026-09-14 | whisper | 建立 Function 示例模块与 Fragment 组合边界 |

## 1. 定位

`feature:function` 是 Starter 的功能演示目录, 用于承载通用 View、交互组件和后续技术能力的可操作示例.
它是应用业务层模块, 可以组合 Foundation 与 Kit 的公开能力, 但不把页面文案、样式或演示状态下沉到公共模块.

当前页面演示 `KitRangeBar`、`KitCodeInputEditText`、`KitLineView` 以及 RecyclerView 分割线装饰器.
分割线演示组合 Linear、Grid 和 Staggered 三种 LayoutManager, 支持横向/纵向和分割线开关. 后续内容增加时优先按稳定类别在模块内部
拆分页面和 package, 不把所有示例继续堆入首页, 也不因示例数量增加就机械拆分 Gradle 模块.

## 2. 组合边界

`FunctionFragment` 由 app 组合根直接创建, 当前只作为应用内部顶层 Tab 的实现入口. `feature:function` 不依赖
`feature:project`, 不引用 app 类型或资源, 也不声明跨模块路由和 Capability. 目录项通过显式 Intent 打开模块内部的
`FunctionDemoActivity` 或 `RecyclerViewDemoActivity`, 详情不进入首页 Activity 的 Fragment 返回栈.

当其它 feature 需要导航到 Function 页面或调用 Function 能力时, 应先新增对应 `*-api` 模块, 在其中声明稳定路由、
参数、Capability 接口和名称. 实现模块随后接入 Aster. 直接字符串可以完成跳转, 但不能代替对外契约.

## 3. 演进原则

Starter 是底层能力的演示应用, 可以为了展示真实模块化接入采用比普通小型应用更完整的设计. 这种设计仍需对应可操作的
演示场景、测试或维护边界, 不创建没有消费者的 API、路由或抽象.

新增演示时保持以下边界:

* 页面只展示公共能力的正常用法, 不复制 Kit 内部实现.
* 控件状态和演示数据由 Function 自己维护.
* 需要独立依赖、稳定公开契约或显著隔离收益时, 再评估新的 feature 模块.
* 真实网络与仓库信息优先归 Project; 通用网络机制演示可在需求明确后单独评估.

分割线详情使用独立 Activity 和 `recyclerview` package, 不继续扩展普通控件详情的布局与交互分支.
Linear/Grid 使用 `RegularItemDividerDecoration`, Staggered 使用 `StaggeredItemDividerDecoration`.
列表项使用相同静态数据, 瀑布流仅改变主轴占用尺寸; 分割线使用主题 `colorOutlineVariant` 和 1dp 尺寸,
不在 item 背景中伪造分割线. 模式按钮复用 Foundation 的共享分段样式, 保持连续轮廓, 选中态只改变颜色;
Function 只补充适应演示选项文案的内边距和文本缩放配置.
