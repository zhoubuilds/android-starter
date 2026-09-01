# Kit 开发文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                          |
|-----------------|---------|-----------------------------------|
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
   |  |  |- activity/
   |  |  |- extension/
   |  |  |- function/
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
      |- activity/
      |- function/
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
| `activity`                | 全局 Activity 生命周期跟踪工具      |
| `extension`               | 通用 Android 与 ViewBinding 扩展    |
| `function`                | 通用 Context 和 CharSequence 扩展   |
| `recyclerview.decoration` | RecyclerView item 间距和分割线工具  |
| `recyclerview.holder`     | RecyclerView ViewHolder 通用封装    |
| `recyclerview.listener`   | RecyclerView item 内点击分发工具    |
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
| AndroidX RecyclerView | `api(libs.androidx.recyclerview)`  | 点击分发公开 API 暴露 `RecyclerView` 类型 |
| AndroidX ViewBinding  | `api(libs.androidx.viewbinding)`   | ViewHolder 公开 API 暴露 `ViewBinding` 类型 |
| Material Components   | `implementation(libs.material)`    | 通用内容卡片内部使用 `ShapeableImageView`   |

## 4. Activity 生命周期跟踪

`ActivityLifecycleTracker` 是业务无关的进程级 Activity 观察工具. 应由 Application 在启动阶段调用
`ActivityLifecycleTracker.install(application)` 完成一次安装; 重复安装同一个 Application 保持幂等.

`ActivityLifecycleTracker.topActivity` 返回最近创建、启动或恢复且仍可用的 Activity. 工具使用弱引用持有页面, Activity 正在结束、已经销毁
或弱引用已释放时返回 null. Activity 进入 stopped 状态后仍属于当前任务栈, 因此在销毁前继续作为栈顶返回; 调用方执行 UI 操作时仍须切到主线程.

## 5. 通用扩展

`extension` 提供 Activity、Dialog 和 Fragment 的 ViewBinding 委托及其它通用 Android 扩展。当前 ViewBinding 实现从
Architecture 原样迁入, 其 API 和生命周期行为留待 Kit 专项检查。

`function` 提供不依赖应用资源和业务语义的 Android 通用扩展. CharSequence 富文本扩展包含绝对字号、相对字号、前景色、
字体样式、指定 Typeface、下划线、删除线和点击行为. 所有扩展应作用于整段文本、保留原文本已有的 Span 并支持链式组合.
`absoluteSize()` 明确接收 px; 需要遵循系统字体缩放的 sp 字号时, 调用方应传入由 sp dimension 解析得到的像素值.
`relativeSize()` 接收相对于 TextView 基准字号的正数比例. 点击扩展只处理点击和下划线, 不隐式覆盖颜色.
`TextView` 是否启用 `LinkMovementMethod` 仍由承载点击文本的组件或调用方负责.

## 6. 通用 View

`view` 提供业务无关的 View 和交互工具。当前包含 `KitContentFeedCardViewHolder`、`KitShareSheetDialog`、
`KitCodeInputEditText` 和 `KitRefreshLoadLayout`。

### 6.1 通用内容瀑布流卡片

`KitContentFeedCardViewHolder` 用于封面、标题、标签、统计文案、播放图标、时长和封面角标构成的通用内容卡片。
组件只处理视觉绑定、封面高度和点击分发; 业务内容类型、默认标签文案、跳转目标和图片加载库由调用模块提供。

维护要求:

1. `KitContentFeedCardUi` 只表达卡片 UI 状态, 不新增首页、生活、课程、商品等业务语义字段。
2. 图片加载必须通过 `KitContentFeedImageLoader` 回调接入, `kit` 不依赖 `foundation.loadMedia`、Glide 约定或应用图片占位规则。
3. 动态封面高度统一使用 `KitContentFeedCoverHeight`, 固定高度由调用方转换业务类型后写入 `fixedCoverHeightDp`。
4. 点击回调返回 `KitContentFeedCardUi`, 调用方可通过 `payload` 持有自己的原始业务 item; `kit` 不解析该对象。
5. 卡片颜色、圆角、间距和图标资源保持业务无关命名, 资源名遵守 `kit` 前缀。

### 6.2 通用分享底部面板

`KitShareSheetDialog` 用于展示底部分享入口面板。组件只处理窗口、标题、入口排列、取消按钮和点击分发; 分享渠道语义、入口文案、图标资源、
是否可用、实际 SDK 调用、埋点和业务提示由调用模块负责。

维护要求:

1. `ShareAction` 的 `id`、`title`、`iconRes` 和 `payload` 均为调用方数据, `kit` 不内置微信、朋友圈、复制链接、系统分享等渠道语义。
2. 面板不依赖 `foundation`、微信 OpenSDK、图片加载库或应用主题资源; 只能使用 `kit_` 前缀的基础色、背景和布局资源。
3. 面板点击后默认关闭, 需要等待业务操作结果时由调用方通过 `setDismissOnActionClick(false)` 接管关闭时机。
4. 导航栏、状态栏和 edge-to-edge 仍由宿主 Activity 负责; 分享面板只设置底部窗口展示和默认 dim 遮罩。

### 6.3 分格文本输入

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

### 6.4 刷新加载容器

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

## 7. RecyclerView Decoration

`recyclerview.decoration` 提供 RecyclerView item 间距和分割线装饰器。该工具只处理 item offset 和分割线绘制,
不承载业务视觉语义. 绘制边界和取舍见 [RecyclerView Decoration 边界](recyclerview-decoration-boundary.md).

维护要求:

1. 支持 `LinearLayoutManager` 和 `GridLayoutManager`。
2. 对 `RecyclerView.NO_POSITION` 做保护, 不把无效位置参与 span 或首尾计算。
3. `reverseLayout` 和 RTL 下主轴首尾间距和分割线位置必须保持一致.
4. `Start` / `End` 参数使用逻辑方向语义, 横向 RTL 或垂直 RTL 时必须映射到正确的物理边.
5. 网格交叉轴分割线只绘制在非首个 span 前, 避免在容器边界额外画线。
6. 分割线绘制位置应跟随 item translation, 避免 item 动画过程中分割线和 item 视觉位置错开。
7. 不在该工具中读取应用资源、主题色或业务尺寸。

## 8. RecyclerView ViewHolder

`recyclerview.holder` 提供 ViewBinding ViewHolder 通用封装。该工具只持有 binding, 不持有业务数据, 不处理业务点击事件。

维护要求:

1. 公开 API 使用 `ViewBindingInflater` 接收生成 binding 的 `inflate` 方法引用。
2. 不提供反射创建 binding 的入口。
3. 不让 ViewHolder 持有 item 数据或业务回调。
4. 只封装 ViewBinding 和 RecyclerView ViewHolder 的通用样板代码。

## 9. RecyclerView 点击分发

`recyclerview.listener` 提供 RecyclerView 级 item 内子 View 点击分发能力。该工具只处理 Android View 命中和手势通知, 不承载业务事件语义。

维护要求:

1. 保持 `OnItemClickListener` 为 `fun interface`, 让调用方可直接传入 lambda。
2. 扩展函数参数继续使用 `OnItemClickListener`, 保持公开 API 语义明确。
3. 默认只回调 `clickable && enabled` 的目标 View。
4. 没有命中可点击目标时不回调; 需要 item 空白区域响应时, 由调用方将 item 根 View 设置为 clickable。
5. 监听器是旁路通知工具, 不消费事件, 不改变原生 View 点击行为。
6. 命中顺序只承诺常规 child order + z/elevation, 不承诺自定义 `getChildDrawingOrder`。

后续增加长按、双击等能力时, 优先继承内部 `OnDispatchGestureListener` 复用命中检测逻辑, 不复制坐标转换算法。

## 10. 测试

修改 `kit` 后至少执行:

```bash
./gradlew :kit:testDebugUnitTest -q
./gradlew :kit:compileReleaseKotlin -q
git diff --check
```

公开扩展函数应保留编译型测试, 覆盖 lambda 调用形态。
