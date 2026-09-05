package com.whisper.kit.recyclerview.listener

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 验证 RecyclerView 点击扩展的 Kotlin 调用形态.
 *
 * @author whisper
 * @since 2026/07/30
 */
class RecyclerViewItemGestureExtensionsTest {

    /**
     * 验证 [OnItemClickListener] 可通过 lambda 构造.
     */
    @Test
    fun onItemClickListener_supportsLambdaCreation() {
        val listener: OnItemClickListener = OnItemClickListener { _, _, _ -> }

        assertNotNull(listener)
    }

    /**
     * 验证 [OnItemLongClickListener] 可通过 lambda 构造.
     */
    @Test
    fun onItemLongClickListener_supportsLambdaCreation() {
        val listener: OnItemLongClickListener = OnItemLongClickListener { _, _, _ -> }

        assertNotNull(listener)
    }

    /**
     * 验证扩展函数参数保持 [OnItemClickListener] 时, 调用方仍可直接传入 lambda.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun compileAddOnItemChildClickListenerLambda(recyclerView: RecyclerView) {
        recyclerView.addOnItemChildClickListener { _, _, _ -> }
    }

    /**
     * 验证长按扩展函数参数保持 [OnItemLongClickListener] 时, 调用方仍可直接传入 lambda.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun compileAddOnItemChildLongClickListenerLambda(recyclerView: RecyclerView) {
        recyclerView.addOnItemChildLongClickListener { _, _, _ -> }
    }
}
