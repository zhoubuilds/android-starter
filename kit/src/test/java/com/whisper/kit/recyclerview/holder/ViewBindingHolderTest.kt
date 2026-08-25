package com.whisper.kit.recyclerview.holder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 验证 ViewBindingHolder 的 Kotlin 调用形态.
 *
 * @author whisper
 * @since 2026/07/30
 */
class ViewBindingHolderTest {

    /**
     * 验证 [ViewBindingInflater] 可通过 lambda 构造.
     */
    @Test
    fun viewBindingInflater_supportsLambdaCreation() {
        val inflater: ViewBindingInflater<TestBinding> = ViewBindingInflater { _, parent, _ ->
            TestBinding(parent)
        }

        assertNotNull(inflater)
    }

    /**
     * 验证创建函数可以接收 ViewBinding inflate 方法引用.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun compileCreateWithBindingInflate(parent: ViewGroup) {
        ViewBindingHolder.create(parent, TestBinding::inflate)
    }

    /**
     * 测试用 ViewBinding.
     */
    private class TestBinding(
        private val root: View,
    ) : ViewBinding {

        override fun getRoot(): View = root

        companion object {

            /**
             * 模拟生成 binding 的 inflate 方法.
             */
            @Suppress("UNUSED_PARAMETER")
            fun inflate(
                inflater: LayoutInflater,
                parent: ViewGroup,
                attachToParent: Boolean,
            ): TestBinding = TestBinding(parent)
        }
    }
}
