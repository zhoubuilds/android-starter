# Kit 使用文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                          |
|-----------------|---------|-----------------------------------|
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

## 4. 通用分享底部面板

需要展示底部分享渠道选择时, 使用 `KitShareSheetDialog`。`kit` 只提供面板和入口点击分发, 调用方负责提供渠道文案、图标和实际分享动作:

```kotlin
KitShareSheetDialog.Builder(context)
    .setTitle("分享到")
    .addAction(
        KitShareSheetDialog.ShareAction(
            id = "wechat_session",
            title = "微信好友",
            iconRes = R.drawable.ic_wechat,
        ),
    )
    .setOnActionClickListener { _, action ->
        when (action.id) {
            "wechat_session" -> shareToWechatSession()
        }
    }
    .show()
```

分享面板默认在入口点击后关闭。需要先等待权限、网络、截图或 SDK 调用结果时, 可以设置 `setDismissOnActionClick(false)`,
并在业务完成后自行关闭或保留面板。分享渠道不可用时, 调用方可以把 `ShareAction.enabled` 设为 `false`;
不可用原因和提示文案仍由业务模块处理。

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

需要保留标准 EditText 输入能力并按格展示字符时, 使用 `KitCodeInputEditText`:

```xml
<com.whisper.kit.view.input.KitCodeInputEditText
    android:id="@+id/codeInput"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:autofillHints="smsOTPCode"
    android:background="@android:color/transparent"
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

分格数量使用原生 `android:maxLength`, 未配置时默认 6。`kitCodeItemBackground` 可以传入 shape、selector
或其它 Drawable 来统一控制格子背景。`wrap_content` 按格子宽高、数量和 `kitCodeItemSpacing` 计算期望尺寸;
`match_parent`、固定宽度或父布局上限压缩最终宽度时, `kitCodeItemSpacing` 不参与绘制, 控件会按最终可用宽度和分格数量重新分配间距。
空间不足以容纳全部固定宽度格子时会显示为格子重叠, 便于直接发现不合理的布局约束。

应用应为业务布局显式指定组件样式, 并可通过主题的 `kitCodeInputEditTextStyle` 提供漏写 `style` 时的默认兜底。
直接依赖 kit 且没有应用组件样式时, 调用方仍需像上例一样显式提供背景、格子尺寸和文本样式;
应用级颜色和 Drawable 不应下沉到 kit。

控件会隐藏光标并把选区保持在文本末尾。页面使用 `-` 这类非描述性 hint 时, 应使用 `android:labelFor`
将字段标题与输入控件关联, 避免损失无障碍语义。

## 7. 刷新加载容器

需要下拉刷新和上拉加载的纵向内容可以使用 `KitRefreshLoadLayout` 包裹。容器只负责交互和回调,
具体刷新、加载、点赞、删除等业务状态仍由页面 ViewModel 维护。header/footer 子 View 角色必须通过
`app:kitLayoutRefreshLoadRole` 显式声明, 普通内容子 View 不需要声明角色。

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
<com.whisper.kit.view.refresh.KitRefreshLoadLayout
    android:id="@+id/refreshLoadLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</com.whisper.kit.view.refresh.KitRefreshLoadLayout>
```

单个页面需要特殊 header 或 footer 时, 可以显式声明对应 child 覆盖默认布局。只声明 header 时,
footer 仍会继续使用主题中的默认配置:

```xml
<com.whisper.kit.view.refresh.KitRefreshLoadLayout
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
</com.whisper.kit.view.refresh.KitRefreshLoadLayout>
```

header/footer 各允许 0 或 1 个。缺少 header 时下拉刷新会自动禁用, 缺少 footer 时上拉加载会自动禁用。
如果调用方仍将对应开关设置为 `true`, 控件当前有效值会保持禁用并输出警告日志。该设置意图会被保留,
后续运行时补充对应 header/footer 或通过默认样式补齐后, 控件会恢复对应能力。

内容 View 的滚动能力决定边界衔接效果。普通 `LinearLayout` 等不滚动 View 可以直接通过手势拉出 header/footer。
`RecyclerView`、`NestedScrollView` 等支持 NestedScrolling 的内容可以在滚到顶部或底部后连续把剩余滚动交给容器。
可滚动但不支持 NestedScrolling 的自定义 View 可能需要松手后重新拖拽才能拉出 header/footer, 也可能无法触发滚动到底自动加载。

`kitRefreshLoadRefreshTriggerDistance` 和 `kitRefreshLoadLoadMoreTriggerDistance` 默认值为 `-1`。
在 XML 样式中配置负数哨兵值时仍按 dimension 写法保留单位, 例如 `-1dp`。所有负数值都表示触发距离分别使用 header/footer 自身高度。
只有需要提前或延后触发时, 才配置具体 dimension 值。

刷新中或加载中, header/footer 会作为 content 的附加部分跟随列表滚动。用户继续滚动 RecyclerView 或列表进入惯性滚动时,
header/footer 会像列表条目一样逐步离开屏幕。

启用 `kitRefreshLoadAutoLoadMoreEnabled` 或运行时设置 `autoLoadMoreEnabled = true` 后,
内容向下滚动到底部时会自动进入加载更多状态, 并触发和手动上拉相同的 `RefreshLoadAction.LoadMore` 回调。
自动加载只依赖内容 View 的 `canScrollVertically(1)`, 不绑定 RecyclerView 或具体 LayoutManager。
如果需要没有更多数据时停止自动触发, 页面应在 ViewModel 状态中将 `loadMoreEnabled` 设置为 `false`。

`kitRefreshLoadDragResistance` 是基础拖拽阻尼。实际阻尼会随 header/footer 拉出距离增加, 越接近最大拖拽距离阻力越大。
回弹和完成收起动画使用减速曲线, 越接近目标位置速度越慢。

header/footer View 如需根据拖拽状态更新文案或动画, 可以实现 `KitRefreshLoadComponent`:

```kotlin
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

页面监听用户手势, 再转交给 ViewModel:

```kotlin
binding.refreshLoadLayout.setOnRefreshLoadListener { action ->
    when (action) {
        KitRefreshLoadLayout.RefreshLoadAction.Refresh -> viewModel.refresh()
        KitRefreshLoadLayout.RefreshLoadAction.LoadMore -> viewModel.loadMore()
    }
}
```

页面收集 ViewModel 状态后同步列表, 并在刷新或加载请求结束时收起 header/footer:

```kotlin
adapter.submitList(state.items)
binding.refreshLoadLayout.loadMoreEnabled = state.hasMore
binding.refreshLoadLayout.autoLoadMoreEnabled = state.autoLoadMore

if (!state.refreshing) {
    binding.refreshLoadLayout.finishRefresh()
}
if (!state.loadingMore) {
    binding.refreshLoadLayout.finishLoadMore()
}
```

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

## 9. RecyclerView ViewHolder

ViewBinding item 可以使用 `ViewBindingHolder` 减少 ViewHolder 样板代码:

```kotlin
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewBindingHolder<ItemUserBinding> =
    ViewBindingHolder.create(parent, ItemUserBinding::inflate)

override fun onBindViewHolder(holder: ViewBindingHolder<ItemUserBinding>, position: Int) {
    holder.binding.name.text = items[position].name
}
```

该工具只持有 binding, 不持有 item 数据, 不处理业务点击事件。点击事件可按需搭配 RecyclerView 点击分发工具统一处理。

## 10. RecyclerView 点击与长按分发

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

## 11. 匿名设备标识

需要为验证码请求控频等场景提供一个无需额外权限的弱标识时, 可以使用:

```kotlin
val deviceId: String = DeviceIdUtils.getDeviceId(context)
```

该值由应用首次生成并保存在私有 SharedPreferences 中, 在应用数据仍然存在时尽量保持稳定. 它不读取 `ANDROID_ID`,
不绑定设备或硬件, 也不保证在清除数据、卸载、备份恢复、存储异常或多进程竞争后保持不变. 宿主应用允许系统备份时,
已有值可能随备份恢复到另一设备. 如果首次调用时 SharedPreferences 暂时不可用, 后续调用会保持当前进程中的同一个值并尝试补写.

调用方可以把它作为服务端风控输入之一, 但不能用于身份认证、授权或唯一控频依据. 验证码接口仍应在服务端结合手机号、账号、
IP、请求行为或平台完整性信号进行限制, 并按适用的隐私规则处理和保留该标识.

## 12. 客户端与应用信息

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
