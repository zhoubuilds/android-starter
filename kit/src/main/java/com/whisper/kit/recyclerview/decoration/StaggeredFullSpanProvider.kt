package com.whisper.kit.recyclerview.decoration

import androidx.annotation.IntRange

/**
 * 为 StaggeredGridLayoutManager Decoration 提供稳定的 full-span 拓扑.
 *
 * Staggered Decoration 需要区分主轴起始拓扑时, Adapter 应实现该接口, 并在绑定相同 [position] 时使用
 * 同一个查询结果设置 `StaggeredGridLayoutManager.LayoutParams.isFullSpan`. 缺少该接口时 Decoration
 * 会禁用依赖起始拓扑的主轴行为, 交叉轴行为不受影响. 已实现该接口时, 查询结果必须只取决于当前 Adapter 数据,
 * 不能依赖 itemView 是否已经创建或绑定; 结果与 LayoutParams 不一致属于编程错误.
 *
 * @author whisper
 * @since 2026/09/02
 */
fun interface StaggeredFullSpanProvider {

    /**
     * 返回指定 Adapter position 是否占据全部 span.
     */
    fun isFullSpan(@IntRange(from = 0) position: Int): Boolean
}
