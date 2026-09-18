# Function 使用文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-09-17 | whisper | 说明详情统一避让系统栏、刘海和输入键盘 |
| 2026-09-15 | whisper | 说明列表分割线与 LayoutManager 切换入口 |
| 2026-09-14 | whisper | 说明组件详情使用模块内部 Activity |
| 2026-09-14 | whisper | 说明 RecyclerView 目录与详情导航 |
| 2026-09-14 | whisper | 说明 Function 页面入口与当前示例 |

## 1. 页面入口

安装并启动 Starter 后, 首页默认选中“演示”Tab. App 直接创建 `FunctionFragment`, 当前没有 Aster 路由或独立
`*-api` 契约.

## 2. 当前示例

RecyclerView 目录提供四个入口, 点击后进入对应详情:

* 整数范围选择: 拖动起点或终点, 页面同步显示当前范围.
* 分格输入: 输入最多 6 位数字, 页面同步显示已输入长度.
* 线条样式: 对比实线、虚线和点划线的主题色绘制.
* 列表分割线: 切换列表、两列网格和两列瀑布流, 对比横向/纵向布局, 开关分割线.

普通控件详情使用未导出的 `FunctionDemoActivity`, 分割线详情使用未导出的 `RecyclerViewDemoActivity`.
工具栏和系统返回键都回到演示目录. 切换底部 Tab 时目录 Fragment 仍由首页保留.
这些 Activity 只供模块内部显式 Intent 使用, 当前不属于公开路由.

详情通过 Kit 的实时 Insets 扩展避让系统栏与刘海; 分格输入页弹出键盘后保留可滚动的输入区域.

分割线默认使用纵向列表和 1dp 实线. 切换 LayoutManager 时保留可见列表项, Activity 重建后恢复布局模式、
方向、开关与滚动位置. 规整布局使用 `RegularItemDividerDecoration`, 瀑布流使用
`StaggeredItemDividerDecoration`; 具体接入约束见 [Kit 使用文档](../kit/usage.md#8-recyclerview-decoration).

## 3. 接入方式

最终 app 通过实现依赖组合页面:

```kotlin
dependencies {
    implementation(project(":feature:function"))
}
```

其它 feature 不应直接依赖此实现模块. 出现跨模块路由或 Capability 需求后, 先建立对应 API 模块并迁移调用方依赖.
