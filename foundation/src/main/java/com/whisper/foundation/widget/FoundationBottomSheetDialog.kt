package com.whisper.foundation.widget

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.whisper.foundation.R

/**
 * 统一应用 Foundation 样式、关闭策略和窗口高度约束的 BottomSheetDialog 基类.
 *
 * 返回键与外部点击可以独立配置, 手势下拉关闭默认禁用. 最大允许高度在 Dialog 已进入窗口后计算,
 * 子类通过 [getMaximumAllowedHeight] 根据当前可用高度进一步收窄, 并可通过
 * [isSwipeDismissEnabled] 决定是否允许手势下拉关闭.
 *
 * @author whisper
 * @since 2026/10/22
 */
open class FoundationBottomSheetDialog(
    context: Context,
) : BottomSheetDialog(context, R.style.Foundation_ThemeOverlay_BottomSheetDialog) {

    private val blockedBackPressCallback: OnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    /**
     * 按返回键时是否关闭 Dialog.
     */
    var isBackDismissEnabled: Boolean = true
        private set

    /**
     * 点击 Bottom Sheet 外部区域时是否关闭 Dialog.
     */
    var isOutsideDismissEnabled: Boolean = true
        private set

    init {
        onBackPressedDispatcher.addCallback(blockedBackPressCallback)
        applyDismissPolicy()
    }

    override fun onStart() {
        super.onStart()
        applyDismissPolicy(isSwipeDismissEnabled())
        val bottomSheet: FrameLayout = findViewById(
            com.google.android.material.R.id.design_bottom_sheet,
        ) ?: return
        bottomSheet.post {
            applyMaximumAllowedHeight(bottomSheet)
        }
    }

    /**
     * 设置按返回键时是否关闭 Dialog.
     */
    fun setBackDismissEnabled(enabled: Boolean) {
        isBackDismissEnabled = enabled
        applyDismissPolicy(getActiveSwipeDismissEnabled())
    }

    /**
     * 设置点击 Bottom Sheet 外部区域时是否关闭 Dialog.
     */
    fun setOutsideDismissEnabled(enabled: Boolean) {
        isOutsideDismissEnabled = enabled
        applyDismissPolicy(getActiveSwipeDismissEnabled())
    }

    /**
     * 返回是否允许用户通过下拉手势关闭 Bottom Sheet, 默认不允许.
     *
     * 返回键和外部点击关闭策略不受该值影响.
     */
    protected open fun isSwipeDismissEnabled(): Boolean = false

    /**
     * 返回本次窗口布局允许的最大 Bottom Sheet 高度.
     *
     * @param availableHeight 当前窗口扣除可见系统栏、刘海和输入法后的可用高度.
     * @return 大于 0 且不超过 availableHeight 的最大高度, 默认使用全部可用高度.
     */
    protected open fun getMaximumAllowedHeight(availableHeight: Int): Int = availableHeight

    private fun getActiveSwipeDismissEnabled(): Boolean =
        isShowing && isSwipeDismissEnabled()

    private fun applyDismissPolicy(isSwipeDismissEnabled: Boolean = false) {
        val isCancelableByAnyConfiguredAction: Boolean =
            isBackDismissEnabled || isOutsideDismissEnabled || isSwipeDismissEnabled
        super.setCancelable(isCancelableByAnyConfiguredAction)
        super.setCanceledOnTouchOutside(isOutsideDismissEnabled)
        blockedBackPressCallback.isEnabled = !isBackDismissEnabled
        behavior.isDraggable = isSwipeDismissEnabled
    }

    private fun applyMaximumAllowedHeight(bottomSheet: FrameLayout) {
        val availableHeight: Int = getCurrentAvailableHeight(bottomSheet)
        if (availableHeight <= 0) {
            return
        }
        val maximumAllowedHeight: Int = getMaximumAllowedHeight(availableHeight)
        require(maximumAllowedHeight in 1..availableHeight) {
            "Maximum allowed height must be in 1..availableHeight"
        }
        behavior.maxHeight = maximumAllowedHeight
        bottomSheet.requestLayout()
    }

    private fun getCurrentAvailableHeight(bottomSheet: FrameLayout): Int {
        val decorView = window?.decorView ?: bottomSheet.rootView
        val windowHeight: Int = decorView.height.takeIf { height: Int -> height > 0 }
            ?: context.resources.displayMetrics.heightPixels
        val insets = ViewCompat.getRootWindowInsets(bottomSheet)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime(),
        )
        return (windowHeight - (insets?.top ?: 0) - (insets?.bottom ?: 0)).coerceAtLeast(0)
    }
}
