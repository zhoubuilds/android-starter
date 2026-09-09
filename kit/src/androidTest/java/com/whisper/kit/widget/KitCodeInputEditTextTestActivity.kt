package com.whisper.kit.widget

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.whisper.kit.test.R

/**
 * 为分格输入控件提供真实窗口与布局生命周期的测试页面.
 *
 * @author whisper
 * @since 2026/09/08
 */
class KitCodeInputEditTextTestActivity : Activity() {

    lateinit var input: KitCodeInputEditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density: Float = resources.displayMetrics.density
        input = KitCodeInputEditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_NUMBER
            showSoftInputOnFocus = false
            isCursorVisible = false
            itemHeight = 52f * density
            itemSpacing = 8f * density
            itemBackground = getDrawable(R.drawable.kit_test_code_item_background)
            setText("1234")
        }
        val content: LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            addView(TextView(this@KitCodeInputEditTextTestActivity).apply {
                setText(R.string.kit_test_code_input_label)
                labelFor = input.id
            })
            addView(
                input,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (72f * density).toInt(),
                ),
            )
        }
        setContentView(content)
        content.requestFocus()
    }
}
