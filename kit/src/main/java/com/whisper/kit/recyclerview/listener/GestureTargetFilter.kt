package com.whisper.kit.recyclerview.listener

import android.view.View

/**
 * RecyclerView item 内手势目标过滤器.
 *
 * 该接口仅供手势分发内部实现使用, 用于判断命中的 itemView 或后代 View 是否可以作为
 * 当前手势的回调目标.
 */
internal fun interface GestureTargetFilter {

    /**
     * 判断 [view] 是否可以作为当前手势目标.
     *
     * @param view 当前命中的 itemView 或后代 View.
     * @return `true` 表示可以回调给调用方.
     */
    fun filter(view: View): Boolean
}
