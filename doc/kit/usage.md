# Kit 使用文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                          |
|-----------------|---------|-----------------------------------|
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

## 2. ViewBinding 扩展

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

## 3. 通用分享底部面板

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

## 4. 富文本扩展

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

## 5. 分格文本输入

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

## 6. 刷新加载容器

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

## 7. RecyclerView Decoration

线性或网格列表可以使用 `ItemSpaceDecoration` 设置 item 间距:

```kotlin
recyclerView.addItemDecoration(
    ItemSpaceDecoration(
        mainAxisSpace = 16,
        crossAxisSpace = 12,
        startSpace = 16,
        endSpace = 16,
    )
)
```

需要绘制分割线时使用 `ItemDividerDecoration`:

```kotlin
recyclerView.addItemDecoration(
    ItemDividerDecoration(
        dividerSize = 1,
        dividerMargin = 16,
        dividerColor = Color.LTGRAY,
    )
)
```

Decoration 只接收 px 值, 不负责 dp 转换、主题色读取或业务尺寸选择. 分割线绘制边界见
[RecyclerView Decoration 边界](recyclerview-decoration-boundary.md).

## 8. RecyclerView ViewHolder

ViewBinding item 可以使用 `ViewBindingHolder` 减少 ViewHolder 样板代码:

```kotlin
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewBindingHolder<ItemUserBinding> =
    ViewBindingHolder.create(parent, ItemUserBinding::inflate)

override fun onBindViewHolder(holder: ViewBindingHolder<ItemUserBinding>, position: Int) {
    holder.binding.name.text = items[position].name
}
```

该工具只持有 binding, 不持有 item 数据, 不处理业务点击事件。点击事件可按需搭配 RecyclerView 点击分发工具统一处理。

## 9. RecyclerView 点击分发

RecyclerView item 内子 View 点击可以通过统一触摸监听分发, 避免在每个 ViewHolder 中分散绑定点击逻辑:

```kotlin
recyclerView.addOnItemChildClickListener { recyclerView, child, position ->
    when (child.id) {
        R.id.avatar -> ...
        R.id.delete -> ...
    }
}
```

该工具只回调 `clickable && enabled` 的命中目标, 不消费事件, 不改变子 View 原本的触摸和点击行为。需要让 item 空白区域响应点击时,
应将 item 根 View 设置为 clickable。

返回值是已经添加到 RecyclerView 的触摸监听器, 需要移除时使用:

```kotlin
val listener = recyclerView.addOnItemChildClickListener { _, _, _ -> }

recyclerView.removeOnItemTouchListener(listener)
```
