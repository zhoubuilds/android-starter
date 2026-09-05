package com.whisper.kit.recyclerview.listener

import androidx.recyclerview.widget.RecyclerView

/**
 * 添加 RecyclerView item 内子 View 点击手势监听器.
 *
 * 该扩展会创建 [ItemClickTouchListener] 并添加到当前 RecyclerView.
 * 返回的监听器可用于在当前手势结束后调用 [RecyclerView.removeOnItemTouchListener] 移除监听.
 * 该监听器保持完全无侵入, 只观察指针手势, 不替代 View 原生的无障碍或键盘点击链路.
 * 长按占用规则、事件序列要求和自定义触摸边界见 [ItemClickTouchListener].
 *
 * @param listener item 内子 View 点击回调.
 * @return 已添加到当前 RecyclerView 的触摸监听器.
 */
fun RecyclerView.addOnItemChildClickListener(
    listener: OnItemClickListener,
): ItemClickTouchListener =
    ItemClickTouchListener(
        recyclerView = this,
        listener = listener,
    ).also(::addOnItemTouchListener)

/**
 * 添加 RecyclerView item 内子 View 长按手势监听器.
 *
 * 该扩展会创建 [ItemLongClickTouchListener] 并添加到当前 RecyclerView.
 * 返回的监听器可用于在当前手势结束后调用 [RecyclerView.removeOnItemTouchListener] 移除监听.
 * 目标 View 必须满足 `longClickable && enabled`; 监听器只旁路观察指针长按, 不消费事件.
 * 与旁路点击的协调规则、事件序列要求和自定义触摸边界见 [ItemLongClickTouchListener].
 *
 * @param listener item 内子 View 长按回调.
 * @return 已添加到当前 RecyclerView 的触摸监听器.
 */
fun RecyclerView.addOnItemChildLongClickListener(
    listener: OnItemLongClickListener,
): ItemLongClickTouchListener =
    ItemLongClickTouchListener(
        recyclerView = this,
        listener = listener,
    ).also(::addOnItemTouchListener)
