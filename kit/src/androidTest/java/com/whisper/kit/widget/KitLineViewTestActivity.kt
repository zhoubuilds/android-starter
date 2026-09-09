package com.whisper.kit.widget

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout

/**
 * 为线条控件提供白底、固定像素尺寸的硬件加速测试窗口.
 *
 * @author whisper
 * @since 2026/09/09
 */
class KitLineViewTestActivity : Activity() {

    lateinit var line: KitLineView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        line = KitLineView(this)
        val content: FrameLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(20, 20, 20, 20)
            addView(line, FrameLayout.LayoutParams(120, 120))
        }
        setContentView(content)
    }
}
