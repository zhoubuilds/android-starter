package com.whisper.kit.recyclerview.holder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewbinding.ViewBinding
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证 ViewBindingHolder 的 Kotlin 调用形态.
 *
 * @author whisper
 * @since 2026/07/30
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    @Test
    fun create_whenInvoked_usesParentContextAndKeepsBindingRootUnattached() {
        val parent: ViewGroup = FrameLayout(RuntimeEnvironment.getApplication())
        lateinit var receivedInflater: LayoutInflater
        lateinit var receivedParent: ViewGroup
        var receivedAttachToParent: Boolean = true
        lateinit var createdBinding: TestBinding
        val bindingInflater: ViewBindingInflater<TestBinding> = ViewBindingInflater { inflater, targetParent, attach ->
            receivedInflater = inflater
            receivedParent = targetParent
            receivedAttachToParent = attach
            val root: View = View(targetParent.context)
            if (attach) {
                targetParent.addView(root)
            }
            createdBinding = TestBinding(root)
            createdBinding
        }

        val holder: ViewBindingHolder<TestBinding> = ViewBindingHolder.create(parent, bindingInflater)

        assertSame(parent.context, receivedInflater.context)
        assertSame(parent, receivedParent)
        assertFalse(receivedAttachToParent)
        assertSame(createdBinding, holder.binding)
        assertSame(createdBinding.root, holder.itemView)
        assertNull(holder.itemView.parent)
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
