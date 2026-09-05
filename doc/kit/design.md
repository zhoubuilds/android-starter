# Kit 设计文档

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                   |
|-----------------|---------|----------------------------|
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
