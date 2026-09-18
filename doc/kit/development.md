# Kit 开发文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                          |
|-----------------|---------|-----------------------------------|
| 2026-09-17      | whisper | 增加实时 Insets padding、剩余分发与低版本刘海处理 |
| 2026-09-11      | whisper | 校准刷新组件外观来源及布局快照比较基准 |
| 2026-09-11      | whisper | 补齐刷新组件布局补发去重及实例快照生命周期规则 |
| 2026-09-11      | whisper | 补齐刷新边界重入、有效距离布局同步及 margin 外包尺寸规则 |
| 2026-09-11      | whisper | 补齐 GONE 入口检查、阻尼有限值校验及参数注解规则 |
| 2026-09-11      | whisper | 补齐取消单次清理、组件重新布局及阈值更新的重入维护规则 |
| 2026-09-11      | whisper | 补齐刷新加载统一边界、无障碍及业务显示同步维护规则 |
| 2026-09-11      | whisper | 补齐 Parent1 桥接、独立滚动轴记录及原生滚动验证规则 |
| 2026-09-11      | whisper | 补齐刷新加载状态机、嵌套滚动及动态组件维护规则 |
| 2026-09-11      | whisper | 同步范围选择和刷新加载子包及测试结构 |
| 2026-09-11      | whisper | 补充范围焦点初始化和无障碍父坐标的兼容维护规则 |
| 2026-09-11      | whisper | 补齐范围起点下限的统一约束与手动禁用前缀示例 |
| 2026-09-11      | whisper | 统一原 Drawable 状态管理并补齐同值赋值锚点回归 |
| 2026-09-11      | whisper | 补充范围选择控件的手动测试入口 |
| 2026-09-10      | whisper | 补齐单触点取消与组合 Drawable 尺寸保留规则 |
| 2026-09-10      | whisper | 补齐范围抬起采样、原生按键监听与 Drawable 尺寸保留规则 |
| 2026-09-10      | whisper | 明确范围事件、整数无障碍、键盘、状态快照与 RTL 维护规则 |
| 2026-09-09      | whisper | 明确最近合法范围投影和滑块继承、清空及共享维护规则 |
| 2026-09-09      | whisper | 补齐整数范围、双指跟踪与滑块 Drawable 维护契约 |
| 2026-09-09      | whisper | 补充标准分割线与 KitLineView 的职责边界 |
| 2026-09-09      | whisper | 补齐线型变化时的原生路径缓存失效规则 |
| 2026-09-09      | whisper | 补充线条低版本绘制兼容与路径更新回归 |
| 2026-09-09      | whisper | 补充线条真实窗口像素回归验证 |
| 2026-09-09      | whisper | 补齐线条运行时属性、默认实线与状态色规则 |
| 2026-09-09      | whisper | 补齐线条方向、测量与亚像素绘制契约 |
| 2026-09-09      | whisper | 补齐取消拖拽、菜单创建期与方向变化维护规则 |
| 2026-09-09      | whisper | 补齐触摸焦点、长按粘贴与图标背景绘制契约 |
| 2026-09-08      | whisper | 收敛短码无障碍节点和文本菜单契约 |
| 2026-09-08      | whisper | 移除分格输入的 codeLength 兼容 API |
| 2026-09-08      | whisper | 明确分格宽度上限与单元测试 SDK 基线 |
| 2026-09-08      | whisper | 修正分格输入长度与绘制契约并补充设备验证 |
| 2026-09-08      | whisper | 合并 widget 包并移除内容卡片和分享面板 |
| 2026-09-05      | whisper | 标记工具类 Aegis 保护范围          |
| 2026-09-05      | whisper | 收敛窗口边界与方向查询契约        |
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
   |  |  `- widget/
   |  |     |- KitCodeInputEditText.kt
   |  |     |- KitLineView.kt
   |  |     |- rangeselection/
   |  |     |  |- KitRangeBar.kt
   |  |     |  |- KitRangeSelection.kt
   |  |     |  `- KitRangeAccessibilityHelper.kt
   |  |     `- refreshloadlayout/
   |  |        |- KitRefreshLoadComponent.kt
   |  |        `- KitRefreshLoadLayout.kt
   |  |- AndroidManifest.xml
   |  |- keepRules/rules.keep
   |  `- res/
   |- test/java/com/whisper/kit/
   |  |- extension/
   |  |- recyclerview/
   |  |  |- decoration/
   |  |  |- holder/
   |  |  `- listener/
   |  `- widget/
   |     |- rangeselection/
   |     `- refreshloadlayout/
   `- androidTest/java/com/whisper/kit/
      `- widget/
         |- rangeselection/
         `- refreshloadlayout/
```

| 包                        | 职责                                |
|---------------------------|-------------------------------------|
| 根包                      | Application 等模块级 Android 工具   |
| `extension`               | 通用 Android, 文本和 ViewBinding 扩展 |
| `recyclerview.decoration` | RecyclerView item 间距和分割线工具  |
| `recyclerview.holder`     | RecyclerView ViewHolder 通用封装    |
| `recyclerview.listener`   | RecyclerView item 内点击与长按分发工具 |
| `utils`                   | 客户端标识, 窗口等 Android 平台工具 |
| `widget`                  | 线条绘制和分格输入等独立基础控件 |
| `widget.rangeselection`   | 整数范围选择控件及其约束计算、无障碍辅助类型 |
| `widget.refreshloadlayout` | 刷新加载容器及其头尾组件回调契约 |

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
| AndroidX Window       | `implementation(libs.androidx.window)` | 兼容计算 API 24 以上当前窗口 bounds |

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

### 4.1 窗口 Insets padding

`View.applyWindowInsetsPadding()` 在目标 View 上安装唯一 Insets listener, 默认处理 `systemBars | displayCutout`.
调用方按页面选择物理边和 `typeMask`; 输入页面显式加入 `ime`, 首页可用 `bottom = false` 将底部留给导航组件.
四边均以首次安装时的 padding 为基线; 私有 View tag 保存基线, 重复分发或安装不得累加. 安装后不要再由其它代码修改
同一 View 的 padding 或覆盖 listener. 安装后在 View 附着时请求分发, 不持有任何全局窗口或 Activity 状态.

多种 Insets 在同一边取最大值, 不累加 IME 与导航栏高度. 返回值扣除实际应用的 Insets, 不扣除页面自身的初始 padding,
未处理边继续交给子 View. API 28-29 读取当前分发的 `displayCutout` 安全区, 避免 AndroidX 的按类型查询回读根窗口.
API 28 的 `inset()` 不扣减刘海, 完整避让后使用该版本稳定的 `consumeDisplayCutout()` 消费, 局部抑制其废弃提示;
API 28 不支持把同一刘海的不同边分给多个嵌套 View, 应由同一层完整处理. API 29 起由平台完成逐边扣减.

窗口 edge-to-edge、IME 的 `adjustResize`、系统栏外观和具体页面的滚动策略仍由宿主配置. 本扩展不修改
`WindowUtils`、Architecture Activity 生命周期或应用主题. 测试覆盖初始 padding、重复安装、父子剩余分发、
刘海、IME 显示与隐藏以及物理左右边的 RTL 语义.

## 5. 基础控件

`widget` 及其子包统一提供业务无关的基础控件与回调契约, 不再维护语义重叠的 `view` 包.
`KitLineView` 和 `KitCodeInputEditText` 保留在根包. `KitRangeBar` 与仅服务该控件的 `KitRangeSelection`、
`KitRangeAccessibilityHelper` 位于 `widget.rangeselection`; `KitRefreshLoadLayout` 与
`KitRefreshLoadComponent` 位于 `widget.refreshloadlayout`. 辅助类型与所属控件就近维护, 不移入通用工具包,
不因包拆分扩大可见性. 对应单元测试保持相同包路径, 范围设备测试与专用 Activity 同样位于 `widget.rangeselection`;
跨控件手动测试入口 `KitCodeInputManualActivity` 保留在 `widget` 根包.

控件应围绕独立的绘制, 输入或交互能力维护, 应用样式和内容组合由调用方负责. 内容卡片和分享面板固化整套展示结构,
不作为 Kit 的公共控件提供; 实际使用它们的应用应在自己的业务模块或 `foundation` 中维护相应界面和资源.

### 5.1 分格文本输入

`KitCodeInputEditText` 使用单个原生 `Editable` 和 `InputConnection` 承接输入, 保留粘贴, 删除, TextWatcher, Autofill
和状态恢复能力, 不在多个子 EditText 之间转移焦点. 控件面向数字或 ASCII 字母组成的短验证码, 允许字符由调用方通过
原生 `android:digits` 或 `filters` 限制. 不承诺复杂文字排版, 自由选区或无障碍逐字导航.

维护要求:

1. XML 或样式的原生 `android:maxLength` 同时定义初始分格数量和 UTF-16 长度上限, 未配置时默认 6;
   不把 TextView 单行模式自动安装的 5000 位限制当作控件默认值. 运行时 `setFilters()` 保留调用方的其它过滤器,
   使用最小 `LengthFilter.max` 作为格数, 没有长度过滤器时补充 6 位限制. 非正长度必须在修改现有状态前拒绝.
   不再提供额外的长度属性或 setter. 缩短长度时在原 `Editable` 上截断, 不割裂截断边界上的有效代理对.
2. 测量应建立并维护原生 `Layout`; 绘制保留经过裁剪的父类调用以维持 Editor 与 IME 同步, 分格正文从 `layout.text`
   读取转换后的文本, 不绕过密码等原生转换行为. 绘制画笔复制原生文本画笔以保留缩放与字形样式;
   空位置使用标准 `hint`, `textColorHint` 和 `kitCodeHintFontFamily`, hint 字体也必须参与自适应宽高测量.
   父类绘制使用空裁剪区时, 必须独立绘制当前 `compoundDrawables`, 包括 `setError()` 的图标及原生 RTL 解析结果.
   测量和格子内容区域使用 compound padding, 期望尺寸还应容纳左右图标高度与上下图标宽度, 避免图标与格子重叠或被裁切.
3. 输入格背景接收 `Drawable`, 格子宽高优先使用显式配置, 未配置时依次使用 Drawable 固有尺寸和文本尺寸.
   同一个背景实例使用固定局部 bounds 和 Canvas 平移绘制各格, 不为每个位置重复改写 bounds.
   背景回调必须失效全部格子, selector 状态变化导致固有尺寸改变时请求重新布局.
   更新背景布局方向后同样检查固有尺寸, 尺寸变化时请求重新布局并刷新绘制.
   背景可见性通过 `onVisibilityAggregated()` 同步, 替换或 detach 时将旧背景设为不可见并取消调度;
   不主动调用动画的 `start()` / `stop()`, 不改变调用方的播放意图.
   Ripple 热点使用最近格子的局部坐标, 并限制在该格宽高内. 所有格子仍共享背景状态和热点, 不提供逐格独立交互.
4. 控件由内容决定宽度时, 使用 `kitCodeItemSpacing` 计算期望宽度, 并遵循原生像素 min/max 尺寸约束;
   父布局指定精确宽度, 或父布局上限, 控件自身 `maxWidth` / `maxEms` 将宽度压缩到期望值以下时,
   忽略该期望间距并根据可用宽度, 格宽和分格数量重新分配实际间距, 包括 `UNSPECIFIED` 下自身上限生效的情况.
5. 最终可用宽度小于全部格子所需宽度时, 允许计算出负间距并继续绘制重叠格子, 不静默停止绘制.
6. 光标保持隐藏, 选区固定在文本末尾, 避免点击空白格后产生不可见的中间插入位置。
7. 控件不负责判断输入内容、发送验证码或触发提交; 完成时机由调用方监听文本决定。
8. 页面使用简短分格 hint 时, 应通过 `labelFor` 或等价无障碍信息提供输入字段语义。
9. 业务布局应显式引用应用提供的组件样式; `kitCodeInputEditTextStyle` 只用于遗漏显式 style 时兜底。kit 只定义样式入口和可配置属性,
   不持有应用颜色、尺寸或 Drawable。
   自定义样式未提供触摸焦点配置时, 可聚焦的输入框默认支持触摸获焦; XML 或样式显式配置的
   `android:focusableInTouchMode="false"` 和 `android:focusable="false"` 必须保留, 不引入原生 EditText 样式改变外观.
10. 分格沿用 UTF-16 长度单位, 有效代理对必须联合绘制且占两个格子; 不将其计为一个格子, 不承诺 grapheme 分格.
    RTL 页面中的短码仍从左向右排列, 控件及周边布局继续继承 RTL, 不强制整个 View 的布局方向为 LTR.
11. 无障碍节点保留 editable, label, text, hint, password 和焦点等原生字段语义, `maxTextLength` 使用最小长度过滤器上限.
    移除并拒绝 `ACTION_SET_SELECTION`, `ACTION_NEXT_AT_MOVEMENT_GRANULARITY` 和
    `ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY`, 将 movement granularities 设为 0; API 33 及以上将 `isTextSelectable` 设为 `false`.
    API 26 及以上不得声明或返回 `EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY`, 避免把连续文本 Layout 的坐标作为分格坐标提供.
    `ACTION_SET_TEXT` 设置和清空整段文本, `ACTION_PASTE` 及 IME 末尾删除继续走原生编辑与过滤链路.
12. 默认插入和选区 ActionMode 以及右键菜单不提供 `selectAll`, `onTextContextMenuItem()` 必须拒绝该操作.
    调用方替换原生 ActionMode callback 时也应保留不提供全选的规则, 不为该限制新增组件专属配置 API.
    默认 `OnLongClickListener` 通过公开的 `ActionMode.TYPE_FLOATING` 展示系统粘贴菜单项, 并调用
    `customInsertionActionModeCallback`; 未被 callback 消费的粘贴交给 `onTextContextMenuItem()` 进入原生 Editable 和过滤链路.
    返回键, 再次点击输入框, 失去输入焦点, 聚合可见性变为不可见或 detach 时关闭菜单. 再次触摸在 `ACTION_DOWN` 关闭;
    返回键兼容旧版 `onKeyPreIme()` 与 API 33 及以上的 `OnBackInvokedCallback`, 菜单销毁时注销已注册的返回回调.
    创建回调同步结束菜单时不得将返回的失效模式写入字段或注册返回回调, 后续长按仍可重新创建菜单.
    调用方设置自己的 `OnLongClickListener` 会按原生语义替换默认入口, 应自行处理长按或接入自己的操作流程.

### 5.2 刷新加载容器

`KitRefreshLoadLayout` 用于处理纵向内容的下拉刷新和上拉加载交互。

维护要求:

1. 只处理触摸、NestedScrolling、无障碍动作、内容位移、header/footer 露出、刷新加载显示状态和回调.
   容器直接处理的触摸序列只有 `ACTION_UP` 按对应方向阈值触发. 在原生事件分发入口记录 `ACTION_CANCEL`,
   保证直接拖拽与 AndroidX NestedScrolling 的触摸停止均保留取消原因. 取消时清理指针和拖拽状态,
   尚未进入刷新或加载时回弹至 0, 已在进行中的任务保持原状态, 不重复通知业务回调.
   已进入原生事件分发的触摸序列, 嵌套停止延迟到 `ACTION_UP` 后结算; 子 View 内部收到取消时的停止不能代表正常释放.
   完成后的回弹维持 `Settling` 到动画结束, 动画帧不重新计算拖拽状态; 旧会话的停止不能再次提交,
   新触摸手势可以取消回弹并接管当前位置, 首帧未产生位移也必须转回拖拽状态以保证松手正常收尾.
   触摸与非触摸滚动分别记录旧会话是否失效, 新的非触摸滚动会话不依赖手指 DOWN 恢复响应.
   CANCEL 的直接处理和外层分发共用一次清理, 回调新建任务后旧事件不得取消新动画.
   原生 DOWN 及独立嵌套触摸会话开始时停止指示器旧惯性, 不改变忙碌状态.
2. 不持有业务列表数据, 不处理页码、接口请求、业务错误、空页面语义和没有更多数据语义。
3. 默认通过当前滚动目标的 `View.canScrollVertically()` 判断内容滚动边界, 不把实现绑定到某个业务列表.
   `ScrollBoundaryCallback` 同时覆盖上下边界, `direction = -1` 表示向顶部, `1` 表示向底部;
   `setScrollBoundaryCallback(null)` 恢复默认判定. 回调收到真实嵌套滚动目标, 直接拖拽时收到普通内容子 View.
   拖拽、自动加载及忙碌期间的 pre-scroll、post-scroll 和惯性衔接必须共用此判定, 不保留仅覆盖顶部的旧 API.
   边界回调返回后校验状态及交互仍有效, 回调完成或切换任务、终止旧会话后, 不得继续按旧状态移动指示器或提交动作.
   反向拖拽越过零点时重新检查另一侧开关及内容边界, 不沿用起始方向的许可. 阻尼计算保留不足一个像素的余量,
   新手势、活动指针接管和取消时重置余量, 避免慢速拖动停滞或把旧手势残量带入新操作.
   `dragResistance` 的程序及 XML 赋值共用有限值校验, `NaN` 或正负无穷抛出 `IllegalArgumentException`,
   校验失败保留旧配置; 小于 1 的有限值仍归一化为 1. 范围和像素单位注解明确标在 getter 与 setter 参数上,
   供 IDE / Android Lint 静态检查, 不替代运行时校验, 也不改变距离参数的负数哨兵语义.
4. 运行时 API 提供交互开关、边界判定、动作监听、完成及业务显示同步能力. `setActiveAction(action, animate = false)`
   只同步显示, 不触发 `OnRefreshLoadListener`; `Refresh` / `LoadMore` 对应忙碌状态, null 表示收起.
   相同忙碌状态保留当前位置和动画; null 在 `Idle` / `Settling` 时不重复执行, 但必须收起未提交拖拽.
   切换状态清理旧手势与滚动交接, 防止旧事件随后再次提交. 未布局或组件缺失、`GONE` 时保留待定位动作,
   在布局及组件尺寸可用后定位, 同步更新头尾组件; 等待期间若状态变化, 不得执行过期定位.
   同步显示允许容器或方向交互已禁用, 不隐式启用交互. 此语义不改变关闭方向开关时结束对应显示的既有行为.
5. header/footer 作为普通子 View 布局, 外观由调用方提供的 View 或样式指定的布局资源决定,
   容器不内置应用专用主题、资源或业务文案.
   内容遵守 `FrameLayout.LayoutParams.gravity`、padding、margin 及 RTL 语义; header/footer 的纵向位置由角色固定,
   横向位置使用相同的 gravity 和 RTL 规则.
   转换已有 `FrameLayout.LayoutParams` 时保留 gravity 和 margin, 转换本控件 LayoutParams 时额外保留角色.
   header 的隐藏底边位于内容上边界减 bottomMargin, footer 的隐藏顶边位于内容下边界加 topMargin.
   忙碌展开距离使用组件自身高度与上下 margin 的总和, 总和非正时沿用触发距离兜底; 求和避免 Int 溢出.
   非负 margin 的外包区域在空闲时位于内容边界外; 负 margin 保留 Android 的重叠布局语义, 不隐式归零或拒绝.
6. header/footer 子 View 角色必须通过 `kitLayoutRefreshLoadRole` 显式标记, 普通内容子 View 不需要标记。
7. 默认 header/footer 可通过 `kitRefreshLoadLayoutStyle` 配置, 显式声明的 child header/footer 优先于默认布局。
8. header/footer 各允许 0 或 1 个, 缺失时对应刷新或加载能力应自动禁用, 且在调用方尝试启用时输出警告日志。
   调用方启用意图应独立于当前有效值保存, 运行时补齐 header/footer 后应恢复对应能力。
   对应组件为 `GONE` 时禁止该方向的直接拖拽、嵌套拖拽、自动加载及无障碍动作, 各入口实时检查;
   松手提交和组件回调返回后也重新检查, 防止途中隐藏后继续发出请求. 恢复非 `GONE` 后按现有开关重新允许操作.
   可见性不改写方向开关, 不取消已经发起的业务任务, 不限制程序显示同步; `INVISIBLE` 保留现有能力.
   未提交拖拽仍可收起已有位移, 不再继续拉出隐藏方向; 忙碌指示器停止滚动消费和惯性, 保留任务状态.
9. 除 header/footer 外最多只能存在一个普通直接子 View, 多个内容子 View 应抛出异常。
10. header/footer 如需感知拖拽距离或状态变化, 应实现 `KitRefreshLoadComponent`, 容器不直接读取具体子 View 的文案或控件 ID。
    运行时新增、替换或默认补齐的组件在尺寸可用后同步当前状态及所属方向位移, 不依赖下一次拖动或状态变化初始化.
    组件回调可能同步完成任务、禁用或替换组件, 回调返回后重新确认操作仍有效; 已失效的动作不能继续展开或通知业务,
    已过期的状态和位移也不能继续广播给其它组件.
    位移跨零切换时, 退出方向也接收归零通知. 忙碌方向由当前任务状态决定, 切换动画尚未跨零时旧方向完成回调
    或禁用操作不得结束新的反向任务.
    首次状态通知与待同步位移分开记录. 状态或位移回调请求重新布局时, 保留位移同步需求,
    等组件完成测量及布局后再补当前快照, 不重复发送已初始化的同一状态.
    布局补发按组件实例比较上次实际交付的宽高、角色、位移及有效触发和展开距离; 相同快照清除待同步标记,
    不再回调, 并允许继续初始定位. 普通位移及有效距离通知保留原有时机.
    中间布局的尺寸若未交付位移快照, 随后恢复上次已交付尺寸和数据时同样不补发, 不以中间测量结果作为比较基准.
    在回调前记录快照, 防止外层通知覆盖重入产生的新记录; 移除组件或待同步角色变为普通内容时清理记录,
    同一实例重新加入后仍需完成首次状态和位移同步.
    ViewRoot 布局期间产生的组件同步及初始定位延后到布局结束执行, 合并重复任务并在 detach 时撤销;
    这类可修改布局的组件回调应在 onLayout 返回后执行, 避免其 requestLayout 被框架延后或过滤而漏测新尺寸.
    初始忙碌定位必须在首次状态回调及其引起的尺寸调整之后执行; 布局期间新增的组件等待后续布局,
    不得收到零尺寸或尚未布局的首次位移快照.
11. 刷新中和加载中, header/footer 应作为 content 的附加部分跟随列表滚动, 包括触摸滚动和惯性滚动。
    NestedScrolling post-scroll 中实际移动指示器时, 按本次真实位移累计 `consumed[1]`, 不漏报或重复报告消费量.
12. 刷新和加载触发距离使用负数作为哨兵值, 表示跟随对应 header/footer 自身高度; 0 会归一化为 1 px。
    正位移只比较刷新阈值, 负位移只比较加载阈值, 不以位移绝对值跨方向提交动作.
    运行时有效阈值变化后立即重算拖拽阶段并同步组件距离, 不主动提交动作或修改忙碌、收起阶段及动画.
    同值或等效阈值赋值不重复通知. 参数回调重入时按版本停止过期广播, 保证两端最终收到当前距离;
    阈值变化未改变拖拽阶段时不递增状态版本, 避免截断外层正在发送的状态通知.
    跟踪已同步的两端有效触发及展开距离, 重新布局后比较并同步; 即使组件未实现回调接口, 高度变化也要更新拖拽阶段.
    尺寸引起的同步同样在 ViewRoot 布局结束后执行, 回调前记录当前距离以免同尺寸重复布局持续通知;
    回调再次请求布局时等待后续尺寸就绪, 不在 onLayout 中直接调用可修改布局的组件回调.
    setter 即时同步与布局同步共用距离快照, 等效配置或相同尺寸不重复广播; busy / Settling 的位置及现有动画保持不变.
13. 自动加载使用统一的向底部滚动能力判定, 默认是 `View.canScrollVertically(1)`, 不依赖 RecyclerView 或具体滚动容器类型.
14. 内容可以是不滚动 View 或支持 NestedScrolling 的滚动 View; 可滚动但不支持 NestedScrolling 的内容不保证连续边界衔接和自动加载触发。
    不带 `type` 的 Parent1 入口委托 `TYPE_TOUCH` 实现, 不另建刷新状态机. 使用 `NestedScrollingParentHelper`
    分别记录触摸与非触摸滚动轴, 停止一种会话不能清除另一种, detach 清空两种记录.
    旧版会话结束且没有新协议非触摸会话接管时清除待交接速度, 防止独立后续滚动继承旧速度.
    原生 `ScrollView` 由调用方显式开启嵌套滚动; 容器不修改子 View 的配置. 原生惯性及程序滚动不发送嵌套增量,
    不通过监听私有滚动器或猜测滚动类型模拟 AndroidX 的非触摸协议.
15. `isEnabled = false` 或 detach 时清理未提交拖拽、触点、嵌套触摸会话和回弹, 收起残留位移并回到空闲状态.
    已发起的 `Refreshing` / `LoadingMore` 保留任务状态, 不重复通知业务, 仍通过对应完成 API 结束;
    该规则不替代调用方的请求生命周期管理.
16. 无障碍节点在 `Idle`、容器启用、对应方向启用及组件可用时提供刷新或加载更多动作, 执行时再次检查可用条件.
    显式动作不要求内容到达边界, 不滚动内容, 经由正常状态机通知业务. 忙碌时提供状态描述,
    状态和可用性变化时更新无障碍节点, 不在每个拖拽位移帧播报. 保留子节点、调用方 delegate 及显式内容/状态描述,
    不截获列表的键盘方向键. 应用可用普通按钮提供键盘操作入口.
17. 不通过 `SavedState` 自动保存刷新或加载任务, 不恢复活动触点及动画. 页面根据 ViewModel 的真实任务重新调用
    `setActiveAction()`, 恢复显示不能重新发出请求. 进程重建后的请求重启由业务决定.

`kit` 禁止依赖:

1. `foundation`。
2. `architecture`。
3. `feature/*`。
4. `app`。

### 5.3 线条绘制

`KitLineView` 使用 `android:orientation` 指定方向. `HORIZONTAL` 和 `VERTICAL` 直接引用
`LinearLayout.HORIZONTAL = 0` 和 `LinearLayout.VERTICAL = 1`, 默认水平; 构造器的 `Context` 必须非空.
样式入口为 `kitLineViewStyle`; 运行时通过 `orientation`, `dotWidth`, `dashWidth`, `gapWidth` 和 `lineColors`
配置对应属性, `setLineColor(@ColorInt Int)` 提供纯色设置入口.

维护时不要为静态实线场景重复扩展 `KitLineView` 的能力. 调用方已接入 Material Components 时可使用
`MaterialDivider`; 无 Material 依赖时使用带背景色和固定厚度的普通 `View`. `KitLineView` 负责虚线、点划线、
横竖方向统一配置、状态色和运行时线型变化等超出基础分割线的能力.

维护要求:

1. `kitDotWidth`, `kitDashWidth` 和 `kitGapWidth` 分别表示沿线方向的点长, 划长和间隔, 使用 `getDimension()`
   读取为 px `Float`, 保留亚像素精度. 三者均默认 0; XML 与运行时赋值均将负数归一化为 0,
   运行时 setter 对 `NaN` 或无穷值抛出 `IllegalArgumentException`, 不修改已有值.
2. 间隔为 0 时绘制实线, 包括点长和划长均为 0 的默认配置. 间隔为正时, 两段均为 0 则不绘制,
   只有一段为正则重复该段, 两段均为正则交替绘制点和划. 不在构建 `DashPathEffect` 前将正数尺寸截断为整数.
   线型属性改变后重建绘制效果并重绘, 不改变线条粗细或请求重新布局.
3. 沿线方向使用 `getDefaultSize()` 测量, 父布局提供 `EXACTLY` 或 `AT_MOST` 时填满其可用尺寸.
   交叉轴期望尺寸为 `max(两侧 padding 总和 + 1px, suggestedMinimum)`;
   `suggestedMinimum` 包含 View 最小尺寸和背景最小尺寸. 使用 `resolveSizeAndState()` 遵守精确尺寸和父布局上限.
4. 绘制区域按实际布局后的 `width` 和 `height` 扣除 padding 计算; 内容宽度或高度不大于 0 时直接结束绘制,
   避免零粗细触发 Canvas hairline, 或反向线段进入 padding 区域. 线条粗细等于内容区域的交叉轴尺寸.
5. `android:color` 使用 `getColorStateList()` 保留 selector. 未配置时读取主题的 `android:textColorSecondary`
   `ColorStateList`, 主题未提供时使用 `ColorStateList.valueOf(Color.GRAY)`; 显式 XML 或样式颜色优先.
   `lineColors` 为非空 `ColorStateList`, 替换后立即请求重绘; `setLineColor()` 用纯色列表替换现有状态列表.
6. 线色按 View 自身的 `drawableState` 解析, 状态变化请求重绘, 不自动开启 `clickable` 或 `focusable`.
   生命周期回调需兼容父类构造期间的调用, 避免在自有颜色或画笔字段初始化前访问它们.
7. `orientation` 只接受 SDK 定义的 0 和 1, 非法值抛出 `IllegalArgumentException`, 不修改已有值.
   方向改变后调用 `requestLayout()` 和 `invalidate()`, 不交换 `LayoutParams.width` 与 `height`.
8. 实线使用 `drawLine()`, 带 `DashPathEffect` 的线型使用 `drawPath()`, 兼容旧版 HWUI 不支持在线段上应用
   `PathEffect` 的限制, 不修改 View 的 `layerType`. 复用单个 `Path`, 在实际内容边界, 方向或线型变化后清空并重建,
   避免残留旧线段. 旧版 HWUI 的纹理缓存键包含 effect 原生地址和 Path 版本; 线型改变时必须使路径版本更新,
   防止旧 effect 回收后地址复用命中旧图案. 实线或空线型期间保留路径失效标记, 下次绘制点划线时再重建;
   仅颜色变化或重复赋相同线型值时继续复用路径.

### 5.4 整数范围选择

`KitRangeBar` 使用 `kitRangeBarStyle` 样式入口, 只使用整数数值域. 维护时遵守以下规则:

1. `start`, `end`, 绘制和回调使用同一份有序且对齐步长的状态. XML 和 `setSelectedRange()` 先对原始端点排序,
   再联合选择满足数值域、端点限制和最小间隔的最近范围. 不提前夹值或分别吸附, 避免丢失距离信息.
   单端赋值固定另一端, 在该端合法刻度内选择最近值; 不通过显示时排序掩盖交叉状态.
2. XML `kitStep` 必须大于 0, 非法值抛出 `IllegalArgumentException`. 步长从 0 起算, 拖动仅产生整数倍刻度,
   程序和 XML 赋值同样强制对齐; `total` 不能整除步长时不额外生成终点刻度.
   触摸坐标在入口取整为 px, 数值映射、步长舍入、端点限制与最小间隔计算使用 `Long`, 防止溢出及大整数精度丢失.
3. `THUMB_UNLIMITED=-1` 是端点限制的唯一无限制标记, 0 是有效限制. `total` 归一化到至少 1,
   XML 及直接设置 `startMin` / `startMax` / `endMin` 时将参数归一化到 `-1..total`, `minSpan` 归一化到非负值.
   `startMin` 为包含边界的起点下限, 对应 XML `kitStartMin` 和 `setStartMin()` / `getStartMin()`;
   起点合法刻度从该下限向上取整, 不重新定义步长原点, 终点通过不可交叉与最小间隔规则同样不能低于该下限.
   `setTotal()` 保留已经生效的 `startMin`、`startMax`、`endMin` 和 `minSpan`, 不按新上界重写或放宽它们.
   限制或 `total` 实际改变时, 先求出新约束下已有选区的最近范围, 成功后一次更新配置和选区并重建触点锚点;
   即使选区不变也更新约束相关信息. 归一化后配置相同则保留活动锚点.
   无可行范围时抛出 `IllegalArgumentException`, 不改变任何旧配置、选区或活动锚点.
   联合投影最小化两端平方位移之和, 等距时依次选择较小的起点和终点; 通过凸目标的一阶差分二分查找,
   使用整数运算并保持 `O(log(刻度数))` 复杂度, 不遍历全部刻度或直接计算可能溢出的平方和.
   拖动先求合法区间的首末刻度; 轨道长度为 0 时保持原值, 不对空区间调用 `coerceIn()`.
4. 触点以 `getPointerId()` 标识, 不持久化 pointer index. 每个滑块独立维护触点、数值和坐标锚点,
   保留双指同时拖动以及已命中触点等待接管的能力. 重合仲裁使用记录的 DOWN 坐标和后续方向,
   不依赖 MOVE 历史样本; 捕获目标后保持该端点身份, 接管被释放的滑块时重新建立锚点.
   正常 `ACTION_UP` 和 `ACTION_POINTER_UP` 先应用当前事件中活动触点的最终坐标, 再释放或接管滑块.
   Android 13 及以上的 `ACTION_POINTER_UP` 携带 `FLAG_CANCELED` 时只释放 action index 对应的触点,
   不使用该触点的新坐标或回滚已有值; 其余触点继续采样和拖动, 等待触点仍可重新锚定并接管空出的滑块.
   `ACTION_CANCEL` 清理全部跟踪, 不使用取消事件坐标追加数值变化.
5. 只有命中可操作滑块才消费初始触摸并阻止父容器拦截. 松手、取消、禁用、不可见和脱离窗口时清理跟踪状态;
   数值域、约束、程序范围或布局几何实际改变时重建活动触点锚点, 避免沿用失效映射.
   普通单端或双端赋值归一化后选区相同则直接返回, 不重建锚点或丢弃步长吸附前的像素余量;
   保证范围回调内原样回写与不回写时的同一拖动轨迹结果一致.
6. `setOnRangeChangedListener()` 只接收 `OnRangeChangeListener.onRangeChanged(start, end)`, 仅最终合法选区变化时通知.
   单端和双端赋值、数值域或约束更新、触摸、键盘、无障碍及状态恢复遵守同一规则; 配置变化但端点相同不通知.
   同一个 MOVE、UP 或 POINTER_UP 内完成两个端点的调整后再比较和通知, 不暴露双指更新的中间范围.
   正常结束通知必须使用已应用最终坐标的选区. 移除 `release` 和立即通知重载,
   注册监听器本身不回调, 读取初始值使用 `start` / `end`.
   `setOnInteractionListener()` 独立接收 `OnInteractionListener`, `onInteractionStart(start, end)`,
   `onInteractionEnd(start, end)` 和 `onInteractionCancel(start, end)` 均提供默认空实现.
   会话固定使用开始时的交互监听器, 运行时替换只影响后续会话, 避免事件成对通知被拆给不同监听器.
   触摸从有效 DOWN 到最终 UP 是一个会话, 增减手指数或接管端点不拆分会话; 键盘一次调节键 DOWN 到 UP,
   包含 repeat, 是一个会话; 每次有效无障碍调节动作独立形成会话. 程序赋值和恢复不发交互事件.
   单触点取消不通知交互结束或取消, 最终正常 UP 才结束当前会话;
   整次 CANCEL、禁用、不可见或脱离窗口结束活动会话时只通知取消, 保留实时值, 不再补发正常结束.
7. `kitThumb` / `setThumb()` 提供通用外观, `kitStartThumb` / `setStartThumb()` 与
   `kitEndThumb` / `setEndThumb()` 提供独立覆盖. `null` 清空该端, `INHERIT_THUMB` 才恢复通用外观.
   XML 独立属性省略或使用 `inherit=-1` 时继承, `none=0` 或 `@empty` 时清空; 不依赖 `@null` 区分缺省和显式清空.
   哨兵只用于配置解析, 不参与绘制或生命周期. `setThumb(null)` 不改变已有的独立覆盖.
   程序 setter 直接管理调用方传入的原实例, 不调用 `newDrawable()` 或 `mutate()`;
   `mutate()` 也可能重建组合 Drawable 的子项, 导致已经配置的子项透明度、滤镜或目标密度丢失.
   仅 XML 资源加载时调用 `mutate()` 隔离共享的 `ConstantState`, 此时尚无调用方的运行时实例配置.
   程序从资源加载外观时, 调用方先 `getDrawable(...).mutate()`, 再设置 alpha、滤镜、tint 或密度等配置,
   最后交给控件; 代码直接新建的独占实例不需要 `mutate()`.
   不通过根级 alpha、colorFilter 或 tint setter 重新应用外观, 避免透明度重复衰减或覆盖组合图层的独立配置;
   `BitmapDrawable` 目标密度、代码创建的 px 尺寸和包装图层外观由原实例保留, 不额外缩放或递归复制.
   按实例身份判断共享, 每个端点保存自己的状态数组. 同一对象用于多个端点或轨道角色时,
   在绘制和读取固有尺寸时临时应用对应状态, 绘制时应用对应 bounds, 并在 `finally` 恢复.
   临时应用和恢复状态后均调用 `jumpToCurrentState()`, 使共享实例直接显示目标静态状态, 不混合不同角色的过渡.
   临时配置和恢复期间屏蔽同步失效, 避免状态切换、bounds 变化引起递归或持续重绘;
   外部失效和实际绘制中的刷新请求仍保持有效.
   进入共享时取消该实例已有的动画调度, 共享期间不转发动画调度请求, 避免临时状态切换不断产生过渡刷新任务.
   共享实例只保证静态状态外观, 颜色、透明度和私有动画状态不能按端隔离. 独立动画需为两端提供不同实例,
   独占实例正常接收状态与动画调度, 不因测量或绘制而强制调用 `jumpToCurrentState()`.
   `getStartThumb()` / `getEndThumb()` 返回当前实际绘制实例, 共享时为同一对象.
8. 轨道与滑块接入 `Drawable.Callback`, 状态、布局方向、聚合可见性和动画调度跟随 View 生命周期.
   滑块单独组合 `state_enabled`, `state_pressed`, `state_drag_hovered` 和 `state_focused`, 两端状态不能相互覆盖.
   生命周期按实例身份去重. 替换 Drawable 时仅释放已不再使用的实例回调和待执行任务, 请求必要的布局与重绘.
   独立覆盖不能因通用外观改变而丢失. 传入实例交由当前控件管理, 不同时供其它 View 使用, 因为 `callback` 只能有一个.
9. `kitThumbWidth` / `kitThumbHeight` 的 `inner` 配置保留到测量阶段, 分别读取各端 Drawable 的固有尺寸;
   显式尺寸共同应用于两端. 轨道预留较宽滑块的空间, 几何使用实际布局尺寸和 `paddingLeft` / `paddingRight`.
   高度遵守父布局约束与 View 最小高度; 选中前景通过裁剪叠加, 不使用 CLEAR 擦除背景.
   内部几何字段保持 `private`, 不作为公开布局或交互 API.
10. 两个无障碍虚拟节点分别提供端点名称、整数当前值和该端的合法刻度范围, 重合时仍独立可聚焦.
    `startThumbLabel` / `endThumbLabel` 为可空 `CharSequence`, `null` 使用通用默认名称;
    XML 对应 `kitStartThumbLabel` / `kitEndThumbLabel`. 调节动作只在合法范围内按整数步长增减,
    不设置浮点 `RangeInfo`, 不声明或接受 `ACTION_SET_PROGRESS`. 节点值和动作可用性随选区、约束及禁用状态更新.
    当前 CustomView 1.1.0 的 `ExploreByTouchHelper` 强制要求节点父坐标, 并由此计算屏幕边界和可见性;
    保留 `setBoundsInParent()` 的单处弃用抑制, 不能仅替换为屏幕坐标. 升级依赖时重新核对该要求及替代 API.
    Tab / Shift+Tab 在端点之间移动并允许离开控件, 左右键调整当前端点; 焦点状态只应用于对应滑块.
    `ExploreByTouchHelper` 构造会强制启用宿主焦点, 必须在创建前捕获平台解析的焦点配置, 创建后恢复显式配置.
    API 24/25 使用布尔值, API 26 及以上保留整数模式以维持 `FOCUSABLE_AUTO` 语义;
    未指定 `android:focusable` 时默认可聚焦, 不覆盖调用方的 `android:focusableInTouchMode` 配置.
    使用原生按键分发保留 `OnKeyListener` 的优先消费能力, 监听器返回 `true` 时不再由控件调整数值或导航端点.
    已开始的键盘会话即使匹配的 UP 被监听器消费也必须收尾; 正常 UP 通知结束, 带 `FLAG_CANCELED` 的 UP 通知取消.
    调节和导航仅处理 DOWN / UP 及 DOWN 的 repeat; 旧式 `ACTION_MULTIPLE` 不执行调整, 防止框架合成未配对的交互.
11. 保存 `start`, `end`, `total`, `step`, `startMin`, `startMax`, `endMin` 和 `minSpan` 的一致快照,
    恢复时一次应用完整配置和合法选区, 不通过一串公共 setter 暴露中间状态或重复通知.
    不保存 Drawable、活动指针、交互会话或动画状态. 恢复后的范围与恢复前不同才通知范围监听器,
    不伪造交互开始、结束或取消事件.
12. `isRtlMirroringEnabled` / XML `kitRtlMirroringEnabled` 默认为 `true`, 在 RTL 下统一镜像轨道选区、
    滑块位置、命中区域、拖动数值映射、重合方向仲裁及左右键调节. 起点仍是较小数值, 无障碍增减仍按数值大小解释.
    关闭时固定数值从左到右递增, 不强制修改 View 的 `layoutDirection`, 继续使用平台解析后的 padding 和 Drawable 方向.
    运行时切换镜像或布局方向后同步更新几何、触点锚点和无障碍边界, 不修改数值选区.

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
匿名应用侧标识由 `DeviceIdUtils` 负责, 显示与窗口信息由 `WindowUtils` 负责. 该边界避免一个宽泛的设备信息入口混合不同生命周期,
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

## 11. 窗口信息

`WindowUtils.getCurrentWindowBounds()` 只接收 Activity, 通过 AndroidX `WindowMetricsCalculator` 统一计算 API 24 及以上
当前窗口 bounds. 返回独立的 `Rect` 副本, 单位为 px, 包含状态栏和导航栏所在区域, 不扣除任何 WindowInsets.
API 29 及以上结果由 AndroidX Window 保证正确; API 24 至 28 使用 best-effort 兼容计算, 多窗口或无法可靠补偿导航栏时
可能不精确.
该结果是调用时基于系统最近一次上报窗口状态生成的快照. 旋转、分屏比例调整、自由窗口缩放或其它窗口配置变化后,
调用方必须重新查询, 不得把返回的 `Rect` 作为进程级固定窗口状态长期缓存.
该工具不提供物理屏幕尺寸或静态的安全内容尺寸; 状态栏、导航栏、IME 和 display cutout 必须由界面层基于目标 View 实时收到的
`WindowInsetsCompat` 处理.

`WindowUtils.isLandscape()` 和 `WindowUtils.isPortrait()` 必须严格读取传入 Context 当前资源配置的
`Configuration.orientation`. Display rotation 只表示相对设备自然方向的旋转角度, 不得用于推断当前窗口方向.
两个方法分别只在配置明确为 `ORIENTATION_LANDSCAPE` 或 `ORIENTATION_PORTRAIT` 时返回 `true`;
`ORIENTATION_UNDEFINED` 和 `ORIENTATION_SQUARE` 下都返回 `false`, 不使用互为取反的实现.

## 12. Aegis 保护范围

`utils` 包中的三个公开顶层工具均由类型 KDoc 的 `@aegis` 标记保护:

* `DeviceIdUtils` 保护公开 API、匿名标识生成与存储兼容语义、进程内并发一致性及安全使用边界.
* `ClientInfoUtils` 保护公开 API、平台原值 getter、版本与 Locale 查询语义以及默认 User-Agent 格式和隐私边界.
* `WindowUtils` 保护公开 API、完整窗口 bounds 的快照与兼容语义、Insets 职责边界及资源方向判定规则.

保护范围包括本文件第 9 至 11 节明确的对应公开契约、可观察行为和实现约束. 修改受保护内容时必须遵守根目录
`AGENTS.md` 的授权和审计要求.

## 13. 测试

修改 `kit` 后至少执行:

```bash
./gradlew :kit:testDebugUnitTest -q
./gradlew :kit:compileReleaseKotlin -q
git diff --check
```

公开扩展函数应保留编译型测试, 覆盖 lambda 调用形态。

单元测试启用 Android 资源以加载测试 XML. `kit/src/test/resources/robolectric.properties` 使用 `sdk=24` 保持原有默认测试基线,
避免默认 SDK 随 Manifest 的 targetSdk 改变; 测试类或方法中的显式 `@Config` 继续覆盖该默认值.
分格输入测试显式使用 API 34 Native Graphics, 验证长度过滤, 原生转换文本, 测量, Drawable 绘制及文本菜单限制.
线条测试同样使用 API 34 Native Graphics, 验证方向, 测量, 亚像素线型, 默认颜色与运行时属性更新.
通过 Canvas 绘制入口观察公开的 `Path.generationId`, 验证线型变化更新路径版本, 包括从实线或空线型切回点划线;
颜色或状态变化及相同线型值赋值应保留路径版本. 该测试验证缓存失效条件, 不依赖 GC 或原生地址复用时机.
范围选择测试覆盖所有赋值入口的步长对齐、起点下限、最近范围投影、等距规则、无解时原子保留旧状态和大数值边界,
起点下限同时验证未对齐步长时的首个合法刻度、与起点上限及最小间隔的组合、缩小 total 时不放宽下限,
以及触摸、键盘、无障碍和状态恢复入口的一致性.
小范围联合投影通过穷举合法范围校验最优结果; 同时覆盖非连续 pointer ID、双指独立拖动、重合方向仲裁、接管不跳动,
以及取消和禁用后的状态清理. 使用 API 34 Native Graphics 验证前景裁剪、测量约束、RTL padding、独立滑块状态,
Drawable 清空与继承优先级、共享状态与固有尺寸、显式 BitmapDrawable 目标密度及包装外观、刷新回调和生命周期调度.
Drawable 回归应验证程序配置不复制或调用原实例的 `mutate()`, 保留半透明颜色及图层独立透明度、滤镜和 tint;
XML 加载的资源应隔离共享配置, 程序资源示例遵守先 `mutate()`、再配置、最后交给控件的顺序.
共享带过渡 selector 应直接绘制目标静态状态并恢复 state 和 bounds, 独占实例不应因测量或绘制被强制跳过动画.
验证实例进入共享后取消已有动画任务且不再调度新任务, 外部直接请求刷新仍生效.
XML 哨兵和清空需使用真实编译的样式资源验证.
轨道没有可用空间时应保持原范围且不崩溃.
范围事件回归应覆盖全部赋值入口的变化去重、双指单次 MOVE / UP / POINTER_UP 的最终范围通知,
验证同值回写、归一化后同值赋值和重复约束设置均不改变后续拖动结果, 实际约束或几何变化仍正确重建锚点;
正常抬起时最终坐标应用和 CANCEL 不追加采样, 以及触摸、键盘重复键、无障碍动作对应的开始、结束和取消时序.
单触点取消需验证只忽略被取消触点的新坐标、其他触点继续生效、等待触点接管不跳动及整次会话保持到最终 UP / CANCEL.
状态恢复应验证完整配置快照、单次范围通知及无交互事件.
键盘和无障碍回归应覆盖重合端点独立聚焦、边界动作、大整数步进、Tab 离开、单端焦点状态及 `OnKeyListener` 优先消费,
包括 DOWN 被消费时不开始交互和 UP 被消费时仍结束已有会话;
焦点初始化回归在 API 24/25 验证显式禁用及触摸模式配置, 在 API 26/34 额外验证 `auto` 模式保留.
RTL 回归同时检查默认镜像、关闭镜像、运行时切换后的触摸映射和节点边界.

刷新加载回归覆盖不对称阈值、跨零点方向限制、慢速阻尼余量、busy 状态嵌套滚动消费量、取消与禁用清理,
以及回弹期间的旧会话停止和新手势接管. 嵌套取消需要通过真实 `NestedScrollView` 的原生事件分发验证,
不能只直接调用容器的 `onStopNestedScroll()` 代替. 组件测试覆盖同步完成或禁用后的回调重入、动态替换和默认补齐后的状态快照;
布局测试覆盖内容 gravity、头尾角色位置、margin、padding 及 RTL. detach 后重新挂载时不得保留未提交拖拽或回弹位移.
标准 FrameLayout 布局参数转换应保留 gravity; 纵向 margin 回归同时检查空闲隐藏位置和展开后的组件、内容间距.
设备测试 `KitRefreshLoadLayoutInstrumentedTest` 覆盖双向嵌套取消后的新手势、真实窗口 detach/attach,
以及回弹被新手势接管后立即松手的收尾. 回弹设备测试要求系统动画开启, 关闭时显式跳过.
旧版协议回归通过原生 `ViewParent` 重载和开启嵌套滚动的 `ScrollView` 验证边界交接、松手、取消及 busy 状态消费,
同时验证两类滚动轴独立停止、detach 清理和旧版速度交接边界. 原生控件的设备回归使用真实祖先触摸分发链.
`KitRefreshLoadLayoutBoundaryTest` 覆盖上下方向、直接和嵌套目标、自定义判定及 null 恢复默认,
并验证自动加载和 busy 期间的滚动/惯性衔接遵守同一判定.
边界回调重入应覆盖结束或切换任务、取消旧会话后, 本次滚动不再消费位移或遗留空闲偏移.
`KitRefreshLoadLayoutSyncStateTest` 覆盖双向显示同步不触发请求、同值同步保留位置/动画、null 收起拖拽,
首次布局和缺失组件的延迟定位、旧手势失效、禁用期间同步及组件回调重入.
首次状态和位移快照引起的尺寸变化, 以及布局期间替换组件, 需验证最终尺寸与定位一致且不重复发送忙碌状态.
已初始化组件由回调或外部代码修改高度后, 验证默认阈值、拖拽状态及距离快照一致, 覆盖增高、缩小和显式阈值保留.
同时验证同尺寸重布局不重复通知, 距离变化不重启 busy / Settling 动画, 并在真实 ViewRoot 中覆盖回调再次改尺寸.
位移回调更新 `wrap_content` TextView 文案时, 验证直接文本组件及组合组件都能停止重复布局并完成双向初始展开;
真实尺寸变化仍需补发新快照, 同一实例移除重加后仍需初始化.
`KitRefreshLoadLayoutConfigurationTest` 覆盖双向阈值更新、等效值去重、松手判定、动画保留及参数回调重入.
配置回归还需覆盖阻尼 `NaN`、正负无穷赋值失败后旧值不变, 以及有限低值归一化.
可见性回归覆盖双向 `GONE` 的直接及嵌套拖拽、自动加载、途中隐藏和组件回调重入,
恢复可见后重新操作, 并验证另一方向、已有任务及程序同步不受影响.
`KitRefreshLoadLayoutInteractionAnimationTest` 覆盖双向按下终止指示器惯性, 以及直接/嵌套取消中同步新任务的动画保护.
设备测试还需通过真实窗口验证取消重入, 以及首布局状态回调请求重新测量后的最终展开位置.
`KitRefreshLoadLayoutAccessibilityTest` 覆盖动作可用性和失效动作拒绝、任意内容位置执行、忙碌描述,
以及调用方描述、delegate 和内容/组件独立节点的保留.
`KitRefreshLoadLayoutAccessibilityInstrumentedTest` 通过系统无障碍服务树验证容器与内容按钮分别聚焦和操作,
双向动作只通知一次请求, 以及新窗口在首次布局前同步业务任务时恢复显示且不重复请求.
这些自动化断言不替代 TalkBack 的完整朗读与手势体验验收.

涉及原生编辑器或窗口行为的修改还需连接设备或模拟器执行:

```bash
./gradlew :kit:connectedDebugAndroidTest -q
```

线条设备测试在 API 26 及以上通过 `PixelCopy` 读取硬件加速窗口中的控件区域, 验证默认实线, 状态色和运行时线型与方向变化.
状态或属性更新后应由控件自身请求重绘, 测试不得额外调用 `invalidate()` 掩盖刷新遗漏; 复制的像素仅在内存中检查.
路径兼容回归还需覆盖连续绘制后的方向, padding 和实际布局尺寸变化, 确认没有残留旧线段或错误沿用旧边界.
低版本硬件加速兼容需在 API 26 或 27 的设备或模拟器复跑线型测试; API 34 的 Native Graphics 和设备结果不能替代旧版 HWUI 验证.

设备测试覆盖 InputConnection, Autofill, 原生 Layout 保留, 不声明或返回字符几何和静态背景停止重绘.
长按粘贴回归通过真实触摸事件长按空输入框或空白格, 再点击系统浮动菜单中的粘贴项, 验证末尾插入和长度过滤;
不能只用直接调用粘贴方法代替该入口验证.
通过 UiAutomation 获取真实无障碍节点, 验证字段语义, 焦点, 整段文本限长与清空, 粘贴及拒绝不支持的选区和导航操作.
设备上的 `ACTION_PASTE` 与长按菜单粘贴验证仅在剪贴板为空时执行; 已有内容时跳过相应测试, 以保留用户剪贴板数据.
节点集成测试不能替代启用 TalkBack 后的端到端操作验证, 不据此宣称完整的 TalkBack 使用体验已经验证.

手动查看控件效果时构建 `:kit:assembleDebugAndroidTest`, 安装生成的测试 APK 后从桌面打开 "Kit 输入测试".
入口 `KitCodeInputManualActivity` 展示范围选择、线条和短码输入; 范围区显示实时值和交互状态,
通过 XML `kitStartMin=20` 将可选区间限制为 20 到 100, 包含 20. 轨道保留 0 到 100 的数值域,
测试专用 Drawable 将小于 20 的前缀标为不可选颜色, 并跟随 RTL 镜像; 不通过回调回写模拟下界限制.
提供重合、重置、启用及 RTL 开关, 可直接验证双指拖动和重合后的方向选择. 页面和展示资源仅位于 `androidTest`,
不进入正式应用或库产物.
