package com.whisper.kit.recyclerview.listener

import androidx.recyclerview.widget.RecyclerView

/**
 * 添加 RecyclerView item 内子 View 点击监听器.
 *
 * 该扩展会创建 [OnItemTouchDispatchClickListener] 并添加到当前 RecyclerView.
 * 返回的监听器可用于后续调用 [RecyclerView.removeOnItemTouchListener] 移除监听.
 *
 * @param listener item 内子 View 点击回调.
 * @return 已添加到当前 RecyclerView 的触摸监听器.
 */
fun RecyclerView.addOnItemChildClickListener(
    listener: OnItemClickListener,
): OnItemTouchDispatchClickListener =
    OnItemTouchDispatchClickListener(
        recyclerView = this,
        listener = listener,
    ).also(::addOnItemTouchListener)
