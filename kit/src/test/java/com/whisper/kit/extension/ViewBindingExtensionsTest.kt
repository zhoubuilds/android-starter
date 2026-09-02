package com.whisper.kit.extension

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewbinding.ViewBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

/**
 * 验证 Activity、Dialog 和 Fragment 的 ViewBinding 委托生命周期.
 *
 * @author whisper
 * @since 2026/09/01
 */
@RunWith(RobolectricTestRunner::class)
class ViewBindingExtensionsTest {

    @Test
    fun activityViewBinding_repeatedAccess_createsBindingOnce() {
        val controller: ActivityController<TestActivity> = Robolectric.buildActivity(
            TestActivity::class.java,
        ).setup()
        val activity: TestActivity = controller.get()

        assertEquals(0, activity.bindingCreationCount)

        val firstBinding: TestViewBinding = activity.binding
        val secondBinding: TestViewBinding = activity.binding

        assertSame(firstBinding, secondBinding)
        assertEquals(1, activity.bindingCreationCount)

        controller.pause().stop().destroy()
    }

    @Test
    fun dialogViewBinding_repeatedAccess_createsBindingOnce() {
        val dialog = TestDialog(RuntimeEnvironment.getApplication())

        assertEquals(0, dialog.bindingCreationCount)

        val firstBinding: TestViewBinding = dialog.binding
        val secondBinding: TestViewBinding = dialog.binding

        assertSame(firstBinding, secondBinding)
        assertEquals(1, dialog.bindingCreationCount)
    }

    @Test
    fun fragmentViewBinding_viewRecreated_clearsAndCreatesBindingForNewView() {
        val controller: ActivityController<FragmentActivity> = Robolectric.buildActivity(
            FragmentActivity::class.java,
        ).setup()
        val activity: FragmentActivity = controller.get()
        val container = FrameLayout(activity).apply {
            id = View.generateViewId()
        }
        val fragment = TestFragment()
        activity.setContentView(container)

        val beforeViewException: IllegalStateException = assertThrows(
            IllegalStateException::class.java,
        ) {
            fragment.binding
        }

        assertEquals(0, fragment.bindingCreationCount)

        activity.supportFragmentManager.beginTransaction()
            .add(container.id, fragment)
            .commitNow()

        val firstBinding: TestViewBinding = fragment.binding

        assertSame(firstBinding, fragment.binding)
        assertSame(fragment.requireView(), firstBinding.getRoot())
        assertEquals(1, fragment.bindingCreationCount)
        assertEquals(expectedBindingError(), beforeViewException.message)

        activity.supportFragmentManager.beginTransaction()
            .detach(fragment)
            .commitNow()

        val afterDestroyViewException: IllegalStateException = assertThrows(
            IllegalStateException::class.java,
        ) {
            fragment.binding
        }

        activity.supportFragmentManager.beginTransaction()
            .attach(fragment)
            .commitNow()

        val recreatedBinding: TestViewBinding = fragment.binding

        assertNotSame(firstBinding, recreatedBinding)
        assertSame(fragment.requireView(), recreatedBinding.getRoot())
        assertEquals(2, fragment.bindingCreationCount)
        assertEquals(expectedBindingError(), afterDestroyViewException.message)

        controller.pause().stop().destroy()
    }

    private fun expectedBindingError(): String {
        return "ViewBinding property 'binding' is only available between " +
            "onViewCreated() and onDestroyView()."
    }

    /**
     * 用于验证 Activity Binding 缓存的测试组件.
     */
    class TestActivity : Activity() {

        var bindingCreationCount: Int = 0
            private set

        @get:MainThread
        val binding: TestViewBinding by viewBinding { inflater: LayoutInflater ->
            bindingCreationCount++
            TestViewBinding(FrameLayout(inflater.context))
        }
    }

    /**
     * 用于验证 Dialog Binding 缓存的测试组件.
     */
    class TestDialog(context: Context) : Dialog(context) {

        var bindingCreationCount: Int = 0
            private set

        @get:MainThread
        val binding: TestViewBinding by viewBinding { inflater: LayoutInflater ->
            bindingCreationCount++
            TestViewBinding(FrameLayout(inflater.context))
        }
    }

    /**
     * 用于验证 Fragment View 生命周期重建的测试组件.
     */
    class TestFragment : Fragment() {

        var bindingCreationCount: Int = 0
            private set

        @get:MainThread
        val binding: TestViewBinding by viewBinding { view: View ->
            bindingCreationCount++
            TestViewBinding(view)
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View {
            return FrameLayout(requireContext())
        }
    }

    /**
     * 测试用轻量 ViewBinding.
     */
    class TestViewBinding(
        private val rootView: View,
    ) : ViewBinding {

        override fun getRoot(): View {
            return rootView
        }
    }
}
