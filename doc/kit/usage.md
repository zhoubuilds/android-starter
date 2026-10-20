# Kit 使用文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                          |
|-----------------|---------|-----------------------------------|
| 2026-10-20      | whisper | 明确滚轮实时中心回调语义          |
| 2026-10-20      | whisper | 说明滚轮空数据的中心回调语义      |
| 2026-10-19      | whisper | 说明滚轮滚动指标及循环滚动条语义  |
| 2026-10-19      | whisper | 说明滚轮模式及程序滚动目标边界    |
| 2026-10-19      | whisper | 说明滚轮状态恢复、锚点校验和异步数据配置 |
| 2026-10-19      | whisper | 说明滚轮普通动画与预测动画边界    |
| 2026-10-19      | whisper | 说明滚轮 padding 与 item 外边界语义 |
| 2026-10-15      | whisper | 补充滚轮零高度 item 的循环语义    |
| 2026-10-14      | whisper | 增加 RecyclerView 滚轮布局及条件循环用法 |
| 2026-09-17      | whisper | 说明实时 Insets padding 与页面处理边的选择 |
| 2026-09-14      | whisper | 说明 Kit 无需全局 Application 初始化 |
| 2026-09-11      | whisper | 补充刷新内容数量、动画时长和业务回调时机说明 |
| 2026-09-11      | whisper | 补充刷新组件更新文案后的布局快照去重语义 |
| 2026-09-11      | whisper | 补充刷新组件 margin、尺寸更新及边界回调重入语义 |
| 2026-09-11      | whisper | 补充 GONE 组件的交互限制及阻尼赋值异常说明 |
| 2026-09-11      | whisper | 补充刷新加载运行时阈值、组件改尺寸及惯性中断语义 |
| 2026-09-11      | whisper | 增加统一滚动边界、无障碍动作及 ViewModel 显示同步用法 |
| 2026-09-11      | whisper | 补充原生 ScrollView 嵌套滚动接入与惯性能力边界 |
| 2026-09-11      | whisper | 补充刷新加载取消、禁用、组件同步及布局用法 |
| 2026-09-11      | whisper | 更新范围选择和刷新加载包路径及升级迁移说明 |
| 2026-09-11      | whisper | 补充范围选择控件的默认焦点及显式配置语义 |
| 2026-09-11      | whisper | 增加起点下限配置及不可选前缀接入说明 |
| 2026-09-11      | whisper | 明确 Drawable 原实例共享、独立动画与同值回写语义 |
| 2026-09-10      | whisper | 补充单触点取消与组合 Drawable 尺寸保留说明 |
| 2026-09-10      | whisper | 补充范围最终抬起值、按键拦截与 Drawable 尺寸保留说明 |
| 2026-09-10      | whisper | 更新范围与交互监听并补充无障碍、键盘、恢复和 RTL 用法 |
| 2026-09-09      | whisper | 补充范围自动对齐及滑块清空、继承和共享用法 |
| 2026-09-09      | whisper | 增加整数范围选择与两端独立 Drawable 配置用法 |
| 2026-09-09      | whisper | 补充标准分割线与 KitLineView 的选型建议 |
| 2026-09-09      | whisper | 补充线条低版本硬件加速兼容说明 |
| 2026-09-09      | whisper | 补充线条默认实线、状态色与运行时配置用法 |
| 2026-09-09      | whisper | 补充线条横竖示例、尺寸语义与方向迁移 |
| 2026-09-09      | whisper | 明确取消拖拽与菜单回调边界 |
| 2026-09-09      | whisper | 补充短码触摸焦点、长按粘贴与图标背景用法 |
| 2026-09-08      | whisper | 明确短码字符约束与 RTL 无障碍用法 |
| 2026-09-08      | whisper | 分格输入长度配置迁移到原生 filters |
| 2026-09-08      | whisper | 补充分格自身宽度上限的间距规则 |
| 2026-09-08      | whisper | 明确分格输入长度规则与编辑使用边界 |
| 2026-09-08      | whisper | 更新 widget 用法并说明旧控件迁移 |
| 2026-09-05      | whisper | 补充完整窗口边界与方向用法        |
| 2026-09-05      | whisper | 补充客户端信息使用边界            |
| 2026-09-05      | whisper | 补充匿名设备标识使用边界          |
| 2026-09-04      | whisper | 明确点击与长按的退化语义和使用边界 |
| 2026-09-04      | whisper | 收敛 RecyclerView 手势 API 与兼容边界 |
| 2026-09-04      | whisper | 修正 Divider 高级构造器使用条件 |
| 2026-09-04      | whisper | 补充 Divider 构造与 Staggered 降级说明 |
| 2026-09-03      | whisper | 补全 Divider 非负参数契约       |
| 2026-09-03      | whisper | 明确 Decoration 单轴所有权与透明间距 |
| 2026-09-03      | whisper | 区分异步列表与 span 缓存失效时序 |
| 2026-09-03      | whisper | 明确 Staggered 主轴零尺寸限制 |
| 2026-09-03      | whisper | 明确 Decoration 失效调用时序 |
| 2026-09-03      | whisper | 区分 Regular 与 Staggered Decoration |
| 2026-09-03      | whisper | 增加 Staggered 分割线用法        |
| 2026-09-03      | whisper | 明确 Decoration 预测布局退化语义 |
| 2026-09-03      | whisper | 明确 Divider margin 与动画取舍    |
| 2026-09-02      | whisper | 明确 Divider 参数和绘制归属       |
| 2026-09-02      | whisper | 补充 Staggered 列表间距用法       |
| 2026-09-02      | whisper | 明确 RecyclerView 列表装饰用法    |
| 2026-09-02      | whisper | 完善 Context 域尺寸换算用法       |
| 2026-09-01      | whisper | 明确 ViewBinding 主线程访问方式   |
| 2026-09-01      | whisper | 补充 ViewBinding 委托用法         |
| 2026-09-01      | whisper | 明确重复同类 Span 的收敛规则      |
| 2026-09-01      | whisper | 明确绝对与相对字号组合规则        |
| 2026-09-01      | whisper | 明确点击与下划线的设置顺序        |
| 2026-09-01      | whisper | 明确重复点击行为的覆盖规则        |
| 2026-09-01      | whisper | 明确字体与字形组合规则            |
| 2026-09-01      | whisper | 统一通用扩展包路径                |
| 2026-08-27      | whisper | 迁入通用 ViewBinding 扩展         |
| 2026-08-17      | 张梁    | 补充通用分享底部面板用法          |
| 2026-08-14      | whisper | 明确分格输入样式显式引用方式      |
| 2026-08-14      | whisper | 补充分格输入主题样式入口          |
| 2026-08-14      | whisper | 补充分格文本输入控件用法          |
| 2026-08-13      | whisper | 补充 CharSequence 富文本扩展用法  |
| 2026-07-31      | whisper | 修正刷新加载触发距离示例单位      |
| 2026-07-31      | whisper | 补充刷新加载动态头尾恢复语义      |
| 2026-07-31      | whisper | 补充刷新加载内容滚动边界          |
| 2026-07-31      | whisper | 调整刷新加载容器包路径            |
| 2026-07-30      | whisper | 补充滚动到底自动加载能力          |
| 2026-07-30      | whisper | 调整刷新加载触发距离默认语义      |
| 2026-07-30      | whisper | 调整刷新加载指示器为跟随滚动语义  |
| 2026-07-30      | whisper | 补充刷新加载容器用法              |
| 2026-07-30      | whisper | 补充 Decoration 绘制边界入口      |
| 2026-07-30      | whisper | 补充 RecyclerView decoration 用法 |
| 2026-07-30      | whisper | 补充 ViewBinding ViewHolder 用法  |
| 2026-07-30      | whisper | 新增 Kit 模块使用说明             |

本文面向使用 `kit` 能力的开发者, 说明通用工具的接入方式和使用边界。维护实现请阅读 [开发文档](development.md)。

## 1. 使用边界

`kit` 只提供业务无关、应用语义无关的 Android 通用工具。调用方不应要求 `kit` 理解业务状态、应用资源、统一消息、图片加载约定或领域模型。
Kit 不持有全局 Application, 宿主无需在 `Application.onCreate()` 中初始化 Kit. 需要 Context 的 API 会通过参数或当前
Android 组件作用域显式获取.

业务模块通常通过 `foundation` 间接获得 `kit` 的公开工具:

```kotlin
dependencies {
    implementation(project(":foundation"))
}
```

仅在不需要 `foundation` 的模块中, 才直接依赖:

```kotlin
dependencies {
    implementation(project(":kit"))
}
```

## 2. 尺寸扩展

`Number.dp` 和 `Number.sp` 分别使用当前 Context 的显示密度和字体缩放配置转换为 px `Float`. 两个属性只能在存在隐式 Context
的作用域内使用, 不读取全局 Application:

```kotlin
val spacingPx: Float = with(context) { 16.dp }
val textSizePx: Float = with(context) { 14.sp }
```

当前代码位置没有隐式 Context 接收者时, 可以通过 `with(context)` 建立 Context 作用域:

```kotlin
val spacingPx: Float = with(requireContext()) { 16.dp }
```

Fragment、View 和 Dialog 也提供直接的组件域适配. 在它们的成员作用域内可以直接访问:

```kotlin
class ExampleFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spacingPx: Float = 16.dp
    }
}

class ExampleView(context: Context) : View(context) {
    val insetPx: Float
        get() = 8.dp
}

class ExampleDialog(context: Context) : Dialog(context) {
    val textSizePx: Float
        get() = 14.sp
}
```

Fragment 适配依赖 `requireContext()`, 因此只能在 Fragment 已附加 Context 时访问.
多个不同类型的组件接收者嵌套时, 使用函数形式显式传入目标 Context, 避免同名组件适配产生歧义:

```kotlin
val spacingPx: Float = 16.dp(view.context)
val textSizePx: Float = 14.sp(dialog.context)
```

返回值保留亚像素精度. 需要传给只接收整数像素的 API 时, 调用方应根据布局或文本语义显式选择舍入方式.
项目使用 Kotlin 2.4.0 及以上版本时无需添加 context parameter 实验性编译参数.

## 3. ViewBinding 扩展

Activity、Dialog 和 Fragment 的 ViewBinding 委托函数为 `com.whisper.kit.extension.viewBinding`. 委托不提供线程同步,
创建委托和访问 Binding 必须发生在主线程; 委托属性应使用 `@get:MainThread` 让 Android Lint 检查访问线程.

Activity 使用生成类的 `inflate()` 方法引用, 并显式设置 content view:

```kotlin
class MainActivity : Activity() {
    @get:MainThread
    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }
}
```

Activity Binding 在首次访问后按 Activity 对象生命周期缓存, 委托不会隐式调用 `setContentView()` 或在 `onDestroy()` 中清理.

已经通过构造布局或 `onCreateView()` 创建 View 的 Fragment 使用生成类的 `bind()` 方法引用. Binding 只能在
`onViewCreated()` 至 `onDestroyView()` 之间访问, View 销毁后由委托自动清空:

```kotlin
class ProfileFragment : Fragment(R.layout.fragment_profile) {
    @get:MainThread
    private val binding by viewBinding(FragmentProfileBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.title.text = "Profile"
    }
}
```

普通 Dialog 同样使用 `inflate()`, 并显式设置 content view:

```kotlin
class ProfileDialog(context: Context) : Dialog(context) {
    @get:MainThread
    private val binding by viewBinding(DialogProfileBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }
}
```

Dialog Binding 在首次访问后按 Dialog 对象生命周期缓存, `dismiss()` 不会清理 Binding; 同一个 Dialog 对象再次展示时继续使用
同一 Binding. 委托不会隐式调用 `setContentView()`.

通过 `onCreateView()` 提供内容的 DialogFragment 可以使用 Fragment 委托. 仅实现 `onCreateDialog()` 且没有 Fragment View
的 DialogFragment 不可使用该委托, 应在 `onCreateDialog()` 内局部调用生成类的 `inflate()`.

## 4. 基础控件与迁移

基础控件位于 `com.whisper.kit.widget` 及其职责子包. 根包提供线条绘制和分格输入,
`com.whisper.kit.widget.rangeselection` 提供范围选择, `com.whisper.kit.widget.refreshloadlayout` 提供刷新加载交互.
调用方负责配置样式, 组合界面内容并处理业务状态.

从旧版本升级时, 同步更新 Kotlin / Java import, XML 控件标签和使用完整类名的反射或混淆配置:

| 旧类型路径 | 当前类型路径 |
|------------|--------------|
| `com.whisper.kit.view.input.KitCodeInputEditText` | `com.whisper.kit.widget.KitCodeInputEditText` |
| `com.whisper.kit.view.refresh.KitRefreshLoadLayout` | `com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadLayout` |
| `com.whisper.kit.view.refresh.KitRefreshLoadComponent` | `com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadComponent` |
| `com.whisper.kit.widget.KitRangeBar` | `com.whisper.kit.widget.rangeselection.KitRangeBar` |
| `com.whisper.kit.widget.KitRefreshLoadLayout` | `com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadLayout` |
| `com.whisper.kit.widget.KitRefreshLoadComponent` | `com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadComponent` |

旧包路径不再提供兼容类型, 使用它们的外部模块需要更新引用并重新编译. `KitLineView` 和 `KitCodeInputEditText`
保持现有 `widget` 包路径. 包整理保留 XML 属性、样式入口及控件行为, 分格输入的当前长度和绘制规则见第 6 节.
`KitRangeBar.SavedState` 的完整类名随包路径变化, 不支持恢复迁移前版本保存的 View 状态; 升级后应重新创建页面状态.

`view.feed` 的 `KitContentFeedCardUi`, `KitContentFeedCardViewHolder`, `KitContentFeedCoverHeight`,
`KitContentFeedImageLoader` 以及 `view.share.KitShareSheetDialog` 不再提供, 对应专属资源一并移除.
旧版本的外部消费者应由自己的业务模块或应用 `foundation` 维护内容卡片, 分享面板和相关资源, 按应用展示需求组合基础控件.

### 4.1 线条绘制

`KitLineView` 通过 XML, `kitLineViewStyle` 或运行时属性配置方向, 颜色和线型. 默认绘制水平实线,
颜色使用主题的 `android:textColorSecondary` 状态色, 主题未提供时回退到 `Color.GRAY`.

如果只需要固定尺寸的静态实线, 优先使用已有 UI 依赖提供的标准控件. 接入 Material Components 时可以使用
`MaterialDivider`:

```xml
<com.google.android.material.divider.MaterialDivider
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:dividerThickness="1dp"
    app:dividerColor="?attr/colorOutlineVariant" />
```

不使用 Material Components 时可以直接使用平台 `View`:

```xml
<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:background="?android:attr/textColorSecondary" />
```

需要虚线、点划线、横竖方向统一配置、状态色或运行时调整时再使用 `KitLineView`.
水平实线可显式设置高度控制粗细, 使用 `android:color` 覆盖默认颜色:

```xml
<com.whisper.kit.widget.KitLineView
    android:id="@+id/divider"
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:color="@color/divider"
    android:orientation="horizontal" />
```

垂直虚线则使用宽度控制粗细, 高度控制线条长度:

```xml
<com.whisper.kit.widget.KitLineView
    android:layout_width="1dp"
    android:layout_height="48dp"
    android:color="@color/divider"
    android:orientation="vertical"
    app:kitDashWidth="4dp"
    app:kitGapWidth="2dp" />
```

示例的 `@color/divider` 由应用定义, `app` 使用 `http://schemas.android.com/apk/res-auto` 命名空间.
`kitDotWidth`, `kitDashWidth` 和 `kitGapWidth` 表示沿线方向的点长, 划长和间隔, 均默认 0, 支持小数尺寸,
负数按 0 处理. 间隔为 0 时显示实线, 无需设置点或划长度. 间隔为正且点划只有一者为正时显示虚线;
两者均为正时交替显示两段, 配置不同长度可得到点划线; 两者均为 0 时没有可绘制的图案, 不显示线条.
控件内部处理旧版 Android 的点划绘制兼容, 使用虚线或点划线时无需关闭硬件加速或配置软件图层.

线条粗细等于控件扣除 padding 后的高度(水平)或宽度(垂直). 该方向使用 `wrap_content` 时,
期望尺寸为 padding 总和加 1px, 并遵守 `minWidth` / `minHeight`, 背景最小尺寸及父布局上限.
沿线方向在父布局提供尺寸上限时会填满可用尺寸, 即使配置 `wrap_content` 也不会按一组点划的长度收缩.
padding 耗尽内容宽度或高度时不绘制线条.

`android:color` 支持纯色和 selector. 例如应用的 `res/color/divider.xml` 可按禁用和选中状态配置颜色,
其中引用的三个颜色资源由应用定义:

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false" android:color="@color/divider_disabled" />
    <item android:state_selected="true" android:color="@color/divider_selected" />
    <item android:color="@color/divider_default" />
</selector>
```

selector 按 View 自身状态匹配; 调用 `isEnabled = false` 或 `isSelected = true` 后线色自动更新.
聚焦状态同样可用, 但控件不会为状态色自动开启点击或焦点能力, 调用方应按交互需求配置相应 View 属性.

运行时属性与 XML 配置对应, 修改后自动重绘. `dotWidth`, `dashWidth` 和 `gapWidth` 的单位为 px `Float`,
负数按 0 处理, `NaN` 或无穷值会抛出 `IllegalArgumentException`:

```kotlin
val line = view.findViewById<KitLineView>(R.id.divider)
line.orientation = KitLineView.HORIZONTAL
line.dotWidth = 0f
line.dashWidth = with(line.context) { 4.dp }
line.gapWidth = with(line.context) { 2.dp }
line.lineColors = requireNotNull(AppCompatResources.getColorStateList(line.context, R.color.divider))
```

示例使用第 2 节的 `dp` 扩展及 AndroidX `AppCompatResources`. 将 `gapWidth` 设为 `0f` 即可恢复实线.
`lineColors` 接收非空 `ColorStateList`; 只需纯色时使用
`line.setLineColor(ContextCompat.getColor(line.context, R.color.divider_default))`, 这会替换已有状态列表.
`orientation` 仅接受 `KitLineView.HORIZONTAL` 或 `VERTICAL`, 非法值会抛出 `IllegalArgumentException`.
改变方向会触发重新测量, 但不自动交换布局宽高; 例如将 `match_parent` 宽, `1dp` 高的水平线切换为垂直线时,
调用方也应按布局需求调整宽高. 点划和间隔属性不控制线条粗细.

方向默认 `horizontal`. `KitLineView.HORIZONTAL` 和 `VERTICAL` 现与 Android `LinearLayout` 保持一致,
分别为 0 和 1, 旧版本的 1 和 2 不再适用. 引用旧常量的外部模块需要重新编译, 手写数值应改用常量或 XML 的
`horizontal` / `vertical` 枚举. 旧布局若曾用 `android:orientation="vertical"` 绕过映射错误来显示水平线,
应改为 `horizontal`; 使用正确枚举表达原始方向意图的布局无需修改.

默认行为也发生变化: 未配置点划长度和间隔的实例现在显示实线, 颜色从纯白改为主题状态色.
旧代码若通过全 0 线型隐藏控件, 应改用 `visibility` 或透明色; 需要固定白色时显式配置 `android:color`.

### 4.2 整数范围选择

`KitRangeBar` 选择从 0 到 `total` 的水平整数区间, 允许两端重合, 不允许交叉. 可以用两根手指同时调整两个端点;
重合时先按住, 向数值减小方向拖动调整起点, 向数值增大方向拖动调整终点. 默认在 LTR 页面中向右递增,
在 RTL 页面中向左递增, 起点始终表示较小数值.

```xml
<com.whisper.kit.widget.rangeselection.KitRangeBar
    android:id="@+id/range"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:kitBarSize="4dp"
    app:kitBarBackground="@drawable/range_track"
    app:kitBarForeground="@drawable/range_selected_track"
    app:kitThumb="@drawable/range_thumb"
    app:kitStartThumb="@drawable/range_start_thumb"
    app:kitStartThumbLabel="@string/range_start_label"
    app:kitEndThumbLabel="@string/range_end_label"
    app:kitThumbWidth="inner"
    app:kitThumbHeight="inner"
    app:kitTotal="100"
    app:kitStep="10"
    app:kitStart="20"
    app:kitEnd="60" />
```

示例 Drawable 和字符串资源均由应用提供, `app` 使用 `http://schemas.android.com/apk/res-auto` 命名空间.
`kitBarBackground` 绘制整个轨道, `kitBarForeground` 只覆盖选区. 至少配置一种轨道外观和所需滑块外观,
控件没有内置图形. 上例起点使用 `range_start_thumb`, 终点回退到通用 `range_thumb`; 也可用
`kitEndThumb` 单独配置终点. XML 属性可以集中配置到 `kitRangeBarStyle` 对应的样式.
独立滑块属性省略或使用 `inherit` 时继承通用外观; 使用 `none` 或 `@empty` 时明确清空该端,
例如 `app:kitEndThumb="none"`. 不使用 `@null` 表达清空, 因为资源解析后可能无法区分它与缺省配置.

`kitThumbWidth` / `kitThumbHeight` 默认 `inner`, 各端分别使用自身 Drawable 的固有尺寸; 没有固有尺寸时应显式配置尺寸.
显式宽高会同时应用于两个端点. `kitBarSize` 表示轨道厚度, 默认 8px, 建议应用按视觉需要配置 dp 值.
`kitStartThumbEnable` / `kitEndThumbEnable` 默认 `true`, 可以在 XML 中分别禁用端点拖动;
整个控件通过原生 `isEnabled` 控制. 滑块 selector 可以使用 `state_enabled`, `state_pressed`,
`state_drag_hovered` 和 `state_focused`, 两端拖动和焦点状态独立. 隐藏某端外观不会自动关闭该端交互配置.

数值配置均为整数, `kitStep` 必须为正数, 默认 1; `kitTotal` 默认 100, 小于 1 按 1 处理.
XML、代码赋值和拖动都只能选择从 0 开始的步长整数倍. 例如 `total=100`, `step=30` 时合法刻度为
`0, 30, 60, 90`, 100 始终无法选中; 给定 100 会调整到最近的合法刻度.

| 配置 | 作用 |
|------|------|
| `kitStartMin` / `setStartMin()` | 起点下限, 包含边界, 默认 `unlimited` / `THUMB_UNLIMITED`, 0 是有效下限 |
| `kitStartMax` / `setStartMax()` | 起点上限, 默认 `unlimited` / `THUMB_UNLIMITED`, 0 是有效上限 |
| `kitEndMin` / `setEndMin()` | 终点下限, 默认 `unlimited` / `THUMB_UNLIMITED` |
| `kitMinSpan` / `setMinSpan()` | 两端最小数值差, 默认 0, 此时允许重合 |

XML 及直接设置端点限制时, 参数限制在 `-1..total`; 最小间隔的负值按 0 处理.
`getStartMin()` 返回配置的下限, `THUMB_UNLIMITED` 表示未限制. 例如 XML 设置 `app:kitStartMin="20"`,
或调用 `range.setStartMin(20)`, 表示小于 20 的部分不可选, 20 在对齐步长时仍可选择.
两个端点都不能进入这一前缀; `setEndMin(20)` 则只限制终点, 起点仍可以小于 20.
下限不改变轨道的 0 到 `total` 数值域或从 0 起算的刻度: `startMin=20`, `step=30` 时最小可选值是 30,
不是从 20 起算新的刻度. 控件不会自动着色不可选前缀, 需要这种提示时由应用提供对应轨道 Drawable.
这些限制对所有赋值入口生效, 限制值本身不需要对齐步长. 例如 `step=10`, `minSpan=15` 时,
两端实际至少相差 20. 修改限制或 `total` 后, 控件自动将已有选区调整到最近可用范围:
最小化两端移动距离的平方和, 等距时优先较小的起点, 再选择较小的终点.
例如 `[20,60]` 在 `step=10` 下设置 `minSpan=70`, 会调整为 `[0,70]`.
新约束无可行范围时抛出 `IllegalArgumentException`, 保留旧配置和选区; 缩小 `total` 不会偷偷放宽既有下限或最小间隔.
例如 `total=100`, `step=30` 时设置 `endMin=100` 会被拒绝, 因为最大的合法刻度只有 90.

```kotlin
import com.whisper.kit.widget.rangeselection.KitRangeBar

val range = view.findViewById<KitRangeBar>(R.id.range)
range.setTotal(100)
range.setSelectedRange(20, 60)
range.setMinSpan(10)
range.setOnRangeChangedListener { start, end ->
    // 使用最终合法范围更新业务状态.
}
```

`start` 和 `end` 可读写, 单端赋值固定另一端, 在当前全部约束下选择该端的最近合法刻度, 不交换端点身份.
XML 初始范围和 `setSelectedRange()` 先排序两个原始参数, 再联合选择最近合法范围.
范围监听器仅在最终合法选区变化时回调, 包括程序赋值、`setTotal()`、约束调整和状态恢复引起的变化.
同一次触摸事件只通知两个端点都处理完成后的范围. 重复赋值、调整后范围不变或注册监听器本身均不通知;
初始值通过 `range.start` / `range.end` 读取. 普通赋值归一化后选区相同时也不会重置拖动锚点,
可以在业务状态同步时原样回写选区, 不改变后续拖动结果. 重复设置相同约束同样保持拖动连续性;
约束、数值域或布局几何实际变化时, 控件会更新活动锚点.

需要区分用户操作开始、正常结束和取消时, 单独注册交互监听器. 三个方法均有默认空实现, 可只覆盖需要的事件:

```kotlin
range.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
    override fun onInteractionEnd(start: Int, end: Int) {
        // 在用户正常结束操作后处理当前选区.
    }

    override fun onInteractionCancel(start: Int, end: Int) {
        // 当前选区保留实时变化, 由业务决定是否需要其它处理.
    }
})
```

`onInteractionStart(start, end)` 提供开始时的选区. 双指触摸从首次有效按下到最后一根手指抬起是一次操作,
期间增加、移除或切换触点不会拆成多次操作. 正常松手会先应用抬起位置, 再通知结束时的最终选区;
Android 13 及以上系统取消单个触点时, 不使用该触点的新坐标, 已应用的值仍保留, 其他手指可以继续拖动或接管空出的滑块.
单触点取消不会触发交互结束或取消回调, 仍等到最后一指正常抬起时结束操作.
键盘调节从按键按下到松开为一次操作, 长按重复包含在内; 每次有效无障碍调节动作各形成一次操作.
整次触摸取消、禁用、隐藏或移出窗口会取消活动操作, 保留实时选区, 不使用取消事件的坐标,
并且不再通知正常结束. 程序赋值和状态恢复不会产生交互事件.
操作进行中替换交互监听器只影响下一次操作, 当前操作的结束或取消仍通知开始时的监听器.

旧版 `release` 参数和 `setOnRangeChangedListener(invokeImmediately, listener)` 重载已移除.
原先在 `release=true` 时处理完成逻辑的调用方应迁移到交互监听器, 并分别处理正常结束和取消;
范围监听器改用两个参数. 外部消费者需要重新编译.

运行时可以更新通用或独立滑块外观, 独立配置优先, 清空与继承分别表达:

```kotlin
val thumb = AppCompatResources.getDrawable(range.context, R.drawable.range_thumb)?.mutate()
thumb?.alpha = 192 // 先隔离资源共享配置, 再应用实例外观.
range.setThumb(thumb)
range.setStartThumb(AppCompatResources.getDrawable(range.context, R.drawable.range_start_thumb)?.mutate())
range.setEndThumb(AppCompatResources.getDrawable(range.context, R.drawable.range_end_thumb)?.mutate())
range.setEndThumb(null) // 清空终点外观, 不回退到通用外观.
range.setEndThumb(KitRangeBar.INHERIT_THUMB) // 恢复继承通用外观.
```

上述资源同样由应用提供. `setThumb(null)` 只清除通用外观, 不影响已有独立覆盖.
程序配置直接使用传入的 Drawable, 不调用 `newDrawable()` 或 `mutate()`,
保留原实例的透明度、滤镜、tint、密度及组合图层外观. 从资源加载时, 应按上例先调用 `mutate()`,
再设置透明度、滤镜、tint 或密度等实例配置, 最后传给控件; 延后调用 `mutate()` 可能重建组合 Drawable 的子项并丢失配置.
代码直接新建的独占实例不需要 `mutate()`. XML 加载的外观由控件在加载时完成资源隔离.

通用外观可以只传一个实例供两端使用, 控件在绘制和测量时临时应用各端状态和 bounds, 完成后恢复.
轨道与滑块复用同一个实例时采用同样处理.
共享实例只保证静态状态外观及其固有尺寸, 状态切换时通过 `jumpToCurrentState()` 直接显示目标状态;
已有的动画调度会在进入共享时取消, 共享期间不调度动画, 外部直接请求刷新仍有效.
其颜色、透明度和私有动画状态仍是同一份. 需要分别改色或独立动画时, 使用 `setStartThumb()` / `setEndThumb()`
分别传入不同实例; 可以分别加载同一个 Drawable 资源, XML 也可为两个独立属性指定相同资源.
独占实例正常接收状态和生命周期变化, 控件不会在测量或绘制时强制跳过其过渡动画.
需要动态改色或启动动画时, 通过 `getStartThumb()` / `getEndThumb()` 获取当前实际绘制实例,
共享时两个 getter 返回同一对象, 修改会影响使用它的所有角色. 替换外观后重新获取,
控件负责状态、尺寸和绘制刷新. 传入的实例交由该控件管理, 不要同时交给其它 View 使用, 因为 `Drawable.callback` 只能有一个.
轨道可以通过 `setBarBackground()` / `setBarForeground()` 替换, 传入 `null` 关闭对应层绘制.

旧版本公开的 `mContentVerticalStart`, `mBarLength`, `mBarCenterX`, `mBarCenterY` 属于内部几何, 现已移除.
外部调用方应通过标准 View 布局、padding 和控件配置调整展示, 不再直接读写这些字段.

无障碍将两个端点表达为独立节点, 包含名称、整数当前值和合法范围. 两端重合时仍可分别聚焦,
通过增减动作按步长调整. 键盘 Tab / Shift+Tab 可在端点之间导航并离开控件, 左右键调整当前端点.
控件默认可聚焦, 保留显式设置的 `android:focusable` 和 `android:focusableInTouchMode`;
`android:focusable="false"` 关闭键盘焦点入口, API 26 及以上的 `auto` 保留平台自动判断语义.
可以使用原生 `setOnKeyListener()` 优先处理按键; 监听器返回 `true` 时控件不再执行对应调整或导航.
控件不使用浮点范围或任意进度设置动作, 大整数仍保持原有精度.

默认端点名称为通用名称, 业务可通过示例中的 `kitStartThumbLabel` / `kitEndThumbLabel` 字符串资源提供更具体的语义.
运行时使用 `startThumbLabel` / `endThumbLabel`:

```kotlin
range.startThumbLabel = range.context.getString(R.string.range_start_label)
range.endThumbLabel = range.context.getString(R.string.range_end_label)
range.startThumbLabel = null // 恢复通用默认名称.
```

为 View 设置稳定且唯一的 `android:id` 并保持原生状态保存开启后, 页面重建会恢复 `start`, `end`, `total`,
`step`, `startMin`, `startMax`, `endMin` 和 `minSpan` 的一致快照. 外观、动画和未结束的操作不恢复,
也不会因为恢复而产生交互事件. 若恢复改变当前选区, 范围监听器会收到一次最终范围通知.

RTL 镜像默认开启, 同时影响外观位置、触摸方向和键盘方向. 需要在 RTL 页面保持数值从左到右递增时,
在 XML 设置 `app:kitRtlMirroringEnabled="false"`, 或运行时设置 `range.isRtlMirroringEnabled = false`.
这只关闭数值方向镜像, 不改变 View 自身的布局方向或平台解析的 padding 和 Drawable 方向.

## 5. 富文本扩展

富文本扩展位于 `com.whisper.kit.extension` 包.

需要组合不同颜色和点击区域时, 可以配合 AndroidX `buildSpannedString` 使用 `color()` 和 `onClick()`:

```kotlin
val content: CharSequence = buildSpannedString {
    append("已阅读并同意".absoluteSize(contentSizePx))
    append(
        "《用户协议》"
            .relativeSize(1.0f)
            .color(linkColor)
            .onClick { openUserAgreement() }
    )
}
```

`absoluteSize(px)` 使用绝对像素字号. 如果设计字号是 sp, 应使用 `resources.getDimensionPixelSize()` 读取定义为 sp 的 dimen,
再把结果传给该扩展, 从而保留系统字体缩放. `relativeSize(proportion)` 使用当前 `TextView` 字号或已设置绝对字号的比例,
例如 `0.8f` 表示 80%. 两者同时使用时固定先取绝对字号再应用相对比例, 调用顺序不影响结果; 重复设置同一种字号时使用最后一次值.

其它可组合能力包括 `textStyle(Typeface.BOLD)`、`typeface(typeface)`、`underline()` 和 `strikethrough()`.
同一文本范围内重复调用同类设置时只保留最后一个对应 Span; 分别装饰不同文本片段再组合时, 各区域的 Span 同时保留.
`typeface()` 与 `textStyle()` 的调用顺序不影响组合结果. `BOLD` 与 `ITALIC` 会合并, `NORMAL` 会重置已有字形;
重复调用 `typeface()` 时使用最后一次传入的字体. 自定义字体 Span 面向进程内 UI 渲染, 不应用于需要通过 Bundle, SavedState
或 IPC 保留字体的文本契约.
`onClick()` 默认关闭下划线, 需要下划线时传入 `underline = true`. 点击范围重叠时, 后一次设置替换旧点击行为,
并使用后一次的回调和下划线配置; 分别装饰不同文本片段再组合时, 互不相交的点击区域同时保留.
`underline()` 与 `onClick(underline = false)` 作用于同一范围时, 后调用者决定最终是否显示下划线.
该扩展不设置颜色, 应按视觉语义与 `color()` 组合使用.
普通 `TextView` 承载点击文本时还需设置 `LinkMovementMethod`; 已统一处理可点击 Span 的公共组件不需要调用方重复设置.

## 6. 分格文本输入

数字或 ASCII 字母组成的短验证码可以使用 `KitCodeInputEditText`. 允许字符由调用方通过原生 `android:digits` 或
`filters` 限制, 控件不内置字符白名单. 以下示例只允许 ASCII 数字:

```xml
<com.whisper.kit.widget.KitCodeInputEditText
    android:id="@+id/codeInput"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:autofillHints="smsOTPCode"
    android:background="@android:color/transparent"
    android:digits="0123456789"
    android:hint="@string/code_slot_hint"
    android:importantForAutofill="yes"
    android:inputType="number"
    android:maxLength="6"
    android:textColor="@color/code_text"
    android:textColorHint="@color/code_hint"
    android:textSize="24sp"
    app:kitCodeHintFontFamily="sans-serif"
    app:kitCodeItemBackground="@drawable/code_item_background"
    app:kitCodeItemHeight="52dp"
    app:kitCodeItemSpacing="8dp"
    app:kitCodeItemWidth="45.5dp" />
```

`hint` 表示单个空输入格的提示内容。例如配置 `-` 后, 控件会在每个未输入位置绘制 `-`。
已输入内容仍保存在原生 `Editable` 中, 调用方可以继续使用 TextWatcher、Autofill、粘贴和删除。
控件不会在输入完成后自动提交; 页面应监听文本长度并把业务事件交给 ViewModel。

初始分格数量使用 XML 或样式中的原生 `android:maxLength`, 未配置时默认 6. 运行时通过 `filters` 设置正数
`InputFilter.LengthFilter`, 格数使用其中的最小上限; 未提供长度过滤器时自动补充 6 位限制, 其它过滤器保留.
非正长度会在修改状态前被拒绝. 缩短上限时同步截断已有文本, 并保留原 `Editable` 对象.

`codeLength` 已移除, 不提供弃用过渡 API. 原来的运行时赋值应改为替换原生长度过滤器, 并保留其它过滤器:

```kotlin
codeInput.filters = (
    codeInput.filters.filterNot { it is InputFilter.LengthFilter } + InputFilter.LengthFilter(4)
).toTypedArray()
```

长度沿用原生 UTF-16 单位: 有效代理对联合绘制并占两个格子, 缩短长度不会将其截成半个代理对. 控件不按 grapheme
计数或分格. 密码输入使用原生转换后的显示文本, 遮罩和临时可见行为遵循系统设置.

`kitCodeItemBackground` 可以传入 shape, selector 或其它 Drawable 来统一控制格子背景. selector 的固有尺寸变化会触发重新布局.
背景可见性跟随控件及祖先和窗口的聚合可见状态, 替换或移出窗口时隐藏并取消调度, 控件不会主动启动或停止背景动画.
使用 Ripple 时, 热点映射到最近格子的局部坐标并限制在格子内; 各格使用同一个背景实例和状态, 不会分别响应交互.
`wrap_content` 按格子宽高, 数量和 `kitCodeItemSpacing` 计算期望尺寸, 受像素 min/max 尺寸约束;
未指定格子尺寸和背景固有尺寸时, 文本与 `kitCodeHintFontFamily` 指定的 hint 字体共同决定自适应尺寸.
`match_parent`, 固定宽度, 父布局上限或控件自身的 `maxWidth` / `maxEms` 限制最终宽度时, `kitCodeItemSpacing` 不参与绘制,
控件会按最终可用宽度和分格数量重新分配间距. 父布局未限制宽度时, 控件自身上限造成的宽度压缩也遵循该规则.
空间不足以容纳全部固定宽度格子时会显示为格子重叠, 便于直接发现不合理的布局约束。

原生 compound drawable 和 `setError()` 图标可以继续使用. 控件按 compound padding 为图标与格子分配空间,
自适应尺寸会计入图标宽高, 图标位置遵循原生 RTL 解析结果.

应用应为业务布局显式指定组件样式, 并可通过主题的 `kitCodeInputEditTextStyle` 提供漏写 `style` 时的默认兜底。
直接依赖 kit 且没有应用组件样式时, 调用方仍需像上例一样显式提供背景、格子尺寸和文本样式;
应用级颜色和 Drawable 不应下沉到 kit。
可聚焦的控件默认支持触摸获焦, 无需在组件样式中重复声明; 显式设置 `android:focusableInTouchMode="false"`
或 `android:focusable="false"` 时仍保留调用方的配置.

RTL 页面中短码仍从左向右排列, 控件及周边布局继续继承 RTL 方向, 无需把整个 View 或页面强制设为 LTR.
控件隐藏光标并把选区保持在文本末尾. 页面使用 `-` 这类非描述性 hint 时, 应使用 `android:labelFor` 将字段标题与输入控件关联.

无障碍节点保留可编辑字段的文本, hint, 密码和标签语义, 支持焦点, 受长度限制的整段文本设置, 清空与粘贴;
IME 可以继续在末尾输入和删除. 当前不提供自由选区, 逐字导航和分格字符坐标, 默认文本菜单也不提供全选.
调用方自定义插入或选区 ActionMode callback 时应继续移除全选菜单项. 需要自由选区, 逐字导航, 短码倒序或复杂文字排版时,
应使用适合该场景的输入控件.

控件可编辑且剪贴板有内容时, 默认长按会打开系统浮动粘贴菜单, 粘贴到文本末尾并遵循现有 `digits` 和过滤器.
`customInsertionActionModeCallback` 仍参与菜单处理, 创建回调中同步结束菜单后, 后续长按可以重新打开菜单.
设置自己的 `OnLongClickListener` 会替换默认长按入口,
调用方应自行处理长按或接入自己的流程. 返回键, 再次点击输入框, 控件失焦, 控件或窗口不可见以及移出窗口时会关闭该菜单.

## 7. 刷新加载容器

需要下拉刷新和上拉加载的纵向内容可以使用 `KitRefreshLoadLayout` 包裹。容器只负责交互和回调,
具体刷新、加载、点赞、删除等业务状态仍由页面 ViewModel 维护。header/footer 子 View 角色必须通过
`app:kitLayoutRefreshLoadRole` 显式声明, 普通内容子 View 不需要声明角色。
除 header/footer 外最多只能有一个直接内容子 View; 页面包含多个内容 View 时, 先用一个布局容器组合后再放入.

应用可以在主题中统一配置默认 header/footer 和拖拽参数:

```xml
<style name="Base.Theme.Starter" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="kitRefreshLoadLayoutStyle">@style/Widget.Starter.KitRefreshLoadLayout</item>
</style>

<style name="Widget.Starter.KitRefreshLoadLayout">
    <item name="kitRefreshLoadHeaderLayout">@layout/view_refresh_load_header</item>
    <item name="kitRefreshLoadFooterLayout">@layout/view_refresh_load_footer</item>
    <item name="kitRefreshLoadRefreshEnabled">true</item>
    <item name="kitRefreshLoadLoadMoreEnabled">true</item>
    <item name="kitRefreshLoadAutoLoadMoreEnabled">false</item>
    <item name="kitRefreshLoadRefreshTriggerDistance">-1dp</item>
    <item name="kitRefreshLoadLoadMoreTriggerDistance">-1dp</item>
    <item name="kitRefreshLoadDragResistance">2.0</item>
    <item name="kitRefreshLoadAnimationDuration">240</item>
</style>
```

使用统一默认样式时, 页面只需要声明内容:

```xml
<com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadLayout
    android:id="@+id/refreshLoadLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadLayout>
```

单个页面需要特殊 header 或 footer 时, 可以显式声明对应 child 覆盖默认布局。只声明 header 时,
footer 仍会继续使用主题中的默认配置:

```xml
<com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadLayout
    android:id="@+id/refreshLoadLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="64dp"
        app:kitLayoutRefreshLoadRole="header" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadLayout>
```

header/footer 各允许 0 或 1 个。缺少 header 时下拉刷新会自动禁用, 缺少 footer 时上拉加载会自动禁用。
如果调用方仍将对应开关设置为 `true`, 控件当前有效值会保持禁用并输出警告日志。该设置意图会被保留,
后续运行时补充对应 header/footer 或通过默认样式补齐后, 控件会恢复对应能力。
header 为 `GONE` 时不能通过手势或无障碍发起刷新, footer 为 `GONE` 时不能通过手势、自动加载或无障碍发起加载.
拖拽中将对应组件设为 `GONE` 后, 松手也不会发起请求. 恢复非 `GONE` 后按现有开关及边界重新允许操作;
可见性不会修改 `refreshEnabled` / `loadMoreEnabled` 的值. `INVISIBLE` 仍保留交互能力.
隐藏组件不取消已经发起的任务, 请求完成后仍正常调用完成 API 或同步 ViewModel 状态.
新加入或替换的组件会在尺寸可用后收到当前状态和所属方向的位移快照, 刷新或加载中更换组件也会同步现有状态.
组件首次响应状态时可以更新文案或尺寸. 如果触发重新布局, 容器会等待新尺寸再同步位移和初始展开位置,
不会因为等待布局而重复通知同一状态.
位移回调中也可以更新文案. 如果更新请求重新布局, 但相对该组件上次收到的快照, 尺寸、角色、位移及有效距离均未变化,
容器不会再次补发相同快照; 实际变化后仍同步新快照, 初始展开会等待所需布局完成.

普通内容支持 `android:layout_gravity`, 例如固定尺寸内容可以设置 `center` 居中. header/footer 的纵向位置由角色固定,
横向支持 `start`、`end`、`center_horizontal` 等 gravity 配置, 并遵守 RTL 布局方向.
Kotlin 动态添加内容时传入的 `FrameLayout.LayoutParams` 也保留 gravity 和 margin.
header/footer 的上下 margin 计入忙碌时的展开距离, 正 margin 不会导致空闲时指示器露出.
例如高度为 40 px、上下 margin 各为 10 px 的 header, 默认触发距离仍为 40 px, 展开距离为 60 px.
负 margin 仍可造成重叠布局; 高度与 margin 总和非正时, 展开距离使用有效触发距离兜底.

内容 View 的滚动能力决定边界衔接效果。普通 `LinearLayout` 等不滚动 View 可以直接通过手势拉出 header/footer。
`RecyclerView`、`NestedScrollView` 等支持 NestedScrolling 的内容可以在滚到顶部或底部后连续把剩余滚动交给容器。
原生 `android.widget.ScrollView` 也支持这种手指拖拽交接, 需在 XML 中显式设置
`android:nestedScrollingEnabled="true"`, 或通过 Kotlin 设置 `scrollView.isNestedScrollingEnabled = true`.
原生 `ScrollView` 的惯性及程序滚动不发送嵌套增量, 无法仅靠该桥接保证惯性到底自动加载或指示器惯性衔接;
需要这些能力时使用 AndroidX `NestedScrollView` 或 `RecyclerView`.
可滚动但不支持 NestedScrolling 的自定义 View 可能需要松手后重新拖拽才能拉出 header/footer, 也可能无法触发滚动到底自动加载。
直接拖拽或 AndroidX NestedScrolling 交接的触摸如果收到系统取消事件, 会收起尚未提交的拖拽, 不触发刷新或加载回调.
下拉只触发刷新, 上拉只触发加载; 拖拽反向越过原位时, 另一侧也必须已启用且内容已到达对应边界.

容器默认调用当前滚动目标的 `canScrollVertically(direction)` 判断边界. 普通列表通常无需配置;
内容外层是组合容器、实际滚动列表位于内部时, 可以统一指定上下边界的判定:

```kotlin
binding.refreshLoadLayout.setScrollBoundaryCallback { _, _, direction ->
    binding.recyclerView.canScrollVertically(direction)
}
```

`direction = -1` 表示向顶部, `1` 表示向底部. 返回 true 表示还能继续滚动, false 只表示到达边界,
仍需满足开关及对应触发条件才会请求刷新或加载. 回调的第二个参数是当前嵌套滚动目标, 直接拖拽时是普通内容子 View.
此判定同时用于拖拽、自动加载和忙碌期间的头尾滚动交接. `setScrollBoundaryCallback(null)` 恢复默认判定.
回调中同步结束或切换任务时, 当前旧滚动处理会终止, 不再按原任务继续移动内容或发起请求.
旧 `setOnChildScrollUpCallback()` / `OnChildScrollUpCallback` 已移除, 调用方应迁移到新的双向判定.

设置容器 `isEnabled = false` 或将其移出窗口会清理尚未提交的拖拽及回弹. 已发起的刷新或加载保持任务状态,
业务请求仍由调用方管理, 请求结束后照常调用 `finishRefresh()` 或 `finishLoadMore()`.

`kitRefreshLoadRefreshTriggerDistance` 和 `kitRefreshLoadLoadMoreTriggerDistance` 默认值为 `-1`。
在 XML 样式中配置负数哨兵值时仍按 dimension 写法保留单位, 例如 `-1dp`。所有负数值都表示触发距离分别使用 header/footer 自身高度。
只有需要提前或延后触发时, 才配置具体 dimension 值; 配置 0 时按最小 1 px 处理。
运行时修改 `refreshTriggerDistance` / `loadMoreTriggerDistance` 会立即更新拖拽阶段和组件距离信息,
松手按更新后的阈值判定. 修改配置本身不会触发刷新或加载, 也不会重启忙碌或收起动画;
相同或等效的阈值赋值不重复通知组件.
默认阈值也会跟随组件高度变化, 完成重新布局后更新拖拽阶段与组件距离快照; 显式配置的触发距离不随高度改变.
即使只改变展开距离, 组件仍收到更新后的距离信息. 这些更新不重新定位已经展开的忙碌指示器, 也不重启现有动画.

刷新中或加载中, header/footer 会作为 content 的附加部分跟随列表滚动。用户继续滚动 RecyclerView 或列表进入惯性滚动时,
header/footer 会像列表条目一样逐步离开屏幕。
指示器惯性移动期间重新按住内容会停止旧惯性, 当前刷新或加载任务继续保持.
对应组件设为 `GONE` 后停止该指示器的滚动消费和惯性, 任务状态保留; 未提交拖拽仍可收起已有位移.

启用 `kitRefreshLoadAutoLoadMoreEnabled` 或运行时设置 `autoLoadMoreEnabled = true` 后,
内容向下滚动到底部时会自动进入加载更多状态, 并触发和手动上拉相同的 `RefreshLoadAction.LoadMore` 回调。
自动加载使用相同的底部边界判定, 默认依赖内容 View 的 `canScrollVertically(1)`, 不绑定 RecyclerView 或具体 LayoutManager.
如果需要没有更多数据时停止自动触发, 页面应在 ViewModel 状态中将 `loadMoreEnabled` 设置为 `false`。

`kitRefreshLoadDragResistance` 是基础拖拽阻尼。实际阻尼会随 header/footer 拉出距离增加, 越接近最大拖拽距离阻力越大。
XML 配置与运行时 `dragResistance` 均只接受有限值, 默认 2, 小于 1 的有限值按 1 处理.
传入 `NaN`、`Float.POSITIVE_INFINITY` 或 `Float.NEGATIVE_INFINITY` 会抛出 `IllegalArgumentException`, 旧值保持不变.
参数注解用于 IDE / Android Lint 提示, 动态计算得到的非法值仍由 setter 在运行时拒绝.
慢速的小幅移动也会累计生效. 回弹和完成收起动画使用减速曲线, 越接近目标位置速度越慢。
`kitRefreshLoadAnimationDuration` / `animationDurationMillis` 控制展开和收起的位置动画, 单位为毫秒,
默认 240, 小于等于 0 时直接定位; 方法参数 `animate = false` 也会直接定位.
完成后收起期间状态保持为 `Settling`, 不会因原手势随后停止再次触发请求; 新触摸手势可以接管回弹.
完成或取消后的新非触摸滚动会话仍可触发自动加载, 不需要先触摸页面.

header/footer View 如需根据拖拽状态更新文案或动画, 可以实现 `KitRefreshLoadComponent`:

```kotlin
import com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadComponent
import com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadLayout

class DebugRefreshHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs),
    KitRefreshLoadComponent {

    override fun onRefreshLoadStateChanged(
        layout: KitRefreshLoadLayout,
        state: KitRefreshLoadLayout.RefreshLoadState,
    ) {
        text = when (state) {
            KitRefreshLoadLayout.RefreshLoadState.PullingDown -> "下拉刷新"
            KitRefreshLoadLayout.RefreshLoadState.ReleaseToRefresh -> "释放刷新"
            KitRefreshLoadLayout.RefreshLoadState.Refreshing -> "正在刷新"
            else -> "下拉刷新"
        }
    }

    override fun onRefreshLoadOffsetChanged(
        layout: KitRefreshLoadLayout,
        offset: Int,
        triggerDistance: Int,
        holdDistance: Int,
    ) = Unit
}
```

容器空闲、全局和对应方向启用且组件可用时, 无障碍服务可执行刷新或加载更多动作.
显式动作可以从内容任意位置发起, 不改变列表滚动位置. 忙碌时提供正在刷新或加载的状态描述;
内容和 header/footer 的无障碍节点保持独立, 调用方已有的 `contentDescription` / `stateDescription` 不会被覆盖.
容器不截获列表方向键, 页面需要键盘刷新入口时可以提供普通操作按钮并调用 ViewModel.

页面监听手势、自动加载或无障碍发起的动作, 再转交给 ViewModel.
业务回调发生时已经进入对应忙碌状态, 但指示器可能仍在等待布局或展开动画, 回调不代表动画结束.

```kotlin
binding.refreshLoadLayout.setOnRefreshLoadListener { action ->
    when (action) {
        KitRefreshLoadLayout.RefreshLoadAction.Refresh -> viewModel.refresh()
        KitRefreshLoadLayout.RefreshLoadAction.LoadMore -> viewModel.loadMore()
    }
}
```

页面收集 ViewModel 状态后同步列表及当前任务显示. 刷新与加载更多应由业务维持互斥:

```kotlin
adapter.submitList(state.items)
binding.refreshLoadLayout.loadMoreEnabled = state.hasMore
binding.refreshLoadLayout.autoLoadMoreEnabled = state.autoLoadMore

val activeAction = when {
    state.refreshing -> KitRefreshLoadLayout.RefreshLoadAction.Refresh
    state.loadingMore -> KitRefreshLoadLayout.RefreshLoadAction.LoadMore
    else -> null
}
binding.refreshLoadLayout.setActiveAction(activeAction)
```

`setActiveAction()` 只同步显示, 不触发 `OnRefreshLoadListener`, 默认不播放动画; 需要位置过渡时传 `animate = true`.
相同忙碌状态重复同步不会重新拉出已随列表滚动隐藏的指示器, 也不会重启已有动画. 切换任务时旧手势失效;
传 null 会收起拖拽或忙碌显示, 空闲及正在收起时重复传 null 不重新执行.
首次布局前调用或对应组件暂时缺失、`GONE` 时, 控件等待尺寸可用后定位; 期间新的同步结果会替代旧结果.

显示同步不受 `isEnabled`、`refreshEnabled` 或 `loadMoreEnabled` 限制, 也不会启用这些开关.
关闭方向开关本身仍会收起该侧显示, 动画期间为 `Settling`, 收起完成后才回到 `Idle`;
此操作不取消业务请求. 同一轮状态渲染应先设置交互开关, 再同步真实任务, 如上例所示.
仅需结束某一侧任务时仍可使用 `finishRefresh()` / `finishLoadMore()`, 默认播放收起动画.

控件不会通过 View 的 `SavedState` 保存或恢复业务请求. 页面重建后重新收集 ViewModel 状态并调用 `setActiveAction()`,
即可恢复已有任务的显示而不重复发起请求. 进程重建后是否重新请求仍由业务判断, 不恢复旧触点和动画.

没有更多数据的文案或分割展示应由列表自身处理, 例如在列表末尾 item 或 RecyclerView decoration 中绘制。

## 8. RecyclerView Decoration

item 与 item、item 与 RecyclerView 主轴边界之间的间距统一通过 Decoration 配置. 不要在 item 根 View 上设置 margin
表达列表间距, 也不要在 item XML 或 ViewHolder 中增加纯装饰性的分割线 View. item 内部标题、图片、标签等内容之间的 margin
仍由 item 布局自身维护.

线性或网格列表可以使用 `RegularItemSpaceDecoration` 设置 item 间距:

```kotlin
recyclerView.addItemDecoration(
    RegularItemSpaceDecoration(
        mainAxisSpace = 16,
        crossAxisSpace = 12,
        startSpace = 16,
        endSpace = 16,
    )
)
```

瀑布流列表使用独立的 `StaggeredItemSpaceDecoration`. Adapter 同时实现 `StaggeredFullSpanProvider`, 并在绑定时
复用同一个查询结果设置 LayoutParams:

```kotlin
class FeedAdapter(
    private val items: List<FeedItem>,
) : RecyclerView.Adapter<FeedViewHolder>(), StaggeredFullSpanProvider {

    override fun isFullSpan(position: Int): Boolean = items[position].isSectionHeader

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val layoutParams =
            holder.itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams
        layoutParams.isFullSpan = isFullSpan(position)
        holder.bind(items[position])
    }
}

recyclerView.layoutManager = StaggeredGridLayoutManager(
    2,
    RecyclerView.VERTICAL,
)
recyclerView.adapter = FeedAdapter(items)
recyclerView.addItemDecoration(
    StaggeredItemSpaceDecoration(
        mainAxisSpace = 16,
        crossAxisSpace = 12,
        startSpace = 16,
    )
)
```

`StaggeredItemSpaceDecoration` 不接收 `spanCount` 或 `spanIndex`: 两者直接读取当前 LayoutManager. full-span item
不会增加交叉轴 offset; `mainAxisSpace` 位于所有非起始 item 的 logical start; `startSpace` 位于实际接触 Adapter
逻辑起始边界的 item. 当 `startSpace != mainAxisSpace`、span 数大于 1 且列表不止一个 item 时, 该主轴起始拓扑需要
RecyclerView 的直接 Adapter 实现 `StaggeredFullSpanProvider`, 当前不能自动穿透 `ConcatAdapter`.
缺少 Provider 不会导致崩溃: Decoration 会按实例记录一次警告并禁用主轴 offset, 交叉轴仍按 LayoutParams 的实际
span/full-span 状态工作. Adapter 已实现 Provider 时, 其结果必须与 `LayoutParams.isFullSpan` 一致, 否则立即失败.
该装饰器不提供 `endSpace`, 因为瀑布流各 span 的结束位置不一定一致.
每个 item 包含 decoration inset 和 LayoutParams margin 后的 decorated 主轴占用尺寸必须大于 0;
`StaggeredItemSpaceDecoration` 和 `StaggeredItemDividerDecoration` 都不支持主轴零尺寸 item.

Staggered 列表需要分割线时使用 `StaggeredItemDividerDecoration`, Adapter 继续复用上面的
`StaggeredFullSpanProvider` 实现:

```kotlin
recyclerView.addItemDecoration(
    StaggeredItemDividerDecoration(
        dividerSize = 1,
        dividerMargin = 16,
        dividerColor = Color.LTGRAY,
    )
)
```

主轴和交叉轴需要不同尺寸或 Drawable、但不需要 margin 时, 使用四参数次构造器:

```kotlin
StaggeredItemDividerDecoration(
    mainAxisDividerSize = 1,
    crossAxisDividerSize = 12,
    mainAxisDivider = ColorDrawable(Color.LTGRAY),
    crossAxisDivider = null,
)
```

交叉轴分割线在当前已布局内容的 span 间隙中沿主轴连续绘制, full-span item 的实际区域会从分割线中排除;
主轴分割线绘制在所有非起始 item 的 logical start 间距中. 该装饰器同样不提供 item 的 `endSpace`.
`crossAxisDividerMainAxisEndMargin` 只缩进连续分割线在当前内容包络中的 logical end, 不改变任一 item 的 offset.
主轴分割线尺寸非 0、span 数大于 1 且列表不止一个 item 时需要 Provider; 缺少时主轴 offset 和绘制一起禁用,
交叉轴 offset 与分割线继续工作.

Linear/Grid 列表需要绘制分割线时使用 `RegularItemDividerDecoration`:

```kotlin
recyclerView.addItemDecoration(
    RegularItemDividerDecoration(
        dividerSize = 1,
        dividerMargin = 16,
        dividerColor = Color.LTGRAY,
    )
)
```

`RegularItemDividerDecoration` 也提供同样的四参数次构造器, 用于双轴独立、零 margin 的配置. 现有便捷构造器无法表达
非零 margin 与双轴独立尺寸或 Drawable 的组合时, 使用完整八参数构造器, 并应使用命名实参避免混淆相邻的同类型参数.

Grid 中 `crossAxisDivider` 先在 span 间隙中沿主轴连续绘制, `mainAxisDivider` 再在非首行或列的 item
logical start 中沿交叉轴分段绘制. 连续线只处理当前已布局内容, 不需要 Adapter 预先加载全部 item. 分割线在 item
之前绘制; 数据更新动画期间允许归属短暂偏离旧 View.

同一个 RecyclerView 的同一轴最多只能由一个 Decoration 提供非零内部间距. 不要在同一轴叠加非零的 SpaceDecoration
和 DividerDecoration, 也不要叠加两个非零 DividerDecoration: RecyclerView 虽然会累加 offset, 但分割线仍会锚定同一个
item 边界, 且分别取整后的交叉轴 offset 不再保证合计误差最多 1px. 主轴和交叉轴内部间距均为 0、只提供
`startSpace` / `endSpace` 的边界 SpaceDecoration 可以共存.

`mainAxisDividerSize` 和 `crossAxisDividerSize` 始终预留对应轴的 offset. 对应 Drawable 为 `null` 时只保留透明间距,
不会绘制分割线. 因而同一个 DividerDecoration 可以让一个轴绘制分割线、另一个轴只留白, 无需再安装第二个内部间距
Decoration.

Decoration 不观察 Adapter 更新. 同步 `notifyItem*` 会改变 position、itemCount、span 或首尾归属时, 在触发更新前注册
下一次布局回调, 并在该次更新布局完成后手动失效 Decoration:

```kotlin
import androidx.core.view.doOnNextLayout

recyclerView.doOnNextLayout {
    recyclerView.invalidateItemDecorations()
}
adapter.notifyItemMoved(fromPosition, toPosition)
```

`ListAdapter` 或 `AsyncListDiffer` 的 `submitList` 会异步计算差异, 不能在调用 `submitList` 前预注册同一个一次性回调:
差异提交前发生的其它布局可能提前消费该回调. 应在 commit callback 中注册下一次布局回调:

```kotlin
adapter.submitList(newList) {
    recyclerView.doOnNextLayout {
        recyclerView.invalidateItemDecorations()
    }
}
```

连续调用 `submitList` 时, 较早但未实际提交的列表可能不会执行 commit callback. 最终状态恢复必须挂在实际提交的最新列表
callback 中. 不要只在同步 `notifyItem*` 之后立即调用 `invalidateItemDecorations()`: predictive layout 可能在 pre-layout
消费这次失效, 无法保证最终 offset.
pre-layout 的旧布局数量与 Adapter 当前数量不一致时, 首尾 span group 可能暂时使用保守间距; Decoration 不会使用旧 position
查询当前 Adapter 的 `SpanSizeLookup`. 布局完成后的手动失效负责恢复严格的最终间距.

运行时修改 `orientation`、`reverseLayout`、`spanCount`、替换 `SpanSizeLookup` 实例或 RecyclerView layout direction 时,
这些配置变更不会保证将 RecyclerView 缓存的 decoration inset 标记为过期.
在修改配置后立即调用 `recyclerView.invalidateItemDecorations()`, 使下一次布局使用新的方向和 span 拓扑.

如果修改的是同一个 `SpanSizeLookup` 实例的内部规则, 还必须先清除 LayoutManager 持有的 span 查询缓存:

```kotlin
// 修改 spanSizeLookup 所读取的业务规则后:
spanSizeLookup.invalidateSpanIndexCache()
spanSizeLookup.invalidateSpanGroupIndexCache()
recyclerView.invalidateItemDecorations()
```

`invalidateItemDecorations()` 只使 RecyclerView 缓存的 decoration inset 失效, 不会清除 `SpanSizeLookup` 的缓存.

分别配置两类分割线时, margin 参数名中的方向表示 margin 实际应用的轴. `mainAxisDivider` 沿交叉轴延伸,
因此使用 `mainAxisDividerCrossAxisStartMargin` / `mainAxisDividerCrossAxisEndMargin`; `crossAxisDivider` 沿主轴延伸,
因此使用 `crossAxisDividerMainAxisStartMargin` / `crossAxisDividerMainAxisEndMargin`. `Start/End` 均为对应轴的逻辑方向,
会随 RTL 和 `reverseLayout` 映射到正确物理边.

间距装饰器的参数以及 `RegularItemDividerDecoration` / `StaggeredItemDividerDecoration` 的分割线尺寸和 margin
只接收非负 px 值. Decoration 不负责 dp 转换、主题色读取或业务尺寸选择.
Adapter 继续只负责 item 创建和数据绑定, LayoutManager 继续负责方向、span、布局和滚动. 分割线绘制边界见
[RecyclerView Decoration 边界](recyclerview-decoration-boundary.md).
Decoration 类型与 LayoutManager 不匹配时会清空 offset、跳过绘制并按实例记录一次警告, 不会使应用崩溃.

## 9. RecyclerView 滚轮布局

纵向滚轮列表使用 `WheelLayoutManager` 与 `WheelSnapHelper`. 默认是有界滚动:

```kotlin
val wheelLayoutManager = WheelLayoutManager()
recyclerView.layoutManager = wheelLayoutManager
recyclerView.adapter = wheelAdapter
WheelSnapHelper().attachToRecyclerView(recyclerView)

wheelLayoutManager.setOnCenterYPositionChangeListener { position ->
    val selectedItem = items.getOrNull(position)
    // 将 selectedItem 交给页面状态.
}
```

中心变化监听器提供当前时刻最近中心 position. 拖动、fling、程序滚动、布局和 SnapHelper 吸附过程中, 只要最近中心
position 变化就会同步通知, 不等待 RecyclerView idle 或吸附完成; position 不变时不会重复通知. 因此一次 fling 跨过多个
条目时会依次通知多个 position, 这是预期行为, 适合实时高亮和实时级联. 只需要滚动停止后提交最终值的业务应在上层观察
RecyclerView 滚动状态或提供明确的确认操作, 不应把该监听器理解为最终选择事件.

日期等实时级联场景应先根据本次 position 变化归一化出唯一合法的整体状态, 再用该状态更新各级数据和滚轮位置;
不要在更新下游 Adapter 后立即从可能仍处于布局过程的旧 child 反读业务值, 并应避免程序定位再次触发同值级联.

数据从非空变为空时, 中心变化监听器会收到 `RecyclerView.NO_POSITION`; 重复空布局不会重复通知. 数据重新出现后,
即使中心仍是清空前相同的数字 position, 也会再次通知该 position. 调用方应像示例一样安全解析 position,
不要直接用未校验的回调值索引数据. 空数据不会消费等待数据恢复的 `scrollToPosition()` 目标或状态恢复锚点.

需要 Adapter 首尾相接时显式请求循环模式:

```kotlin
recyclerView.layoutManager = WheelLayoutManager(
    WheelLayoutManager.SCROLL_FEATURE_UNLIMITED,
)
```

`SCROLL_FEATURE_UNLIMITED` 是循环请求而不是无条件保证. LayoutManager 会按 item 的实际测量结果试排: 每个真实 Adapter
position 最多挂载一次即可覆盖布局窗口时启用首尾循环; 数据不足以覆盖时自动退化为有界滚动. 因此是否循环不由固定 item
数量或预估总高度决定, 数据量或 item 尺寸变化后的重新布局也会重新判断. item 的纵向占用尺寸可以为 0, 不会被跳过、
强制改成 1px 或单独导致异常; LayoutManager 通过推进真实 position 并限制单次最多访问一轮数据来保证布局终止.
零高度 item 与其它唯一 item 共同能够覆盖布局窗口时仍可启用循环, 否则按相同规则退化为有界滚动.

`scrollToPosition(position)` 与 `smoothScrollToPosition(position)` 都会以目标条目居中为最终位置. Adapter 继续提供真实数据量和
标准 position, 不需要伪造很大的 itemCount 或处理虚拟 position. `notifyItemChanged()` 等标准更新仍作用于唯一的真实 position;
循环模式也不会把超过 Adapter 上界的值解释为额外圈数或取模目标. `smoothScrollToPosition()` 的负 position 属于配置错误并
抛出 `IllegalArgumentException`; Adapter 为空时不启动平滑滚动; 超过当前上界的目标与 RecyclerView 常规行为一致,
收敛到最后一个真实 position, 再根据当前实际滚动模式选择行进方向. 构造 `WheelLayoutManager` 时也必须使用公开的
`SCROLL_FEATURE_LIMITED` 或 `SCROLL_FEATURE_UNLIMITED`, 其它值会抛出 `IllegalArgumentException`.
启用稳定 ID 时仍应遵守 RecyclerView 的唯一和稳定约束. LayoutManager 只提供纵向布局和滚动; item 高度、内容、选中线、
颜色渐变及日期联动由调用方负责. RecyclerView 使用非零或非对称 padding 时, 条目会对齐到扣除 padding 后的内容中心;
运行时修改容器尺寸或 padding 后, 当前逻辑中心会随新的内容中心移动, 尚未吸附完成的相对偏移也会保留.
item 的 LayoutParams margin 与 ItemDecoration offset 会作为完整外边界参与测量、布局和吸附, 不会压缩 item 自身尺寸.
多个零高度 item 同时位于中心时, `smoothScrollToPosition()` 仍会把指定目标确认为逻辑中心.
有界模式的系统滚动指标按首项居中到末项居中的区间计算, 因而 `canScrollVertically(direction)` 会在真实首尾边界收敛;
非等高条目的滚动条长度和位置与 `LinearLayoutManager` 的平滑滚动条一样, 根据当前已布局条目估算, 滚动过程中可能随
样本变化. 实际循环模式没有绝对圈数, 系统滚动指标固定在可双向滚动的相对中间态, 跨首尾时不会跳到起点;
此时滚动条不表示全局进度, 不需要视觉滚动条的滚轮应由界面配置关闭. 请求循环但自动退化时仍显示有界进度和边界.
如果退化发生在一次滚动填充中, 当前 child 在下一次布局前仍保持循环顺序, 该短暂窗口会延续循环相对指标;
该窗口不再消费新的滚动位移, 有界重布局完成后再恢复滚动并切换为真实有界进度和方向,
避免过渡期间错误报告两个方向都不可滚或重复挂载同一 position.

`WheelLayoutManager` 不支持 RecyclerView predictive item animations, 但不会关闭 RecyclerView 的 ItemAnimator.
使用 `notifyItemInserted()`、`notifyItemRemoved()`、`notifyItemMoved()`、`notifyItemChanged()` 或对应 range API 时,
默认 ItemAnimator 仍可执行普通的增加、删除、移动和内容变化动画. 更新前后都具备布局位置的可见条目可以按坐标移动;
缺少一端布局位置的 appearing/disappearing 条目通常使用普通淡入淡出, 不会从推断出的屏幕外位置移动进入或离开.
调用方无需设置 `itemAnimator = null`; 只有希望关闭全部条目动画时才应这样配置.

当 RecyclerView 具有可保存的 View ID 并参与 View 层级状态保存时, `WheelLayoutManager` 会随 RecyclerView 自动保存和恢复
中心 Adapter position 及尚未吸附完成的像素偏移. 恢复期间调用 `scrollToPosition()` 会以显式目标为准. Adapter 异步加载且
初始可能为空时, 应延迟 RecyclerView 分发恢复状态:

```kotlin
adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
```

LayoutManager 会额外保留已经收到但尚无数据可布局的恢复锚点. 如果非空布局时显式或恢复 position 越界, 该锚点会被放弃,
不会在循环模式中取模到其它条目; 有现有 child 时继续保持当前中心, 否则从 position 0 布局. 恢复前后 item 尺寸变化时,
锚点仍与当前内容区域相交就保留原像素偏移; 锚点完全不可见时保留 position 并重新居中, 避免旧像素偏移产生空白视口.
position 恢复不等于业务身份恢复; 数据可能重排时, 调用方应保存业务 key, 在新数据中查找对应 position 后显式调用
`scrollToPosition()`.

## 10. RecyclerView ViewHolder

ViewBinding item 可以使用 `ViewBindingHolder` 减少 ViewHolder 样板代码:

```kotlin
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewBindingHolder<ItemUserBinding> =
    ViewBindingHolder.create(parent, ItemUserBinding::inflate)

override fun onBindViewHolder(holder: ViewBindingHolder<ItemUserBinding>, position: Int) {
    holder.binding.name.text = items[position].name
}
```

该工具只持有 binding, 不持有 item 数据, 不处理业务点击事件。点击事件可按需搭配 RecyclerView 点击分发工具统一处理。

## 11. RecyclerView 点击与长按分发

RecyclerView item 内子 View 点击可以通过统一触摸监听分发, 避免在每个 ViewHolder 中分散绑定点击逻辑:

```kotlin
recyclerView.addOnItemChildClickListener { recyclerView, child, absoluteAdapterPosition ->
    when (child.id) {
        R.id.avatar -> ...
        R.id.delete -> ...
    }
}
```

长按使用对称的扩展函数. 目标 View 必须设置 `longClickable = true`; 通过原生 `setOnLongClickListener` 配置的 View
会自动具有该标志:

```kotlin
recyclerView.addOnItemChildLongClickListener { recyclerView, child, absoluteAdapterPosition ->
    when (child.id) {
        R.id.avatar -> ...
        R.id.card -> ...
    }
}
```

该工具只回调 `clickable && enabled` 的命中目标, 不消费事件, 不改变子 View 原本的触摸和点击行为. 它不会向 item 或子 View
安装监听器、AccessibilityDelegate 或其它状态. 需要让 item 空白区域响应点击时, 应由调用方将 item 根 View 设置为 clickable.
单击目标在按下时确定; 手指移动期间即使其它 View 进入触点区域, 抬起时也不会改派目标. 原目标离开允许的点击边界后,
即使再次回到触点区域, 当前手势也不会触发回调.
只有 LayoutManager 支持的滚动轴位移超过 RecyclerView touch slop 才会按滚动取消手势. 触点仍在目标边界内时,
交叉轴移动不会单独取消点击或长按.
子 View 调用 `requestDisallowInterceptTouchEvent(true)` 后, RecyclerView 级监听器可能无法继续收到完整事件序列;
该工具会取消已经锁定的目标, 当前手势不会回调. 传入 `false` 不影响当前目标.
快速双击会像普通 View 一样产生两次独立点击回调, 不会等待双击确认或吞掉第二次点击.

点击监听器在平台长按超时回调当下决定当前手势是否由长按占用:

| 超时当下的目标状态                  | 后续旁路点击行为                                           |
|-------------------------------------|------------------------------------------------------------|
| `longClickable && enabled`          | 立即且永久取消当前点击, ACTION_UP 不再恢复                 |
| 不满足 `longClickable && enabled`   | 保留目标, ACTION_UP 通过全部最终校验后回调                 |

这项判断只读取公开 View 标志. 即使没有安装 `ItemLongClickTouchListener`, 只要目标在超时时是 longClickable 且 enabled,
旁路点击仍会被取消. 通过 `setOnLongClickListener` 配置的 View 通常会自动成为 longClickable; 即使其原生监听器最终返回
`false`, 本工具也无法无侵入地取得该结果, 因而仍按 longClickable 处理. 反过来, 自定义 OnTouchListener 自行识别长按但没有设置
longClickable 时, 本工具不会把该手势视为已被长按占用.

属性判断只发生在长按超时回调当下: 当时已经取消的点击不会因之后关闭 longClickable 而恢复; 当时保留的目标也不会因之后
开启 longClickable 而重新取消. 保留目标不代表一定回调, ACTION_UP 仍会检查目标是否 `clickable && enabled`、是否仍属于原 item、
触点是否仍在允许边界内、adapter position 是否有效, 以及 RecyclerView、item、目标的窗口挂载和焦点状态.

同一 RecyclerView 同时安装点击和长按旁路监听器时, 同时满足 clickable 与 longClickable 的目标会在超时后只产生旁路长按通知,
不会再产生旁路点击通知. 两个监听器仍都不消费原生事件; View 自己的 OnClickListener、OnLongClickListener 和上下文菜单按原生
触摸链路独立运行, 可能形成另一套通知. 不应让旁路回调和原生回调重复执行同一个不可幂等业务操作.

回调位置是 RecyclerView 完整 Adapter 链中的 absolute adapter position. 使用 `ConcatAdapter` 时, 它不是子 Adapter 的
binding adapter position; 需要子 Adapter 位置的调用方应按自己的 Adapter/数据映射关系转换.

长按使用平台手势超时, 回调不返回 Boolean, 也不消费或替代 View 原生 OnLongClickListener. 同一个 View 同时配置原生监听器和
RecyclerView 旁路监听器时会收到两套长按通知.
RecyclerView、item 或目标已经脱离窗口, 或 RecyclerView 已失去窗口焦点时, 点击和长按都不会回调.

该能力只观察经过 RecyclerView 的 MotionEvent, 因此无障碍操作、键盘激活和代码直接调用 `performClick()` / `performLongClick()`
不会触发此回调. 需要支持这些输入方式的业务操作必须继续通过 View 原生点击和无障碍链路提供, 不能把该触摸旁路回调作为唯一入口.

命中和遮挡只匹配 `clickable`、`enabled`、`longClickable`、`contextClickable` 等标准 View 标志. 非 clickable View 上
自定义的消费型 OnTouchListener 可能被视为普通前景, clickable View 的自定义消费结果也不会改变旁路回调;
TouchDelegate 扩展出的点击区域不会被该工具识别. 这些场景没有可靠的无侵入探测方式, 应继续使用 View 原生监听器处理,
不要把 RecyclerView 旁路回调作为其业务入口.

扩展函数会把监听器正确绑定到当前 RecyclerView. 直接调用公开构造器时, 构造参数和
`addOnItemTouchListener()` 的接收者必须是同一个 RecyclerView, 该绑定关系由调用方保证.

该工具只保证实际收到完整 DOWN 至 UP/CANCEL 序列时的识别. 不要在手势进行中移除监听器, 也不要与可能在本监听器收到
DOWN 后中途消费同一事件流的其它 `OnItemTouchListener` 组合使用. AndroidX 不会在这些情况下补发 CANCEL,
已排队的长按仍可能回调.

返回值是已经添加到 RecyclerView 的触摸监听器. 当前手势结束后需要移除时使用:

```kotlin
val listener = recyclerView.addOnItemChildClickListener { _, _, _ -> }
val longClickListener = recyclerView.addOnItemChildLongClickListener { _, _, _ -> }

recyclerView.removeOnItemTouchListener(listener)
recyclerView.removeOnItemTouchListener(longClickListener)
```

## 12. 匿名设备标识

需要为验证码请求控频等场景提供一个无需额外权限的弱标识时, 可以使用:

```kotlin
val deviceId: String = DeviceIdUtils.getDeviceId(context)
```

该值由应用首次生成并保存在私有 SharedPreferences 中, 在应用数据仍然存在时尽量保持稳定. 它不读取 `ANDROID_ID`,
不绑定设备或硬件, 也不保证在清除数据、卸载、备份恢复、存储异常或多进程竞争后保持不变. 宿主应用允许系统备份时,
已有值可能随备份恢复到另一设备. 如果首次调用时 SharedPreferences 暂时不可用, 后续调用会保持当前进程中的同一个值并尝试补写.

调用方可以把它作为服务端风控输入之一, 但不能用于身份认证、授权或唯一控频依据. 验证码接口仍应在服务端结合手机号、账号、
IP、请求行为或平台完整性信号进行限制, 并按适用的隐私规则处理和保留该标识.

## 13. 客户端与应用信息

读取 Android 平台与当前宿主应用的基础信息:

```kotlin
val sdkInt: Int = ClientInfoUtils.sdkInt
val release: String = ClientInfoUtils.osRelease
val manufacturer: String = ClientInfoUtils.manufacturer
val brand: String = ClientInfoUtils.brand
val model: String = ClientInfoUtils.model
val packageName: String = ClientInfoUtils.getPackageName(context)
val versionName: String? = ClientInfoUtils.getAppVersionName(context)
val versionCode: Long? = ClientInfoUtils.getAppVersionCode(context)
```

这些 API 返回平台或 PackageManager 原值, 不会清理空白、拼接显示名称或替换缺失值. 版本信息在当前包不存在时返回 `null`;
调用方应根据自己的展示或协议契约处理缺失值, 不要直接把 nullable 值插入请求头或用户可见文本.

读取当前 Context 资源配置实际使用的 Locale:

```kotlin
val locales: LocaleList = ClientInfoUtils.getAppLocales(context)
val primaryLocale: Locale = ClientInfoUtils.getPrimaryAppLocale(context)
```

`getAppLocales()` 保留配置中的完整列表. `getPrimaryAppLocale()` 返回首项, 仅在列表异常为空时回退到进程默认 Locale.
这些结果表示传入 Context 的资源配置, 不保证等同于应用显式设置的全局语言列表.

需要一个无需额外配置、可直接放入 HTTP 请求头的默认 User-Agent 时使用:

```kotlin
val userAgent: String = ClientInfoUtils.getDefaultUserAgent(context)
```

返回格式为 `<package>/<version> Android/<release> API/<sdk>`, 例如
`com.example.app/1.2.0 Android/16 API/36`. 动态字段会清理为 RFC 9110 允许的 ASCII token 并限制长度;
版本名不可用时自动回退到版本号或 `unknown`. 默认值有意不包含设备 ID、厂商、品牌和型号. 服务端协议明确要求其它字段时,
由调用方基于原始 getter 自行组合, 并评估隐私和兼容性影响.

## 14. 窗口信息

需要获取当前 Activity 的完整窗口边界时使用:

```kotlin
val bounds: Rect = WindowUtils.getCurrentWindowBounds(activity)
val windowWidth: Int = bounds.width()
val windowHeight: Int = bounds.height()
```

该 bounds 使用 px, 包含状态栏和导航栏所在区域, 不会扣除任何 WindowInsets, 因而不能直接解释为安全内容区域.
该结果是调用时基于系统最近一次上报窗口状态生成的快照. 旋转、分屏比例调整、自由窗口缩放或其它窗口配置变化后,
调用方必须重新查询, 不得把返回的 `Rect` 作为进程级固定窗口状态长期缓存.
API 29 及以上结果由 AndroidX Window 保证正确; API 24 至 28 为 best-effort 兼容, 多窗口或无法可靠补偿导航栏时可能不精确.
需要避让状态栏、导航栏、IME 或 display cutout 时, 应在目标 View 上通过 `ViewCompat.setOnApplyWindowInsetsListener()`
实时读取 `WindowInsetsCompat`; `WindowUtils` 不提供静态的“可用内容尺寸”.

需要在命令式逻辑中读取当前 Context 的资源方向时使用:

```kotlin
val isLandscape: Boolean = WindowUtils.isLandscape(context)
val isPortrait: Boolean = WindowUtils.isPortrait(context)
```

结果表示传入 Context 当前资源配置的方向, 不表示设备的自然方向或物理旋转角度. 配置方向未定义或为方形时,
两个方法都会返回 `false`. 布局适配仍应优先使用 Android 的 `land` 资源限定符和响应式布局, 不依赖运行时分支复制资源系统能力.

## 15. 窗口 Insets padding

Activity 启用 edge-to-edge 后, 在新建 View 上安装一次通用 padding 扩展:

```kotlin
import com.whisper.kit.extension.applyWindowInsetsPadding

root.applyWindowInsetsPadding()
```

默认避让系统栏和刘海, 保留 XML 中的初始 padding. 通过当前 View 的实时 Insets 更新, 不读取静态屏幕尺寸.
底部由导航组件处理时使用 `root.applyWindowInsetsPadding(bottom = false)`, 其余方向同样可显式关闭.
左右参数表示物理边. 输入页面可以合并 IME, 键盘与导航栏取较大值而非相加:

```kotlin
root.applyWindowInsetsPadding(
    typeMask = WindowInsetsCompat.Type.systemBars() or
        WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
)
```

旧版系统的 IME 分发需要 Activity 使用 `adjustResize`. 可滚动页面仍应使用合适的滚动容器保证输入区域可达.
该扩展占用目标 View 的 Insets listener, 安装后不要覆盖 listener 或直接改其 padding. 重复安装复用首次保存的 padding
基线. 已处理区域从后续分发中扣除, 未处理边留给子 View; API 28 的同一刘海必须由单层完整处理, 不在嵌套 View 间分摊.
