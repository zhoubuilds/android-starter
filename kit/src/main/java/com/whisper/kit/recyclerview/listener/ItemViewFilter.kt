package com.whisper.kit.recyclerview.listener

import android.view.View

/**
 * RecyclerView 子 View 命中过滤器.
 *
 * 该接口仅供点击分发内部实现使用, 用于判断命中的 View 是否应当作为点击目标.
 *
 * @author whisper
 * @since 2026/07/30
 */
internal fun interface ItemViewFilter {

    /**
     * 判断 [view] 是否可以作为点击目标.
     *
     * @param view 当前命中的 View.
     * @return `true` 表示可以回调给调用方.
     */
    fun filter(view: View): Boolean
}
