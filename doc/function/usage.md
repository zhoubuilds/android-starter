# Function 使用文档

## 修订记录

| 修订时间(CST) | 修订人 | 修订说明 |
| --- | --- | --- |
| 2026-10-22 | whisper | 增加年月日滚轮渐变 Bottom Sheet 示例说明 |
| 2026-09-17 | whisper | 说明详情统一避让系统栏、刘海和输入键盘 |
| 2026-09-15 | whisper | 说明列表分割线与 LayoutManager 切换入口 |
| 2026-09-14 | whisper | 说明组件详情使用模块内部 Activity |
| 2026-09-14 | whisper | 说明 RecyclerView 目录与详情导航 |
| 2026-09-14 | whisper | 说明 Function 页面入口与当前示例 |

## 1. 页面入口

安装并启动 Starter 后, 首页默认选中“演示”Tab. App 直接创建 `FunctionFragment`, 当前没有 Aster 路由或独立
`*-api` 契约.

## 2. 当前示例

RecyclerView 目录提供五个入口, 点击后进入对应详情或弹出交互层:

* 整数范围选择: 拖动起点或终点, 页面同步显示当前范围.
* 分格输入: 输入最多 6 位数字, 页面同步显示已输入长度.
* 线条样式: 对比实线、虚线和点划线的主题色绘制.
* 滚轮日期选择: 在 Bottom Sheet 中联动选择年、月、日, 确认后在目录页显示结果.
* 列表分割线: 切换列表、两列网格和两列瀑布流, 对比横向/纵向布局, 开关分割线.

普通控件详情使用未导出的 `FunctionDemoActivity`, 日期选择使用模块内部 `DatePickerBottomSheetDialog`,
分割线详情使用未导出的 `RecyclerViewDemoActivity`.
工具栏和系统返回键都回到演示目录. 切换底部 Tab 时目录 Fragment 仍由首页保留.
这些 Activity 只供模块内部显式 Intent 使用, 当前不属于公开路由.

详情通过 Kit 的实时 Insets 扩展避让系统栏与刘海; 分格输入页弹出键盘后保留可滚动的输入区域.

分割线默认使用纵向列表和 1dp 实线. 切换 LayoutManager 时保留可见列表项, Activity 重建后恢复布局模式、
方向、开关与滚动位置. 规整布局使用 `RegularItemDividerDecoration`, 瀑布流使用
`StaggeredItemDividerDecoration`; 具体接入约束见 [Kit 使用文档](../kit/usage.md#8-recyclerview-decoration).

日期选择用例显式通过 Builder 传入可选范围和初始日期. 设备当前日期不早于 1970-01-01 时, 范围为
1970-01-01 到当前日期增加一个自然年; 设备当前日期更早时, 范围反转为当前日期到 1970-01-01. 初始值始终为
设备当前日期. 三列使用 `WheelLayoutManager` 与 `WheelSnapHelper`, 数值由 `IntSequenceAdapter` 即时计算.
年月变化会实时修正下游范围, 包括起止边界、大小月和闰年; 只有点击“确定”才提交并显示日期.
滚轮以 42dp 行高显示五行. 中心行使用主题主色, 色带外侧从正文色逐渐过渡到上下边缘的次要文本色;
文字滑过色带边界时按像素呈现两侧颜色. Bottom Sheet 不显示拖拽横条且默认禁止手势下拉关闭;
返回键和外部点击关闭默认启用, 业务子类也可以通过 Foundation 基类分别关闭.

## 3. 接入方式

最终 app 通过实现依赖组合页面:

```kotlin
dependencies {
    implementation(project(":feature:function"))
}
```

其它 feature 不应直接依赖此实现模块. 出现跨模块路由或 Capability 需求后, 先建立对应 API 模块并迁移调用方依赖.
