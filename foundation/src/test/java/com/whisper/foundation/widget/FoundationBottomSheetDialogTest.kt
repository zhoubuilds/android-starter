package com.whisper.foundation.widget

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.whisper.foundation.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * 验证 Bottom Sheet 返回键关闭策略可以独立于外部点击策略工作.
 *
 * @author whisper
 * @since 2026/10/22
 */
@RunWith(RobolectricTestRunner::class)
class FoundationBottomSheetDialogTest {

    @Test
    fun backPress_whenBackDismissIsDisabledAndOutsideDismissIsEnabled_keepsDialogVisible() {
        val activity: AppCompatActivity = createActivity()
        val dialog = FoundationBottomSheetDialog(activity)
        dialog.setContentView(View(activity))
        dialog.setBackDismissEnabled(false)
        dialog.setOutsideDismissEnabled(true)
        dialog.show()

        dialog.onBackPressedDispatcher.onBackPressed()

        assertTrue(dialog.isShowing)
        assertFalse(dialog.isBackDismissEnabled)
        assertTrue(dialog.isOutsideDismissEnabled)
        dialog.dismiss()
    }

    @Test
    fun backPress_whenBackDismissIsEnabled_closesDialog() {
        val activity: AppCompatActivity = createActivity()
        val dialog = FoundationBottomSheetDialog(activity)
        dialog.setContentView(View(activity))
        dialog.show()

        dialog.onBackPressedDispatcher.onBackPressed()

        assertFalse(dialog.isShowing)
    }

    @Test
    fun show_whenSwipeDismissUsesDefaultPolicy_disablesDragging() {
        val activity: AppCompatActivity = createActivity()
        val dialog = FoundationBottomSheetDialog(activity)
        dialog.setContentView(View(activity))

        dialog.show()

        assertFalse(dialog.behavior.isDraggable)
        dialog.dismiss()
    }

    @Test
    fun show_whenOnlySwipeDismissIsEnabled_keepsSheetDraggableAndHideable() {
        val activity: AppCompatActivity = createActivity()
        val dialog = SwipeDismissEnabledDialog(activity)
        dialog.setContentView(View(activity))
        dialog.setBackDismissEnabled(false)
        dialog.setOutsideDismissEnabled(false)

        dialog.show()

        assertTrue(dialog.behavior.isDraggable)
        assertTrue(dialog.behavior.isHideable)
        dialog.dismiss()
    }

    private fun createActivity(): AppCompatActivity {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Foundation_Theme)
        return controller.setup().get()
    }

    /**
     * 只开放手势下拉关闭的测试 Dialog.
     */
    private class SwipeDismissEnabledDialog(context: Context) :
        FoundationBottomSheetDialog(context) {

        override fun isSwipeDismissEnabled(): Boolean = true
    }
}
