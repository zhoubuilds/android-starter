# RecyclerView Decoration 边界

## 修订记录

| 修订时间（CST） | 修订人  | 修订说明                             |
|-----------------|---------|--------------------------------------|
| 2026-07-30      | whisper | 补充 Start/End 逻辑方向语义          |
| 2026-07-30      | whisper | 新增 RecyclerView Decoration 绘制边界 |

本文说明 `kit` 中 RecyclerView decoration 工具的绘制边界. 维护入口见 [开发文档](development.md),
接入方式见 [使用文档](usage.md).

## 1. 工具定位

`recyclerview.decoration` 只提供业务无关的 item 间距和分割线绘制能力. 它不读取应用资源, 主题色, 业务尺寸,
页面状态或业务文案, 也不解释某个页面的视觉规范.

Decoration 只接收 px 值. dp 转换, 颜色选择, 业务间距选择应由调用方在业务层或应用公共语义层完成.

所有 `Start` / `End` 参数都使用逻辑方向语义. 横向 RTL 布局中, start 对应物理右侧; 垂直 RTL 布局中,
水平方向的 start 同样对应物理右侧.

## 2. 主轴分割线

主轴分割线以 RecyclerView 内容区域为绘制基准, 按行或列贯穿可用区域. 分割线两端缩进只由
`mainAxisDividerMarginStart` 和 `mainAxisDividerMarginEnd` 控制.

主轴分割线不自动叠加 item `LayoutParams` margin. 这样可以避免 item 自身布局 margin 和 decoration
margin 形成隐式双重缩进, 也让调用方可以通过 decoration 参数稳定控制线长.

## 3. 交叉轴分割线

网格布局的交叉轴分割线只绘制在非首个 span 前, 用于区分同一行或同一列内的 item. 垂直网格在 RTL 布局方向下,
交叉轴 offset 和交叉轴分割线位置会按物理 left/right 镜像处理.

交叉轴分割线同样不自动读取 item `LayoutParams` margin. 当前工具以 item view 自身边界和 decoration 参数计算绘制区域.

## 4. Item Translation

分割线绘制锚点会跟随 item 对应方向的 translation. 横向主轴分割线跟随 `translationX`, 垂直主轴分割线跟随
`translationY`; 交叉轴分割线按自身绘制方向同时处理横向或纵向锚点.

主轴分割线的贯穿方向仍以 RecyclerView 内容区域为基准, 不跟随 item 在交叉轴上的 translation. 这样可以保持主轴线长和
decoration margin 语义稳定.

translation 只影响绘制位置, 不改变 `getItemOffsets` 预留的布局空间.
