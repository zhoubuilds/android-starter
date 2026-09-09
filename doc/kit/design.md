# Kit 设计文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                   |
|-----------------|---------|----------------------------|
| 2026-09-09      | whisper | 补充线条控件与标准分割线的选型边界 |
| 2026-09-09      | whisper | 明确线条点划效果的低版本绘制兼容策略 |
| 2026-09-09      | whisper | 明确线条默认实线、状态色与运行时配置 |
| 2026-09-09      | whisper | 明确线条方向与尺寸职责边界 |
| 2026-09-09      | whisper | 明确取消拖拽与短码菜单创建期行为 |
| 2026-09-09      | whisper | 补齐短码触摸焦点、长按粘贴与 Drawable 展示边界 |
| 2026-09-08      | whisper | 明确短码输入的 RTL 与无障碍编辑边界 |
| 2026-09-08      | whisper | 分格输入长度配置收敛到原生 API |
| 2026-09-08      | whisper | 明确分格输入的原生编辑与展示边界 |
| 2026-09-08      | whisper | 合并基础控件包并移除整套展示组件 |
| 2026-09-05      | whisper | 收敛窗口边界与 Insets 职责 |
| 2026-09-04      | whisper | 明确点击与长按的退化语义和使用边界 |
| 2026-09-04      | whisper | 收敛 RecyclerView 手势 API 与兼容边界 |
| 2026-09-04      | whisper | 明确 Decoration 非致命降级策略 |
| 2026-09-03      | whisper | 明确连续分割线低版本兼容策略 |
| 2026-09-03      | whisper | 区分 Regular 与 Staggered Decoration |
| 2026-09-03      | whisper | 增加 Staggered 分割线能力 |
| 2026-09-03      | whisper | 明确 Decoration 动画期绘制取舍 |
| 2026-09-02      | whisper | 明确 Grid 分割线贯穿方向 |
| 2026-09-02      | whisper | 增加 Staggered 列表间距能力 |
| 2026-09-02      | whisper | 明确 RecyclerView 职责边界 |
| 2026-09-01      | whisper | 排除应用级 Activity 状态    |
| 2026-09-01      | whisper | 建立 Kit 定位和结构取舍     |

本文记录 Kit 的设计目标, 职责边界和结构取舍. 维护实现请阅读 [开发文档](development.md), 业务接入请阅读
[使用文档](usage.md).

## 1. 背景与目标

Android 项目中会持续出现与具体业务无关, 但容易在不同页面和项目间重复实现的工具与 UI 能力. Kit 用于集中维护这些能力,
降低重复实现, 接入和兼容性维护成本.

Kit 同时容纳以下类型的通用能力:

* Android 平台工具和扩展.
* 通用自定义 View 和交互容器.
* RecyclerView 等 Android UI 基础设施的辅助封装.
* 其它不依赖业务语义且具备跨项目复用价值的 Android 能力.

这些能力的技术形态可以不同. Kit 不要求所有内容属于同一种工具类别, 而是要求它们共同遵守业务无关, 应用语义无关和可复用的
边界.

## 2. 非目标

Kit 不负责:

* 领域模型, 业务流程, 业务状态和页面专用组件.
* 固化内容字段组合和视觉结构的整套内容卡片, 分享面板等应用展示方案.
* 应用统一响应, 错误映射, 消息模型, 图片加载约定或主题设计系统.
* 当前 Activity, 任务栈, 前后台状态和导航状态等应用级运行时语义.
* 真实域名, 渠道配置, 业务文案和应用专用资源.
* 为尚未出现的复用需求预先建设完整工具框架或细粒度模块体系.

需要上述能力时, 应由 `foundation`, 业务模块或 `app` 组合根负责.

## 3. 依赖边界

Kit 是独立的底层 Android 工具模块, 不依赖 `architecture`, `foundation`, `feature/*` 或 `app`. 其它模块可以按职责按需消费
Kit, 但 Kit 不读取它们的类型, 资源, 配置或运行状态.

Kit 可以依赖 Android Framework, AndroidX 和工具自身必需的通用库. 新增依赖必须服务 Kit 内的真实能力, 并根据公开 API
可见性选择 `api` 或 `implementation`, 不能因为某个调用方已经使用某项依赖而将其下沉.

## 4. 结构取舍

Kit 当前有意保持为单一模块. 工具, 扩展, 通用 View, 交互容器和 RecyclerView 辅助能力共存, 不代表模块职责失控;
能力类型不同本身也不构成拆分模块的理由.

模块内部优先使用 `package` 按稳定语义组织源码. 只有当一组能力已经形成清晰类别时才建立对应 `package`, 不为单个类型或
假设中的未来能力预先建立目录. `package` 用于提高查找和维护效率, 不承担人为分层或强制隔离职责.

通用自定义 View, 输入控件, 交互容器和相应回调契约统一放入 `com.whisper.kit.widget`. `view` 与 `widget` 没有稳定的职责差异,
不再并列维护; 分格输入和刷新加载控件直接使用 `widget` 根包, 不为单个控件继续保留 `input` 或 `refresh` 子包.
刷新容器直接处理的触摸手势被取消时只收起尚未提交的拖拽, 不把取消当作松手触发刷新或加载.

基础控件应提供可独立组合的绘制, 输入或手势能力, 由调用方配置样式并组织页面内容. 将文案, 图片加载和点击行为改成参数,
不足以证明整套展示方案适合 Kit. 内容卡片的字段组合和分享面板的渠道排列仍属于应用展示设计, 由具有实际消费者的业务模块
或应用 `foundation` 维护, 不在公共模板中预设完整界面.

只有出现明确且可验证的工程收益时才考虑拆分模块, 例如:

* 一组能力需要独立接入, 发布或版本演进.
* 可选能力引入显著依赖, 多数消费者不应承担该依赖或产物体积.
* 构建性能, 资源隔离, 平台基线或依赖冲突已经形成实际问题.
* 独立所有权或稳定公开契约需要构建级隔离.

目录看起来杂乱, 类型数量增加或不同能力使用了不同 Android API, 均不足以单独证明拆分合理.

## 5. 演进原则

* 新能力先证明业务无关性和跨项目复用价值, 再进入 Kit.
* 能力之间保持低耦合, 不为复用方便引入与能力无关的共享可变状态.
* 确需进程级状态时, 初始化入口, 生命周期, 线程语义和失败行为必须明确, 且不得承载业务状态.
* 需要观察当前 Activity 或任务栈时, 由应用组合根按具体消费场景实现并注入, Kit 不提供全局 Activity 定位器.
* 应用文案, 资源选择, 业务回调和领域模型由调用方提供, Kit 只处理通用 Android 行为.
* 公共 API 或可观察行为变化时, 同步更新测试, 开发文档和使用文档.

## 6. RecyclerView 职责边界

Kit 的 RecyclerView 工具用于维持列表各参与者的职责边界, 不建设替代 AndroidX RecyclerView 的 Adapter 或
LayoutManager 框架:

* Adapter 负责 item 类型、item View 创建、数据绑定、稳定 ID 和数据更新通知, 不负责 item 之间或 item 与容器边界之间的几何间距.
* RecyclerView 通过 `ItemDecoration` 统一应用列表间距和分割线, 不要求 Adapter 为装饰创建额外 View 或修改 item 根布局参数.
* LayoutManager 负责方向、顺序、span、item 布局和滚动; Decoration 读取其布局拓扑计算 offset, 不复制或接管布局算法.
* itemView 负责单个 item 的内容、内部布局和语义. item 根 View 不使用 margin 表达列表间距, item 层级不增加纯装饰性的分割线子 View.

item 内部内容之间的 margin 和具备真实内容语义的 View 不受上述限制. 判断边界时以视觉元素是否属于单个 item 内容为准,
不以它恰好写在 XML、ViewHolder 或 Adapter 中为准.

Grid 的 span 边界在交叉轴上稳定, 分割线因而优先沿主轴贯穿当前已布局内容; 行或列间的分割线再按 item
沿交叉轴分段补齐. 该顺序让交汇区域始终由连续线覆盖, 也与不具备统一交叉轴分组边界的流式布局保持相同模型.

分割线在 item 之前绘制, 并直接使用 RecyclerView 当前布局状态判断归属, 不持有逐 View 的历史归属. Adapter 更新动画期间
允许分割线与旧 View 几何短暂不一致, item 会覆盖其绘制结果. Decoration 不观察 Adapter 更新; 影响列表几何的更新布局完成后,
调用方负责失效 decoration inset, 以保证最终间距和归属正确.

Linear/Grid 的行列规整拓扑使用 `RegularItemSpaceDecoration` 和 `RegularItemDividerDecoration`.
`StaggeredGridLayoutManager` 的 span 由运行时高度和 full-span 拓扑共同决定, 与 Linear/Grid 的规则不同, 因此使用独立的
`StaggeredItemSpaceDecoration` 和 `StaggeredItemDividerDecoration`. Decoration 直接读取 LayoutManager 已分配的
span index; Adapter 只通过 `StaggeredFullSpanProvider` 暴露自己本就负责设置的 full-span 元数据, 不参与间距数值、
分割线样式或方向计算. Staggered 分割线与 Grid 使用同一绘制模型: 先沿主轴连续绘制 span 间分割线并跳过与 item
实际区域相交的主轴切片, 再在非起始 item 的 logical start 间距中沿交叉轴分段绘制主轴分割线.

Decoration 类型与 LayoutManager 不匹配时采用非致命 no-op, 并按实例记录一次警告. Staggered 的交叉轴行为只依赖
LayoutParams 的实际 span 信息; 只有主轴起始拓扑需要区分 `startSpace` 与内部间距时才依赖
`StaggeredFullSpanProvider`. 缺少 Provider 时只禁用依赖该拓扑的主轴行为, 已提供 Provider 却与
`LayoutParams.isFullSpan` 不一致时仍立即失败, 避免两个布局事实静默分叉.

## 7. RecyclerView 非侵入手势命中

`recyclerview.listener` 用于在 RecyclerView 层统一观察 item 及其子 View 的点击和长按手势, 核心职责是根据触点、View
层级和变换矩阵确定命中目标. 点击与长按分别使用 `clickable && enabled` 和 `longClickable && enabled` 过滤目标,
并复用同一套命中、目标锁定和失效算法.

完全无侵入是该能力的设计前提: listener 不向 itemView 或子 View 安装监听器、AccessibilityDelegate、tag 或其它状态,
不修改 `clickable` / `enabled`, 不消费 MotionEvent, 也不合成或重新分发事件. 命中算法逐层应用子 View 矩阵的逆变换,
并遵循常规 child order 与 z/elevation. 被目标过滤器排除但按标准 View 标志仍会处理指针事件的前景节点必须阻断后方节点,
避免视觉遮挡区域发生点击穿透.

单击目标在 ACTION_DOWN 时完成一次完整命中并锁定. 后续 MOVE 只沿原目标的当前父链做坐标逆变换和边界验证,
不会重新扫描兄弟树; 原目标一旦离开允许的点击边界, 当前手势永久取消. ACTION_UP 只验证锁定目标仍属于原 item、
仍满足过滤条件且 adapter position 有效, 不允许把一次手势改派给后来移动到触点下方的其它 View.
滚动判定只检查 LayoutManager 支持的滚动轴, 并分别比较各轴位移与 RecyclerView touch slop; 交叉轴移动以及
各滚动轴分别未越过阈值的移动不会因二维合成距离被提前取消.
`requestDisallowInterceptTouchEvent(true)` 不是触摸协议中的 CANCEL, 但可能让 RecyclerView 级旁路监听器无法继续收到完整事件序列;
监听器收到该通知时清理已经锁定的目标, 让当前手势安全退化为不回调.
每个有效的 DOWN/UP 序列独立表达一次普通 View 点击; 点击分发器禁用双击识别, 不合并第二次点击, 也不为确认单击增加延迟.
按住超过平台长按超时后, 点击监听器在超时回调当下采样锁定目标的公开状态. 目标满足
`longClickable && enabled` 时立即清理并永久放弃当前旁路点击; 该结果不取决于是否同时安装旁路长按监听器,
也不取决于原生 OnLongClickListener 或上下文菜单是否真正消费长按. 其它目标保留到 ACTION_UP 并执行完整最终校验;
超时后的属性变化不重新触发长按占用判断. 这是完全无侵入前提下基于公开 View 标志的稳定退化, 不等同于观察
原生 `performLongClick()` 的动态 Boolean 消费结果.
长按使用平台 GestureDetector 超时, 在超时成立时验证并回调同一个 DOWN 目标. 长按回调只是观察通知, 不返回消费结果,
不代替或改变 View 原生 OnLongClickListener 和上下文菜单行为.
任何点击或长按回调都要求 RecyclerView、item 和目标仍附着到窗口, 且 RecyclerView 仍持有窗口焦点;
不满足时当前手势静默结束.

listener 只保证实际收到完整 DOWN 至 UP/CANCEL 事件序列时的手势识别. AndroidX 在手势中途移除
`OnItemTouchListener`, 或由其它 `OnItemTouchListener` 接管事件时, 不会向此前观察到 DOWN 的监听器补发 CANCEL.
因此手势中途移除和与其它中途消费型监听器组合不属于该能力的兼容范围; 调用方应在当前手势结束后移除监听器.

命中和前景遮挡只以 `clickable`、`enabled`、`longClickable`、`contextClickable` 等公开 View 标志为依据.
Android 没有公开 API 可以无副作用地查询 OnTouchListener 是否消费事件, TouchDelegate 也不公开可反查的目标区域;
试探性调用 `dispatchTouchEvent()` 则会重复分发并改变控件状态. 因此自定义触摸消费和 TouchDelegate 扩展区域不属于该旁路
监听器的命中模型, 依赖这些机制的业务交互必须保留原生触摸入口.

由上述无侵入边界决定, listener 只能观察经过 RecyclerView 的 MotionEvent. 无障碍操作、键盘激活和代码直接调用
`View.performClick()` / `View.performLongClick()` 不产生这条触摸事件流, 因而不在该能力的观察范围内. 需要支持这些输入方式的业务操作仍应通过
View 原生点击和无障碍链路提供; listener 不通过侵入 item 层级来模拟完整的 `performClick()` 语义.

## 8. 窗口信息边界

`WindowUtils` 只提供当前 Activity 的完整窗口 bounds 和当前 Context 的资源配置方向, 不提供含义不稳定的物理屏幕尺寸.
窗口 bounds 包含状态栏和导航栏所在区域, 不扣除任何 WindowInsets. API 24 及以上的兼容计算统一委托给 AndroidX
`WindowMetricsCalculator`, 避免在 Kit 中重复维护不同系统版本和多窗口环境的度量分支. AndroidX Window 在 API 29 及以上
保证结果正确; API 24 至 28 为 best-effort 兼容, 多窗口或无法可靠补偿导航栏时可能不精确.
该结果是调用时基于系统最近一次上报窗口状态生成的快照. 旋转、分屏比例调整、自由窗口缩放或其它窗口配置变化后,
调用方必须重新查询, 不得把返回的 `Rect` 作为进程级固定窗口状态长期缓存.

安全内容区域不是稳定的 Context 属性. 状态栏、导航栏、IME、display cutout 的 Insets 会随窗口模式、可见性和目标 View
的分发状态动态变化, 必须由界面层基于目标 View 的实时 `WindowInsetsCompat` 处理. Kit 不把这些 Insets 静态扣减成一个
“可用窗口尺寸”, 避免 edge-to-edge 下重复扣减或丢失动态变化.

显示方向表示传入 Context 当前资源配置的 `Configuration.orientation`, 不表示设备自然方向或 Display rotation.
布局资源适配仍由 Android 资源限定符和响应式布局负责; 方向查询只服务无法由资源系统表达的命令式分支.

## 9. 分格输入边界

`KitCodeInputEditText` 面向由数字或 ASCII 字母组成的短验证码, 允许的字符由调用方通过原生 `android:digits` 或 `filters` 限制,
控件不内置字符白名单. 使用单个原生 `Editable`, `InputConnection` 和 `Layout` 维护文本与编辑器状态,
再按格绘制原生转换后的文本. 密码显示遵循系统转换行为. 长度只通过原生 `android:maxLength` 或 `setFilters()` 配置,
沿用原生 `InputFilter.LengthFilter` 的 UTF-16 单位,
有效代理对联合绘制并占用两个格子, 不引入独立的 code point 或 grapheme 长度规则.

RTL 页面中的短码仍从左向右排列, 控件及周边布局继续继承页面的 RTL 方向, 不通过强制整个 View 为 LTR 改变页面布局.
光标隐藏且选区固定在文本末尾. 无障碍节点保留字段语义, 焦点, 整段文本设置和清空, 粘贴等基础编辑能力,
不声明或执行自由选区与逐字导航, 也不返回尚未适配分格绘制的字符坐标. 默认文本菜单不提供与末尾选区规则冲突的全选操作.
需要短码倒序, 复杂文字排版, grapheme 分格或自由选区与逐字导航的场景应使用适合该场景的输入控件.

默认可聚焦的输入框支持触摸获焦, 不依赖应用组件样式补充此能力; 调用方显式关闭焦点或触摸焦点时保留该设置.
默认长按通过 Android 浮动 ActionMode 提供粘贴入口, 文本变更继续使用原生 Editable 和过滤器; 返回键或再次点击输入框会关闭菜单.
调用方在菜单创建回调中同步结束菜单时, 不保留失效引用或注册返回回调.
复合图标与分格正文分别绘制,
保留原生 compound drawable, error 图标和 RTL 解析结果. 格子背景共享一个 Drawable 实例, 可见性跟随控件的聚合可见状态;
Ripple 热点映射到最近格子的局部坐标, 各格仍共享状态, 不表达独立交互.

## 10. 线条绘制边界

`KitLineView` 提供水平或垂直的实线, 虚线和点划线绘制. 方向复用 Android `LinearLayout` 的横竖常量,
与原生 `android:orientation` 枚举一致, 默认水平. 点, 划和间隔定义沿线方向的长度, 线条粗细由控件扣除 padding 后的
交叉轴尺寸决定, 不把线型长度当作粗细. 测量遵守父布局约束与 View 最小尺寸, 没有正数内容空间时不绘制线条.

默认线型为实线, 无需配置点或划长度; 间隔为 0 时始终使用实线. 默认颜色跟随主题的 `android:textColorSecondary`
状态色, 主题未提供时回退到灰色; 调用方可通过 `android:color` 覆盖. 状态色响应 View 自身的禁用, 选中和焦点等状态,
不为颜色切换自动开启点击或焦点能力.

方向, 颜色与线型既可通过 XML 或 `kitLineViewStyle` 配置, 也可通过运行时属性调整. 方向变化触发重新测量和重绘,
线型和颜色变化触发重绘; 控件不自动交换布局宽高, 也不以点划长度改变线条粗细. 调用方继续负责布局尺寸与可见性.

虚线和点划线通过 `Path` 绘制, 避免旧版 Android 硬件加速下 `drawLine()` 忽略 `PathEffect`.
兼容处理由控件内部完成, 不切换调用方的 View 图层类型.

仅需要固定尺寸的静态实线时, 调用方优先使用平台 `View` 或已接入 Material Components 时使用
`MaterialDivider`; `KitLineView` 不替代这些基础能力. `KitLineView` 适用于需要虚线、点划线、横竖方向统一配置、
状态色或运行时线型调整的场景.
