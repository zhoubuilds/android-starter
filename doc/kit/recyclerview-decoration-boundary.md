# RecyclerView Decoration 边界

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                             |
|-----------------|---------|--------------------------------------|
| 2026-09-04      | whisper | 明确 Staggered Provider 降级边界 |
| 2026-09-03      | whisper | 明确低版本 Canvas 分段绘制边界   |
| 2026-09-03      | whisper | 明确 Decoration 单轴所有权与透明间距 |
| 2026-09-03      | whisper | 明确 Divider 裁剪与 Grid 查询复杂度 |
| 2026-09-03      | whisper | 明确 Staggered 主轴零尺寸限制   |
| 2026-09-03      | whisper | 区分同步与异步 Decoration 失效时序 |
| 2026-09-03      | whisper | 区分 Regular 与 Staggered Decoration |
| 2026-09-03      | whisper | 增加 Staggered 分割线边界             |
| 2026-09-03      | whisper | 收敛预测布局和首尾 span 查询         |
| 2026-09-03      | whisper | 明确动画期取舍并限定 margin 所在轴   |
| 2026-09-02      | whisper | 统一 Grid 绘制、参数与热路径边界    |
| 2026-09-02      | whisper | 补充 Staggered 列表间距边界          |
| 2026-09-02      | whisper | 明确主轴间距使用 logical start 分配  |
| 2026-09-02      | whisper | 明确列表间距职责与取整语义           |
| 2026-07-30      | whisper | 补充 Start/End 逻辑方向语义          |
| 2026-07-30      | whisper | 新增 RecyclerView Decoration 绘制边界 |

本文说明 `kit` 中 RecyclerView decoration 工具的绘制边界. 维护入口见 [开发文档](development.md),
接入方式见 [使用文档](usage.md).

## 1. 工具定位

`recyclerview.decoration` 只提供业务无关的 item 间距和分割线绘制能力. 它不读取应用资源, 主题色, 业务尺寸,
页面状态或业务文案, 也不解释某个页面的视觉规范.

Decoration 只接收 px 值. 间距装饰器的参数以及分割线尺寸和 margin 必须为非负值. dp 转换, 颜色选择, 业务间距选择应由
调用方在业务层或应用公共语义层完成.

所有 `Start` / `End` 参数都使用逻辑方向语义. 横向 RTL 布局中, start 对应物理右侧; 垂直 RTL 布局中,
水平方向的 start 同样对应物理右侧.

## 2. 列表参与者职责

Adapter 负责 item 类型、View 创建、数据绑定和更新通知. Adapter、ViewHolder 或 item XML 不通过以下方式表达列表几何或装饰:

* 在 item 根 View 上设置用于 item 间隔或容器边界留白的 margin.
* 增加只用于相邻 item 分隔的空白占位 View.
* 增加只用于绘制 item 间分割线的子 View.

RecyclerView 负责安装 Decoration. LayoutManager 负责方向、顺序、span、布局和滚动; Decoration 只读取这些布局信息计算
offset 和绘制位置. 单个 item 内部的内容间距、卡片内分组线或其它具有 item 内容语义的视觉元素仍由 itemView 自己负责.

### Decoration 组合

RecyclerView 会累加所有 ItemDecoration 返回的 offset, 但不会为每个 Decoration 保存独立的绘制区域. 为保证相邻间距、
交叉轴互补取整和分割线归属稳定, 同一个 RecyclerView 的同一轴最多只能有一个内部间距所有者:

* `RegularItemSpaceDecoration` / `StaggeredItemSpaceDecoration` 的 `mainAxisSpace` 或 `crossAxisSpace` 非 0 时,
  分别拥有对应轴的内部间距.
* `RegularItemDividerDecoration` / `StaggeredItemDividerDecoration` 的 `mainAxisDividerSize` 或
  `crossAxisDividerSize` 非 0 时, 分别拥有对应轴的内部间距.
* 不得在同一轴叠加两个非零所有者. 主轴和交叉轴内部间距均为 0、只设置 `startSpace` / `endSpace` 的边界
  SpaceDecoration 可以与内部间距所有者共存.

Divider 的 size 始终参与 offset 计算, 不依赖对应 Drawable 是否存在. Drawable 为 `null` 时, 该轴只保留透明间距且不绘制;
这允许同一个 DividerDecoration 同时表达一个轴绘制分割线、另一个轴仅留白, 无需叠加第二个内部间距所有者.

## 3. 主轴间距分配

`RegularItemSpaceDecoration` 的主轴内部间距由后一个 item 或 span group 的 logical start offset 单边承担. 非首项的
logical start offset 为 `mainAxisSpace`, 非末项的 logical end offset 为 0, 因此相邻内容之间的间距严格等于目标值.
首项或首个 span group 的 logical start 使用 `startSpace`, 末项或最后一个 span group 的 logical end 使用 `endSpace`.

RTL 和 `reverseLayout` 只改变 logical start/end 到物理边的映射, 不改变上述分配规则.

`StaggeredItemSpaceDecoration` 将 `mainAxisSpace` 放在所有非起始 item 的 logical start, logical end 始终为 0.
起始 item 使用 `startSpace`; 其范围由 `StaggeredFullSpanProvider` 推导:

* 起始处没有 full-span item 时, 前 `min(itemCount, spanCount)` 个 item 分别开启一个 span, 均属于起始边界.
* position 0 是 full-span item 时, 只有 position 0 属于起始边界.
* 起始前缀中稍后出现 full-span item 时, 只有该 full-span item 之前的普通 item 属于起始边界; full-span item
  及其后的 item 使用 `mainAxisSpace`.

上述推导要求每个 item 包含 decoration inset 和 LayoutParams margin 后的 decorated main-axis
measurement 大于 0. 主轴零尺寸 item 不会推进 StaggeredGridLayoutManager 的 span 端点,
因此不在 `StaggeredItemSpaceDecoration` 和 `StaggeredItemDividerDecoration` 的支持范围内.

瀑布流末端各 span 可能停在不同位置, 因而该装饰器不提供 `endSpace`.

## 4. 网格交叉轴间距取整

设 `spanCount` 为 `n`, 交叉轴目标间距为 `space`, 当前逻辑 span 索引为 `i`. 单 span item 的交叉轴 offset 固定为:

* `crossStart = ceil(i * space / n)`.
* `crossEnd = floor((n - 1 - i) * space / n)`.

start 向上取整, end 向下取整, 两侧采用互补结果. 因而相邻两列始终满足
`crossEnd(i) + crossStart(i + 1) = space`; `space` 只有 1px 时也不会在整数除法中消失.

同一列 start 与 end 的总 offset 相对理想小数值最多相差 1px. 取整只依赖逻辑 span 索引, 所以每个 span group
中的同一逻辑列使用相同结果, 不会在不同行之间随机移动误差. 垂直 RTL 布局只镜像物理 left/right, 不改变逻辑列的取整分配.

`StaggeredItemSpaceDecoration` 对普通 item 读取 `StaggeredGridLayoutManager.LayoutParams.spanIndex`, 并使用相同
取整公式. LayoutManager 会在测量 item 前分配该索引, 因此不需要调用方传入 spanCount 或 spanIndex. full-span item
独占交叉轴, 两侧 offset 均为 0.

## 5. Staggered full-span 契约

Staggered 交叉轴 offset 和分割线直接读取当前 `StaggeredGridLayoutManager.LayoutParams` 的 span 信息, 不依赖
`StaggeredFullSpanProvider`. 主轴起始拓扑需要区分 `startSpace` 与内部间距、span 数大于 1 且列表不止一个 item 时,
RecyclerView 的直接 Adapter 应实现 `StaggeredFullSpanProvider`. Divider 的主轴尺寸非 0 时同样需要该拓扑.

缺少所需 Provider 时采用非致命降级: Decoration 按实例记录一次警告, 禁用依赖起始拓扑的主轴 offset 和分割线,
交叉轴继续工作. Adapter 已实现 Provider 时, 应在 `onBindViewHolder` 中复用查询结果设置
`StaggeredGridLayoutManager.LayoutParams.isFullSpan`; 非预测布局阶段如果两者不一致, Decoration 仍会立即失败,
防止静默产生错误间距.

Provider 查询必须基于当前 Adapter 数据且对同一 position 保持稳定, 不能依赖 itemView 是否已经创建或绑定.
当前不自动穿透 `ConcatAdapter`: `ConcatAdapter` 不能直接实现该接口, 因而不能与该装饰器组合.

## 6. 主轴分割线

`mainAxisDivider` 表示由主轴 item 间距承载的分割线, 名称表示它所在间距的轴, 不表示线的延伸方向.
Linear 主轴分割线以 RecyclerView 内容区域为基准沿交叉轴贯穿, 两端缩进由
`mainAxisDividerCrossAxisStartMargin` 和 `mainAxisDividerCrossAxisEndMargin` 控制. 参数名中的
`CrossAxisStart/End` 明确表示 margin 位于分割线延伸的交叉轴上; 垂直布局按 RTL 映射水平方向逻辑边,
横向布局分别对应物理 top/bottom.

Divider 的容器绘制边界遵循 `RecyclerView.clipToPadding`. `true` 时限制在 padding 内的内容区域;
`false` 时使用 RecyclerView 的完整可见范围, 即横轴 `0..width`、纵轴 `0..height`. 分割线 margin
继续从所选逻辑边向内缩进, 不改变 item offset.

Grid 主轴分割线绘制在非首 span group 每个 item 的 logical start offset 中, 并沿该 item 的交叉轴范围分段绘制.
两端缩进在 item 交叉轴范围内应用. 分割线绘制归属与 `RegularItemSpaceDecoration` 的主轴间距分配保持一致.

Staggered 主轴分割线绘制在所有非起始 item 的 logical start offset 中. 普通 item 和 full-span item 都按自身
交叉轴范围分段绘制, 两端缩进在 item 范围内应用. 分割线归属与 `StaggeredItemSpaceDecoration` 保持一致.

主轴分割线不自动叠加 item `LayoutParams` margin. 这样可以避免 item 自身布局 margin 和 decoration
margin 形成隐式双重缩进.

## 7. 交叉轴分割线

`crossAxisDivider` 表示由 span 间交叉轴间距承载的分割线. Grid 先在当前已布局 item 实际使用的非边界
span 分隔位置上, 沿主轴连续绘制该分割线; 再绘制沿交叉轴分段的主轴分割线. 交汇区域因而由连续线填充,
不依赖相邻 item 的绘制长度或像素取整.

连续线只需覆盖当前已布局 Grid 内容与 RecyclerView 可见内容区域的交集. 它根据 LayoutManager 的 span 几何定位,
无需加载或遍历完整 Adapter. `crossAxisDividerMainAxisStartMargin` 和 `crossAxisDividerMainAxisEndMargin`
只在 Adapter 逻辑首尾的 span group 已布局时缩进连续线对应端点. 参数名中的 `MainAxisStart/End` 表示 margin
位于连续线延伸的主轴上, 并随 `reverseLayout` 及横向 RTL 映射物理边; margin 不会在每个可见 item 上重复产生断点.

垂直 Grid 在 RTL 布局方向下, span offset 和分割线位置按物理 left/right 镜像. 连续线绘制时会排除当前 item
的实际视觉区域, 因此不会穿过跨 span item 或正在平移的 item.

Staggered 同样按当前已布局普通 item 使用的 span 边界沿主轴连续绘制交叉轴分割线, 并排除普通 item、full-span item
的实际视觉区域. 连续线的范围是当前已布局内容在主轴上的包络; main-axis start/end margin 只在 Adapter 对应逻辑边界
已布局时缩进该包络, 不为各 span 构造结束边界间距.

## 8. Item Translation

按 item 绘制的分割线会跟随 item 的 `translationX` 和 `translationY`. Grid 连续 span 分割线锚定在 LayoutManager
的 span 边界, 不会因某个 item 的 translation 整体移动; 与该 item 平移后实际区域相交的主轴切片不会绘制,
避免分割线穿过动画中的 item.

translation 只影响绘制位置, 不改变 `getItemOffsets` 预留的布局空间.

`RegularItemDividerDecoration` 和 `StaggeredItemDividerDecoration` 通过 `onDraw` 在 item 之前绘制, 绘制归属直接读取当前
`RecyclerView.State`、child position 和 LayoutParams, 不缓存逐 View 的历史归属. 连续分割线按主轴分段,
与 translation 后 item 实际区域相交的主轴切片不会绘制; 该实现不依赖 Canvas 差集裁剪 API. Adapter 更新期间最新 position
与屏幕上的旧几何可能短暂不一致, 被移除的 View 也可能得到 `NO_POSITION`; 这类中间帧不保证分割线严格跟随旧布局,
但 item 位于分割线上层, 不会被暂时不准确的分割线覆盖.

RecyclerView 的预测布局可能复用旧 decoration inset. Decoration 不观察 Adapter 更新. 同步 `notifyItem*` 会影响
position、itemCount、span 或首尾归属时, 必须在通知前通过 AndroidX Core KTX 官方 `doOnNextLayout` 注册回调,
并在更新布局完成后调用 `RecyclerView.invalidateItemDecorations()`, 使下一次布局重新计算 offset.

`ListAdapter` 或 `AsyncListDiffer.submitList` 会异步计算差异, 必须在实际提交列表的 commit callback 中注册同样的
下一次布局回调. 连续提交列表时, 较早但未实际提交的列表可能不会执行 callback, 不能依赖较早回调完成最终失效.
在 `submitList` 前预注册的一次性回调可能被差异提交前的其它布局提前消费.

紧跟同步 Adapter `notify` 调用 `invalidateItemDecorations()` 可能被 pre-layout 消费, 不满足上述契约. 因此动画中间态
采用宽松语义; 完成手动失效后的稳定布局必须符合当前 Adapter 和 LayoutManager 拓扑. pre-layout 的
`RecyclerView.State.itemCount` 与 Adapter 当前数量不一致时, `RegularItemSpaceDecoration` 会保守处理首尾 span group,
不拿旧布局 position 查询 Adapter 的新 `SpanSizeLookup`.

运行时修改 LayoutManager 的 `orientation`、`reverseLayout`、`spanCount`, 替换 `SpanSizeLookup` 实例,
或修改 RecyclerView layout direction 后, 必须立即调用 `invalidateItemDecorations()`, 使下一次布局重算已缓存的
decoration inset. 修改同一 `SpanSizeLookup` 实例的内部规则时, 必须先调用 `invalidateSpanIndexCache()` 和
`invalidateSpanGroupIndexCache()`, 再调用 `RecyclerView.invalidateItemDecorations()`; 后者不会清除 span 查询缓存.

## 9. Grid 热路径

`GridLayoutManager.SpanSizeLookup` 默认不保证开启 span index 或 span group index 缓存. 直接调用
`getSpanIndex()` 或 `getSpanGroupIndex()` 可能从 Adapter position 0 扫描到目标 position, 因此不能用于
Decoration 的 `getItemOffsets` 或逐帧绘制路径.

Decoration 直接读取当前 item 的 `GridLayoutManager.LayoutParams.spanIndex/spanSize`. 首个 span group 只在当前
position 仍可能属于首组时探测起始 span. `endSpace` 为 0 时不判断最后一个 span group; 非 0 时只有 Adapter 最后
`spanCount` 个 position 会从当前 item 向后探测本组剩余 span. 单个首尾归属判断的查询次数为 `O(spanCount)`;
同一首组或末组最多有 `spanCount` 个 item 执行该判断, 因此一轮 Regular Grid 可见 item offset 计算最坏为
`O(visibleChildCount + spanCount^2)`.

连续分割线绘制会对当前 child 的主轴区间排序, 并在每个 span 边界跳过与 child 相交的主轴切片.
Regular 一轮绘制最坏为
`O(visibleChildCount * log(visibleChildCount) + spanCount * visibleChildCount + spanCount^2)`;
Staggered 一轮绘制最坏为
`O(visibleChildCount * log(visibleChildCount) + spanCount * visibleChildCount)`.
这些成本都只受当前可见 child 和 `spanCount` 限制, 不随 adapter position 或 itemCount 增长.
该实现不持有跨布局缓存; Adapter 更新后的 decoration inset 失效遵守上一节的调用方契约.
