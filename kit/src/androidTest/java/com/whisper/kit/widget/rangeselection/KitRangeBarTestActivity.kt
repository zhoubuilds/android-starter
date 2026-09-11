package com.whisper.kit.widget.rangeselection

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import com.whisper.kit.test.R

/**
 * 为范围选择控件提供固定像素尺寸的硬件加速测试窗口.
 *
 * @author whisper
 * @since 2026/09/09
 */
class KitRangeBarTestActivity : Activity() {

    companion object {
        const val RANGE_ID: Int = 0x00A00101
        private const val BEFORE_ID: Int = 0x00A00100
        private const val AFTER_ID: Int = 0x00A00102
    }

    lateinit var rangeBar: KitRangeBar
        private set

    lateinit var beforeRange: View
        private set

    lateinit var afterRange: View
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 本窗口无文本编辑, 隔离输入法对硬件方向键的前置消费, 保留系统窗口与焦点分发路径.
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        rangeBar = KitRangeBar(this).apply {
            id = RANGE_ID
            nextFocusForwardId = AFTER_ID
            startThumbLabel = getText(R.string.kit_test_range_start_label)
            endThumbLabel = getText(R.string.kit_test_range_end_label)
            setBarBackground(ColorDrawable(Color.GRAY))
            setBarForeground(ColorDrawable(Color.RED))
            setThumb(GradientDrawable().apply {
                setSize(20, 20)
                setColor(Color.BLUE)
            })
            setSelectedRange(20, 80)
        }
        beforeRange = Button(this).apply {
            id = BEFORE_ID
            setText(R.string.kit_test_range_before)
            isFocusableInTouchMode = true
            nextFocusForwardId = RANGE_ID
        }
        afterRange = Button(this).apply {
            id = AFTER_ID
            setText(R.string.kit_test_range_after)
            isFocusableInTouchMode = true
        }
        val content = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(20, 20, 20, 20)
            addView(beforeRange, FrameLayout.LayoutParams(220, 60))
            addView(rangeBar, FrameLayout.LayoutParams(220, 40).apply { topMargin = 80 })
            addView(afterRange, FrameLayout.LayoutParams(220, 60).apply { topMargin = 140 })
        }
        setContentView(content)
    }
}
