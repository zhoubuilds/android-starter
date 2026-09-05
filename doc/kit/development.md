# Kit 开发文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                          |
|-----------------|---------|-----------------------------------|
| 2026-09-05      | whisper | 明确客户端信息维护契约            |
| 2026-09-05      | whisper | 明确匿名设备标识维护契约          |
| 2026-09-04      | whisper | 明确点击与长按的退化语义和使用边界 |
| 2026-09-04      | whisper | 收敛 RecyclerView 手势 API 与兼容边界 |
| 2026-09-04      | whisper | 恢复 Divider 受约束继承关系 |
| 2026-09-04      | whisper | 明确 Decoration 构造与降级契约 |
| 2026-09-03      | whisper | 使用分段绘制兼容低版本 Canvas |
| 2026-09-03      | whisper | 明确 Decoration 单轴所有权与透明间距 |
| 2026-09-03      | whisper | 明确 Divider 裁剪与 Grid 查询复杂度 |
| 2026-09-03      | whisper | 明确 Staggered 主轴零尺寸限制 |
| 2026-09-03      | whisper | 明确 Decoration 失效时序与组合边界 |
| 2026-09-03      | whisper | 区分 Regular 与 Staggered Decoration |
| 2026-09-03      | whisper | 增加 Staggered 分割线装饰器      |
| 2026-09-03      | whisper | 收敛 Decoration 预测布局和 span 查询 |
| 2026-09-03      | whisper | 明确 Divider 动画取舍并限定 margin 轴 |
| 2026-09-02      | whisper | 统一 Grid Decoration 绘制与热路径契约 |
| 2026-09-02      | whisper | 增加 Staggered 列表间距契约       |
| 2026-09-02      | whisper | 统一主轴间距的 logical start 分配 |
| 2026-09-02      | whisper | 收紧 RecyclerView 间距职责与取整  |
| 2026-09-02      | whisper | 收敛组件域尺寸换算的 Context 依赖 |
| 2026-09-02      | whisper | 明确 Activity Context 判断边界    |
| 2026-09-02      | whisper | 移除 ViewBinding 无效泛型实化约束 |
| 2026-09-02      | whisper | 显式声明 Fragment 公开 API 依赖   |
| 2026-09-01      | whisper | 明确 ViewBinding 主线程访问契约   |
| 2026-09-01      | whisper | 补全 ViewBinding 委托生命周期     |
| 2026-09-01      | whisper | 收敛重复同类富文本 Span           |
| 2026-09-01      | whisper | 统一绝对与相对字号组合语义        |
| 2026-09-01      | whisper | 明确点击与下划线的设置顺序        |
| 2026-09-01      | whisper | 明确重复点击行为的覆盖规则        |
| 2026-09-01      | whisper | 统一富文本字体组合语义            |
| 2026-09-01      | whisper | 合并通用扩展包                    |
| 2026-09-01      | whisper | 移除全局 Activity 生命周期跟踪    |
| 2026-09-01      | whisper | 同步 Kit 当前源码结构和设计入口   |
| 2026-08-27      | whisper | 迁入通用 ViewBinding 扩展         |
| 2026-08-17      | 张梁    | 新增通用分享底部面板              |
| 2026-08-17      | whisper | 新增全局栈顶 Activity 跟踪工具    |
| 2026-08-17      | 张梁    | 新增通用内容瀑布流卡片            |
| 2026-08-14      | whisper | 明确分格输入样式显式引用规则      |
| 2026-08-14      | whisper | 补充分格输入默认样式入口          |
| 2026-08-14      | whisper | 新增分格文本输入控件              |
| 2026-08-13      | whisper | 补充 CharSequence 富文本扩展      |
| 2026-07-31      | whisper | 补充刷新加载动态头尾恢复语义      |
| 2026-07-31      | whisper | 补充刷新加载内容滚动边界          |
| 2026-07-31      | whisper | 调整刷新加载容器包路径            |
| 2026-07-30      | whisper | 补充滚动到底自动加载能力          |
| 2026-07-30      | whisper | 调整刷新加载触发距离默认语义      |
| 2026-07-30      | whisper | 调整刷新加载指示器为跟随滚动语义  |
| 2026-07-30      | whisper | 补充刷新加载容器工具              |
| 2026-07-30      | whisper | 补充 Decoration RTL 方向要求      |
| 2026-07-30      | whisper | 补充 Decoration 绘制边界文档      |
| 2026-07-30      | whisper | 补充 RecyclerView decoration 工具 |
| 2026-07-30      | whisper | 补充 ViewBinding ViewHolder 工具  |
| 2026-07-30      | whisper | 新增 Kit 模块维护说明             |

本文面向 `kit` 模块维护者, 描述通用工具包的维护边界、源码结构和测试方式. 设计目标和结构取舍见
[设计文档](design.md), 业务接入见 [使用文档](usage.md).

## 1. 模块定位

`kit` 是业务无关、应用语义无关的 Android 通用工具层。工具进入 `kit` 前必须同时满足:

1. 不依赖具体业务模块。
2. 不依赖 `foundation` 或应用公共状态模型。
3. 不引用应用资源、文案、主题约定、业务错误码或真实域名。
4. 不需要理解任意应用的当前业务语义也能正确使用。
5. 具备跨项目复用价值。

如果工具需要应用统一状态、图片加载约定、UI 消息、业务状态 typealias、资源或文案, 应留在 `foundation` 或对应业务模块。

## 2. 模块结构

```text
kit/
|- build.gradle.kts
`- src/
   |- main/
   |  |- java/com/whisper/kit/
   |  |  |- KitApplicationHolder.kt
   |  |  |- extension/
   |  |  |- recyclerview/
   |  |  |  |- decoration/
   |  |  |  |- holder/
   |  |  |  `- listener/
   |  |  |- utils/
   |  |  |- view/
   |  |  |  |- feed/
   |  |  |  |- input/
   |  |  |  |- refresh/
   |  |  |  `- share/
   |  |  `- widget/
   |  |- AndroidManifest.xml
   |  |- keepRules/rules.keep
   |  `- res/
   `- test/java/com/whisper/kit/
      |- extension/
      |- recyclerview/
      |  |- decoration/
      |  |- holder/
      |  `- listener/
      `- view/
         |- feed/
         |- input/
         `- refresh/
```

| 包                        | 职责                                |
|---------------------------|-------------------------------------|
| 根包                      | Application 等模块级 Android 工具   |
| `extension`               | 通用 Android, 文本和 ViewBinding 扩展 |
| `recyclerview.decoration` | RecyclerView item 间距和分割线工具  |
| `recyclerview.holder`     | RecyclerView ViewHolder 通用封装    |
| `recyclerview.listener`   | RecyclerView item 内点击与长按分发工具 |
| `utils`                   | 设备, 屏幕等 Android 平台工具       |
| `view.feed`               | 通用内容瀑布流卡片                  |
| `view.input`              | 分格文本输入控件                    |
| `view.refresh`            | 下拉刷新和上拉加载容器              |
| `view.share`              | 通用分享底部面板                    |
| `widget`                  | 通用基础自定义 View                 |

## 3. 依赖边界

`kit` 应保持轻量依赖。新增依赖前必须确认该依赖是工具自身公开 API 或实现所必需, 不因为某个业务模块刚好需要而加入。

当前依赖:

| 依赖                  | Gradle 声明                        | 暴露原因                                   |
|-----------------------|------------------------------------|--------------------------------------------|
| AndroidX AppCompat    | `api(libs.androidx.appcompat)`     | 分格输入控件公开 API 继承 AppCompatEditText |
| AndroidX Core KTX     | `api(libs.androidx.core.ktx)`      | 刷新加载容器公开 API 实现 NestedScrolling |
| AndroidX Fragment     | `api(libs.androidx.fragment)`      | ViewBinding 扩展公开 API 暴露 `Fragment` 类型 |
| AndroidX RecyclerView | `api(libs.androidx.recyclerview)`  | 点击分发公开 API 暴露 `RecyclerView` 类型 |
| AndroidX ViewBinding  | `api(libs.androidx.viewbinding)`   | ViewHolder 公开 API 暴露 `ViewBinding` 类型 |
| Material Components   | `implementation(libs.material)`    | 通用内容卡片内部使用 `ShapeableImageView`   |

## 4. 通用扩展

`extension` 提供 Context, CharSequence, Activity, Dialog 和 Fragment 等 Android 类型的通用扩展.
`Context.hasActivityContext()` 使用对象身份沿 ContextWrapper 包装链判断是否包含 Activity, 并防止异常包装链形成循环;
该结果不表示 Activity 仍处于可用生命周期状态或适合执行窗口操作.

`Number.dp` 和 `Number.sp` 使用 Kotlin 稳定的命名 context parameter 显式依赖当前 Context, 分别按当前显示密度和字体缩放
转换为保留亚像素精度的 px `Float`. 尺寸扩展不得读取全局 Application 或其它静态 Context; 整数像素的舍入策略由调用方根据
具体布局或文本语义决定. Fragment、View 和 Dialog 的同名 context property 只负责取得组件 Context. 全部公开尺寸扩展统一复用
私有换算桥接函数; Fragment 未附加 Context 时访问会按 `requireContext()` 契约失败. Kit 固定使用 Kotlin 2.4.0 及以上版本时
无需为 context parameter 添加实验性编译参数. Fragment、View、Dialog 类型互不构成重载优先级; 多个组件接收者嵌套时必须调用
`Number.dp(context)` 或 `Number.sp(context)` 并传入目标 Context, 不依赖编译器推断组件优先级.

Activity 和 Dialog 的 ViewBinding 委托使用 `LazyThreadSafetyMode.NONE` 在首次访问时调用生成类 `inflate()`
并按组件对象生命周期缓存,
不在 Activity `onDestroy()` 或 Dialog `dismiss()` 时主动清理, 也不隐式调用 `setContentView()`. Fragment 委托使用生成类
`bind()` 绑定已经创建的 View, 仅允许在 `onViewCreated()` 至
`onDestroyView()` 之间访问, View 销毁时自动清空, View 重建后重新创建 Binding. 仅通过 `onCreateDialog()` 创建内容且没有
Fragment View 的 DialogFragment 不使用 Fragment 委托; 应在 `onCreateDialog()` 内局部创建 Binding. 所有委托均不提供线程同步,
创建委托和访问 Binding 必须发生在主线程; 调用方应为委托属性添加 `@get:MainThread`, 由 Android Lint 检查访问线程. 委托不通过
反射查找生成类方法, 调用方应直接传入生成 Binding 类的 `inflate()` 或 `bind()` 方法引用.

CharSequence 富文本扩展包含绝对字号、相对字号、前景色、
字体样式、指定 Typeface、下划线、删除线和点击行为. 所有扩展应作用于整段文本、保留与本次设置不冲突的 Span 并支持链式组合.
同一目标范围内重复调用同类设置时只保留最后一个对应 Span, 不堆积重复 Span; 不同文本区域的 Span 互不影响.
`absoluteSize()` 明确接收 px; 需要遵循系统字体缩放的 sp 字号时, 调用方应传入由 sp dimension 解析得到的像素值.
`relativeSize()` 接收正数比例. 两者同时设置时, 先以 `absoluteSize()` 为基准, 再应用相对比例, 调用顺序不影响结果;
同一种字号设置重复调用时使用最后一次值. 点击扩展只处理点击和下划线, 不隐式覆盖颜色. 后一次
`onClick()` 只替换与其目标范围重叠的 `ClickableSpan`, 其回调和下划线配置共同生效; 同一文本中互不相交的点击区域同时保留.
`underline()` 与 `onClick(underline = false)` 设置同一范围时, 后调用者决定最终下划线状态, 点击回调不受影响.
`typeface()` 与 `textStyle()` 同时生效且不受调用顺序影响. 重复设置字体时后者覆盖前者; `BOLD` 和 `ITALIC`
同时设置时合并为 `BOLD_ITALIC`; `NORMAL` 显式重置已有字形. API 24 及以上使用同一组合算法, 但不承诺不同系统版本的
字体 fallback, 字形栅格和像素指标完全一致. 自定义字体 Span 只用于进程内渲染, 不承诺经过 Bundle, SavedState 或 IPC 后保留.
`TextView` 是否启用 `LinkMovementMethod` 仍由承载点击文本的组件或调用方负责.

## 5. 通用 View

`view` 提供业务无关的 View 和交互工具。当前包含 `KitContentFeedCardViewHolder`、`KitShareSheetDialog`、
`KitCodeInputEditText` 和 `KitRefreshLoadLayout`。

### 5.1 通用内容瀑布流卡片

`KitContentFeedCardViewHolder` 用于封面、标题、标签、统计文案、播放图标、时长和封面角标构成的通用内容卡片。
组件只处理视觉绑定、封面高度和点击分发; 业务内容类型、默认标签文案、跳转目标和图片加载库由调用模块提供。

维护要求:

1. `KitContentFeedCardUi` 只表达卡片 UI 状态, 不新增首页、生活、课程、商品等业务语义字段。
2. 图片加载必须通过 `KitContentFeedImageLoader` 回调接入, `kit` 不依赖 `foundation.loadMedia`、Glide 约定或应用图片占位规则。
3. 动态封面高度统一使用 `KitContentFeedCoverHeight`, 固定高度由调用方转换业务类型后写入 `fixedCoverHeightDp`。
4. 点击回调返回 `KitContentFeedCardUi`, 调用方可通过 `payload` 持有自己的原始业务 item; `kit` 不解析该对象。
5. 卡片颜色、圆角、间距和图标资源保持业务无关命名, 资源名遵守 `kit` 前缀。

### 5.2 通用分享底部面板

`KitShareSheetDialog` 用于展示底部分享入口面板。组件只处理窗口、标题、入口排列、取消按钮和点击分发; 分享渠道语义、入口文案、图标资源、
是否可用、实际 SDK 调用、埋点和业务提示由调用模块负责。

维护要求:

1. `ShareAction` 的 `id`、`title`、`iconRes` 和 `payload` 均为调用方数据, `kit` 不内置微信、朋友圈、复制链接、系统分享等渠道语义。
2. 面板不依赖 `foundation`、微信 OpenSDK、图片加载库或应用主题资源; 只能使用 `kit_` 前缀的基础色、背景和布局资源。
3. 面板点击后默认关闭, 需要等待业务操作结果时由调用方通过 `setDismissOnActionClick(false)` 接管关闭时机。
4. 导航栏、状态栏和 edge-to-edge 仍由宿主 Activity 负责; 分享面板只设置底部窗口展示和默认 dim 遮罩。

### 5.3 分格文本输入

`KitCodeInputEditText` 使用单个原生 `Editable` 和 `InputConnection` 承接输入, 只接管文本区域绘制。
控件应保留粘贴、删除、TextWatcher、Autofill、无障碍和状态恢复能力, 不在多个子 EditText 之间转移焦点。

维护要求:

1. XML 使用原生 `android:maxLength` 同时定义分格数量和最大输入字符数; 未配置时默认 6, 运行时通过 `codeLength` 修改时同步更新长度过滤器。
2. 已输入字符使用标准 `textColor`、`textSize` 和 `fontFamily`; 空位置重复绘制标准 `hint`, 并使用 `textColorHint` 和 `kitCodeHintFontFamily`。
3. 输入格背景接收 `Drawable`, 格子宽高优先使用显式配置, 未配置时依次使用 Drawable 固有尺寸和文本尺寸。
4. 控件由内容决定宽度时, 使用 `kitCodeItemSpacing` 计算期望宽度; 父布局限制最终宽度时, 忽略该期望间距并根据可用宽度、格宽和分格数量重新分配实际间距。
5. 父布局提供的宽度小于全部格子所需宽度时, 允许计算出负间距并继续绘制重叠格子, 不静默停止绘制。
6. 光标保持隐藏, 选区固定在文本末尾, 避免点击空白格后产生不可见的中间插入位置。
7. 控件不负责判断输入内容、发送验证码或触发提交; 完成时机由调用方监听文本决定。
8. 页面使用简短分格 hint 时, 应通过 `labelFor` 或等价无障碍信息提供输入字段语义。
9. 业务布局应显式引用应用提供的组件样式; `kitCodeInputEditTextStyle` 只用于遗漏显式 style 时兜底。kit 只定义样式入口和可配置属性,
   不持有应用颜色、尺寸或 Drawable。

### 5.4 刷新加载容器

`KitRefreshLoadLayout` 用于处理纵向内容的下拉刷新和上拉加载交互。

维护要求:

1. 只处理触摸、NestedScrolling、内容位移、header/footer 露出、刷新加载状态和回调。
2. 不持有业务列表数据, 不处理页码、接口请求、业务错误、空页面语义和没有更多数据语义。
3. 通过 `View.canScrollVertically()` 判断内容滚动边界, 不把实现绑定到某个业务列表。
4. 运行时 API 只提供启用刷新、启用加载、监听触发、完成刷新和完成加载能力, 不暴露业务 loading 状态同步入口。
5. header/footer 只作为普通子 View 布局, 不读取应用主题、应用资源或业务文案。
6. header/footer 子 View 角色必须通过 `kitLayoutRefreshLoadRole` 显式标记, 普通内容子 View 不需要标记。
7. 默认 header/footer 可通过 `kitRefreshLoadLayoutStyle` 配置, 显式声明的 child header/footer 优先于默认布局。
8. header/footer 各允许 0 或 1 个, 缺失时对应刷新或加载能力应自动禁用, 且在调用方尝试启用时输出警告日志。
   调用方启用意图应独立于当前有效值保存, 运行时补齐 header/footer 后应恢复对应能力。
9. 除 header/footer 外最多只能存在一个普通直接子 View, 多个内容子 View 应抛出异常。
10. header/footer 如需感知拖拽距离或状态变化, 应实现 `KitRefreshLoadComponent`, 容器不直接读取具体子 View 的文案或控件 ID。
11. 刷新中和加载中, header/footer 应作为 content 的附加部分跟随列表滚动, 包括触摸滚动和惯性滚动。
12. 刷新和加载触发距离使用负数作为哨兵值, 表示跟随对应 header/footer 自身高度。
13. 自动加载只通过通用 `View.canScrollVertically(1)` 判断内容是否到底, 不依赖 RecyclerView 或具体滚动容器类型。
14. 内容可以是不滚动 View 或支持 NestedScrolling 的滚动 View; 可滚动但不支持 NestedScrolling 的内容不保证连续边界衔接和自动加载触发。

`kit` 禁止依赖:

1. `foundation`。
2. `architecture`。
3. `feature/*`。
4. `app`。

## 6. RecyclerView Decoration

`recyclerview.decoration` 提供 RecyclerView item 间距和分割线装饰器。该工具只处理 item offset 和分割线绘制,
不承载业务视觉语义. 绘制边界和取舍见 [RecyclerView Decoration 边界](recyclerview-decoration-boundary.md).

维护要求:

1. Adapter 只负责 item 类型、创建、数据绑定和更新通知, 不通过 item 根 margin、占位 View 或分割线子 View 实现列表间距和分隔.
2. item 内部内容之间的 margin 仍由 item 布局负责; 只有跨 item 或 item 与 RecyclerView 边界的几何关系交给 Decoration.
3. `RegularItemSpaceDecoration` 和 `RegularItemDividerDecoration` 支持 `LinearLayoutManager`、`GridLayoutManager`;
   `StaggeredItemSpaceDecoration` 和 `StaggeredItemDividerDecoration` 只支持 `StaggeredGridLayoutManager`.
4. `RegularItemSpaceDecoration` 使用 `RecyclerView.State.itemCount` 判断当前布局首尾. predictive layout 中该数量与 Adapter
   当前数量不一致时, 首尾 span group 采用保守结果, 不使用旧 position 查询 Adapter 的新 `SpanSizeLookup`.
5. 对 `RecyclerView.NO_POSITION` 和超出当前布局状态的 position 做保护, 不把无效位置参与 span 或首尾计算。
6. `reverseLayout` 和 RTL 下主轴首尾间距和分割线位置必须保持一致.
7. `Start` / `End` 参数使用逻辑方向语义, 横向 RTL 或垂直 RTL 时必须映射到正确的物理边.
8. Grid 交叉轴分割线只启用当前已布局 item 实际使用的非边界 span 分隔位置, 避免在容器边界或
   未使用的 span 间额外画线.
9. 分割线通过 `onDraw` 在 item 之前绘制, 并使用当前 `RecyclerView.State`、child position 和 LayoutParams 判断归属,
   不保存逐 View 的历史归属. Adapter 更新动画期间允许短暂偏差. Decoration 不观察 Adapter 更新. 同步
   `notifyItem*` 会影响 position、itemCount、span 或首尾归属时, 调用方必须在通知前通过 AndroidX Core KTX 官方
   `doOnNextLayout` 注册回调, 在更新布局完成后调用 `RecyclerView.invalidateItemDecorations()`.
   `ListAdapter` 或 `AsyncListDiffer.submitList` 必须在实际提交列表的 commit callback 中注册同样的下一次布局回调;
   较早提交可能被后续提交取代且不执行 commit callback, 不能依赖较早回调完成失效. 两类路径都只承诺最终稳定布局的
   间距和归属正确.
   按 item 绘制的分割线继续跟随 item translation; Grid 连续 span 分割线固定在 LayoutManager 的 span 边界,
   并从连续线中排除与带 translation 的 item 相交的主轴切片.
10. `RegularItemSpaceDecoration` 的间距参数只接受非负 px; 不在该工具中读取应用资源、主题色或业务尺寸。
11. 网格交叉轴 offset 的 start 向上取整、end 向下取整, 相邻两侧之和必须严格等于目标间距; 取整结果只依赖 span 索引.
12. 主轴内部间距由后一个 item 或 span group 的 logical start offset 单边承担; logical end 只表达列表结束边界.
13. `StaggeredItemSpaceDecoration` 的普通 item 使用 LayoutManager 已分配的 span index 计算交叉轴 offset; full-span item
    的交叉轴 offset 为 0.
14. Staggered 主轴内部间距由所有非起始 item 的 logical start offset 承担. 需要区分 `startSpace` 与内部间距时,
    起始 item 由 Adapter 实现的 `StaggeredFullSpanProvider` 和起始处最多 `spanCount` 个 position 推导.
15. Adapter 实现 `StaggeredFullSpanProvider` 时, 绑定 item 必须复用 `isFullSpan(position)` 的查询结果设置
    `StaggeredGridLayoutManager.LayoutParams.isFullSpan`. 非预测布局中 Provider 与 LayoutParams 不一致时立即失败.
16. 主轴起始拓扑需要 Provider 时, `StaggeredFullSpanProvider` 必须由 RecyclerView 的直接 Adapter 实现并基于当前
    Adapter 数据稳定查询. 缺少时禁用主轴行为并保留交叉轴行为. `ConcatAdapter` 不能直接实现该接口,
    因而只能使用不依赖 Provider 的交叉轴行为或无需区分起始拓扑的主轴配置.
17. 运行时修改 LayoutManager 的 `orientation`、`reverseLayout`、`spanCount`, 替换 `SpanSizeLookup`,
    或修改 RecyclerView layout direction 后, 调用方必须立即调用 `invalidateItemDecorations()`,
    使下一次布局重算 RecyclerView 缓存的 decoration inset. 修改同一 `SpanSizeLookup` 实例的 span 规则时,
    必须先调用 `invalidateSpanIndexCache()` 和 `invalidateSpanGroupIndexCache()`, 再调用
    `RecyclerView.invalidateItemDecorations()`, 避免 LayoutManager 与 Decoration 使用不同的 span 拓扑.
18. `StaggeredItemSpaceDecoration` 不提供 `endSpace`: 瀑布流末端各 span 的结束位置可能不同, 不能仅由 Adapter
    position 静态判断统一的结束边界.
19. `RegularItemDividerDecoration` 和 `StaggeredItemDividerDecoration` 的分割线尺寸、margin 只接受非负 px; 构造参数同时使用
    `@IntRange` 和运行时校验.
    margin 参数名必须同时包含所属 divider 和应用方向: `mainAxisDividerCrossAxisStart/EndMargin` 或
    `crossAxisDividerMainAxisStart/EndMargin`; `Start/End` 是对应方向上的逻辑边.
20. Linear 主轴分割线由非首项 logical start 区域承载并跟随该 item 的 translation; Grid 主轴分割线由非首
    span group 的每个 item logical start 区域承载, 并沿 item 交叉轴范围分段绘制.
21. Grid 先在 span 间隙中沿主轴连续绘制交叉轴分割线, 再沿交叉轴分段绘制主轴分割线. 连续线的主轴范围只需
    覆盖当前已布局内容与 RecyclerView 可见内容区域的交集, 不依赖未加载 item.
22. Divider 的容器绘制边界必须遵循 `RecyclerView.clipToPadding`: `true` 时限制在 padding 内容区域,
    `false` 时使用 RecyclerView 的完整可见范围. divider margin 从对应范围的逻辑边向内缩进.
23. Grid Decoration 直接读取当前 item 已分配的 `GridLayoutManager.LayoutParams.spanIndex/spanSize`.
    `getItemOffsets` 和绘制热路径不得调用可能从 Adapter 起点扫描的 `getSpanIndex/getSpanGroupIndex`; 首末 span group
    判断的 `SpanSizeLookup` 查询次数必须只受 `spanCount` 限制, 不随 adapter position 或 itemCount 增长. 一轮
    Regular Grid 可见 item offset 计算的最坏成本为 `O(visibleChildCount + spanCount^2)`.
    连续分割线会对当前 child 的主轴区间排序, 并在每个 span 边界跳过与 child 相交的主轴切片; 一轮绘制最坏为
    `O(visibleChildCount * log(visibleChildCount) + spanCount * visibleChildCount + spanCount^2)`.
    两条路径都不随 adapter position 或 itemCount 增长. `endSpace` 为 0 时跳过末组判断; 非 0 时只允许
    Adapter 最后 `spanCount` 个 position 向后探测.
24. `RegularItemDividerDecoration` 继承 `RegularItemSpaceDecoration`, `StaggeredItemDividerDecoration` 继承
    `StaggeredItemSpaceDecoration`, 直接复用对应 offset 规则并增加分割线绘制. 两个 SpaceDecoration 的
    `getItemOffsets` 必须保持 `final`, 子类不得改写间距取整、Provider 或布局拓扑语义.
25. Staggered 交叉轴分割线沿当前已布局内容的主轴包络连续绘制, 并排除与 item 相交的主轴切片;
    主轴分割线在所有非起始 item 的 logical start 间距中分段绘制.
26. Staggered 分割线绘制只允许查询当前可见 item 和 Adapter 起始处最多 `spanCount` 个 full-span position, 不得从
    Adapter 起点扫描到任意当前 position. 连续分割线的可见 child 处理与 Regular 采用相同分段算法,
    最坏复杂度为 `O(visibleChildCount * log(visibleChildCount) + spanCount * visibleChildCount)`.
    main-axis end margin 只缩进绘制包络, 不构造 `endSpace`.
27. Staggered Decoration 不支持 decorated main-axis measurement 为 0 的 item. 每个 item 包含
    decoration inset 和 LayoutParams margin 后的主轴占用尺寸必须大于 0;
    否则 LayoutManager 不推进 span 端点, 无法仅通过 position 和 full-span 拓扑稳定判断起始归属.
28. 同一个 RecyclerView 的同一轴最多只能有一个内部间距非零的 Decoration. 内部间距均为 0、只设置主轴
    `startSpace` / `endSpace` 的边界 SpaceDecoration 可以共存. 不得通过扫描或缓存其它 ItemDecoration 的方式推导
    当前 Decoration 的绘制切片.
29. Divider size 始终参与对应轴的 offset 计算. Drawable 为 `null` 时只保留透明间距且不绘制, 用于在单个
    DividerDecoration 中组合一个轴的分割线和另一个轴的空白间距.
30. 两个 DividerDecoration 都必须提供双轴独立尺寸和 Drawable、全部 margin 为 0 的次构造器, 让常见的双轴配置无需
    经过八参数高级构造器. 便捷构造器无法表达非零 margin 与双轴独立尺寸或 Drawable 的组合时,
    使用八参数高级构造器并通过命名实参调用.
31. Decoration 遇到不支持的非空 LayoutManager 时必须清空 offset、跳过绘制并按实例最多记录一次警告, 不得使应用崩溃.
    Staggered 缺少主轴起始拓扑所需的 `StaggeredFullSpanProvider` 时, 只禁用主轴 offset 和分割线, 交叉轴继续读取实际
    LayoutParams. Adapter 已实现 Provider 时, 非预测布局中的查询结果必须与 `LayoutParams.isFullSpan` 一致, 否则立即失败.

## 7. RecyclerView ViewHolder

`recyclerview.holder` 提供 ViewBinding ViewHolder 通用封装。该工具只持有 binding, 不持有业务数据, 不处理业务点击事件。

维护要求:

1. 公开 API 使用 `ViewBindingInflater` 接收生成 binding 的 `inflate` 方法引用。
2. 不提供反射创建 binding 的入口。
3. 不让 ViewHolder 持有 item 数据或业务回调。
4. 只封装 ViewBinding 和 RecyclerView ViewHolder 的通用样板代码。

## 8. RecyclerView 点击与长按分发

`recyclerview.listener` 提供 RecyclerView 级 item 内子 View 点击和长按分发能力. 该工具只处理 Android View 命中和手势通知,
不承载业务事件语义.

维护要求:

1. 保持 `OnItemClickListener` 和 `OnItemLongClickListener` 为 `fun interface`, 让调用方可直接传入 lambda.
2. 点击与长按扩展函数分别使用对应接口, 保持公开 API 语义明确; 两种回调都不返回触摸消费结果.
3. 点击只回调 `clickable && enabled` 的目标 View, 长按只回调 `longClickable && enabled` 的目标 View.
4. 没有命中目标时不回调; 需要 item 空白区域响应时, 由调用方在 item 根 View 设置对应的 clickable 或 longClickable.
5. 监听器是完全无侵入的旁路通知工具: 不向 item 或子 View 安装监听器、AccessibilityDelegate 或状态,
   不修改 View 属性, 不消费、合成或重新分发 MotionEvent, 不改变原生 View 点击行为.
6. 核心命中算法必须逐层处理 parent scroll、child left/top 和 child matrix 逆变换, 支持平移、缩放、旋转、pivot、
   rotationX/rotationY 及嵌套变换; 不可逆矩阵按未命中处理.
7. 命中顺序只承诺常规 child order + z/elevation, 不承诺自定义 `getChildDrawingOrder`.
8. 被过滤但通过 `clickable`、`longClickable` 或 `contextClickable` 标志仍会处理标准指针事件的前景 View 必须阻断
   后方兄弟节点, 不得把禁用或非当前手势目标的控件区域穿透给下层目标.
9. 监听器只观察经过 RecyclerView 的 MotionEvent. 无障碍、键盘和代码直接调用 `performClick()` / `performLongClick()` 不在
   观察范围内; 需要这些输入方式的业务操作必须继续使用 View 原生点击和无障碍链路.
10. 单击目标必须在 ACTION_DOWN 时锁定. MOVE 只允许沿原目标父链验证当前矩阵和点击边界, 不得重新扫描兄弟树;
    目标离开后当前手势永久取消. 滚动取消只检查 LayoutManager 支持的滚动轴, 并按各轴位移分别与 RecyclerView
    touch slop 比较, 不得使用二维合成距离取消交叉轴移动. ACTION_UP 只验证原目标的归属、过滤条件和当前位置,
    不得重新选择目标.
11. DOWN item 和目标允许使用实例级强引用保存, 但必须在 UP、CANCEL、多指、滚动和下一次 DOWN 主动清理;
    外部回调前先清理状态, 避免异常或重入延长 View 生命周期. 收到
    `requestDisallowInterceptTouchEvent(true)` 时必须清理目标, 因为 RecyclerView 级旁路监听器此后不保证继续收到完整事件序列;
    `false` 不改变当前目标.
12. 快速双击必须按两个独立的普通点击处理, 不启用 GestureDetector 双击识别, 不延迟单击确认. 点击监听器收到长按超时后,
    在该时点采样锁定目标: 满足 `longClickable && enabled` 时必须立即清理且当前手势不可恢复; 否则保留目标并在
    ACTION_UP 补充分发. 补充分发仍必须执行 clickable、enabled、父链、命中边界、adapter position、窗口挂载和焦点校验,
    不得因为超时时曾经有效而跳过最终校验. 超时后的属性变化不得重新执行长按占用判断.
    该判断不依赖是否安装 `ItemLongClickTouchListener`, 只表达基于公开 View 标志的低成本退化, 不得解释为原生
    `performLongClick()` 的 Boolean 消费结果.
13. 命中和遮挡只依据公开的标准 View 触摸标志. 不通过反射读取 OnTouchListener 或 TouchDelegate, 不试探性调用
    `dispatchTouchEvent()`; 自定义消费结果和 TouchDelegate 扩展区域明确不在该无侵入监听器的命中模型内.
14. 长按使用平台 GestureDetector 超时并在超时成立时回调锁定目标. `OnItemLongClickListener` 只表达旁路通知,
    不得模拟或代理原生 OnLongClickListener 的 Boolean 消费语义.
15. 点击和长按回调的位置参数是 RecyclerView 完整 Adapter 链中的 absolute adapter position. `ConcatAdapter` 调用方
    需要子 Adapter 位置时, 应使用自己的 Adapter/数据映射关系转换, 不得把该参数解释为 binding adapter position.
16. 公开监听器构造器接收的 RecyclerView 必须与实际注册监听器的 RecyclerView 是同一实例; 直接构造时由调用方保证,
    推荐优先使用扩展函数创建并注册监听器.
17. 监听器只保证实际收到完整 DOWN 至 UP/CANCEL 事件序列时的识别. DOWN 后通过
    `removeOnItemTouchListener()` 移除监听器, 或由其它消费型 `OnItemTouchListener` 中途接管时, AndroidX 不会补发
    CANCEL, 已排队的识别任务也不会因移除动作自动取消. 这些用法不在支持范围内; 监听器只能在当前手势结束后移除,
    且不得与可能中途消费同一事件流的监听器组合使用.
18. 外部回调前必须验证 RecyclerView、DOWN item 和目标 View 仍附着到窗口, 并验证 RecyclerView 仍持有窗口焦点;
    任一条件不满足时静默结束当前手势, 不得向已经离开活动窗口的界面发送点击或长按通知.

点击长按协调测试必须至少覆盖 clickable-only 长按后点击、longClickable 长按后抑制点击、超时采样前属性变化和
ACTION_UP 最终校验. 不通过测试访问私有状态验证实现细节, 应从公开回调结果验证契约.

如果未来提供双击能力, 必须使用独立的显式 API, 不得静默改变现有点击 API 的两次普通点击语义.

## 9. 匿名设备标识

`DeviceIdUtils` 提供无需额外权限的应用侧匿名标识. 新安装首次调用时生成 UUID, 写入应用私有
`kit_device_id` SharedPreferences, 后续在同一进程内缓存并在应用数据仍然存在时复用. 已由旧版本写入的非空值必须继续复用,
不得因生成策略调整主动替换存量标识.

该标识不读取 `ANDROID_ID` 或其它硬件、系统账号标识, 不承诺绑定物理设备. 清除应用数据、卸载后未恢复数据、存储失败、
多进程首次并发生成或宿主应用的备份恢复策略都可能改变其生命周期; 系统备份也可能把已有值恢复到另一设备. 实现只保证
同一进程内并发调用返回相同值, 持久化采用 SharedPreferences 的异步 `apply()` 语义, 不以阻塞调用线程换取进程异常退出时的
落盘保证. SharedPreferences 获取或 `apply()` 调用同步失败时, 当前进程继续返回同一个缓存值; 后续调用使用该值重试持久化,
并通过原子状态保证同一时刻只有一个线程执行补写. 存量值读取失败视为没有可复用值, 使用新 UUID 尝试覆盖异常数据.
`apply()` 正常返回即视为异步写入已经调度, 不推断无法观察的磁盘结果.

进程缓存只维护一个由 UUID 和持久化状态组成的 `DeviceIdState`, 不以 Context 引用区分应用环境. Android 应用进程只服务当前
宿主应用; Application Context 仅用于当前调用的存储访问, 不进入长期缓存或参与缓存切换.

该值只适合作为验证码请求控频、滥用检测等服务端策略的弱信号. 不得将它作为账号身份、认证凭据、授权依据或唯一控频维度;
服务端应结合手机号、账号、IP、请求行为或平台完整性信号形成最终策略.

## 10. 客户端与应用信息

`ClientInfoUtils` 统一提供 Android 客户端和宿主应用的基础运行环境信息. 它不提供设备标识, 屏幕尺寸或窗口状态:
匿名应用侧标识由 `DeviceIdUtils` 负责, 显示与窗口信息由 `ScreenUtils` 负责. 该边界避免一个宽泛的设备信息入口混合不同生命周期,
隐私属性和 Android API 来源.

`sdkInt`, `osRelease`, `manufacturer`, `brand` 和 `model` 必须直接返回对应 `Build` 字段原值, 不在 getter 中执行 trim,
大小写转换、拼接或缺省替换. `getPackageName()`, `getAppVersionName()` 和 `getAppVersionCode()` 同样保留 Context 与
PackageManager 返回的原始语义. 应用包不存在时版本名和版本号返回 `null`; 其它运行时异常不得被宽泛捕获和静默隐藏.
版本号在 API 28 及以上读取 `PackageInfo.longVersionCode`, 在 API 24 至 27 将 `versionCode` 转换为 `Long`.

`getAppLocales()` 返回传入 Context 当前资源配置中的 LocaleList 原值, 不把它解释为应用显式选择的全局语言.
`getPrimaryAppLocale()` 只提供方便读取首项的派生结果; 异常空列表回退到进程默认 Locale. Kit 最低支持 API 24,
统一读取 `Configuration.locales`.

`getDefaultUserAgent()` 生成符合 RFC 9110 product 语法的默认值:
`<package>/<version> Android/<release> API/<sdk>`. 版本名为空时依次回退到版本号和 `unknown`. 该方法只在格式化结果中把
连续的非 ASCII token 字符替换为 `-`, 并将每个动态 token 限制为 128 个字符; 不得反向改变原始 getter. 默认值不加入匿名设备标识,
厂商、品牌或型号, 避免为普通网络请求增加不必要的设备指纹信息. 具体服务端协议需要其它字段时应由调用方自行组合.

## 11. 测试

修改 `kit` 后至少执行:

```bash
./gradlew :kit:testDebugUnitTest -q
./gradlew :kit:compileReleaseKotlin -q
git diff --check
```

公开扩展函数应保留编译型测试, 覆盖 lambda 调用形态。
