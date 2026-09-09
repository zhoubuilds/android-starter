package com.whisper.kit.widget

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.RadioGroup
import com.whisper.kit.test.R
import kotlin.math.roundToInt

/**
 * 提供可从桌面打开的短码输入和线条绘制手动测试页面, 不参与正式应用打包.
 *
 * @author whisper
 * @since 2026/09/09
 */
class KitCodeInputManualActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.kit_test_code_input_manual)
        val content: View = findViewById(R.id.kit_test_manual_content)
        val numeric: KitCodeInputEditText = findViewById(R.id.kit_test_manual_numeric)
        val ascii: KitCodeInputEditText = findViewById(R.id.kit_test_manual_ascii)
        val finishEditing: () -> Unit = {
            content.requestFocus()
            getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(content.windowToken, 0)
        }
        content.setOnClickListener { finishEditing() }
        findViewById<View>(R.id.kit_test_manual_clear).setOnClickListener {
            numeric.text?.clear()
            ascii.text?.clear()
        }
        findViewById<View>(R.id.kit_test_manual_done).setOnClickListener { finishEditing() }
        numeric.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                ascii.requestFocus()
            } else {
                false
            }
        }
        ascii.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishEditing()
                true
            } else {
                false
            }
        }
        configureLineControls(finishEditing)
        if (savedInstanceState == null) {
            content.requestFocus()
        }
    }

    private fun configureLineControls(finishEditing: () -> Unit) {
        val line: KitLineView = findViewById(R.id.kit_test_manual_line)
        val pattern: RadioGroup = findViewById(R.id.kit_test_manual_line_pattern)
        val orientation: RadioGroup = findViewById(R.id.kit_test_manual_line_orientation)
        val enabled: CheckBox = findViewById(R.id.kit_test_manual_line_enabled)
        val selected: CheckBox = findViewById(R.id.kit_test_manual_line_selected)
        val density: Float = resources.displayMetrics.density

        pattern.setOnCheckedChangeListener { _, checkedId ->
            finishEditing()
            line.dotWidth = if (checkedId == R.id.kit_test_manual_line_mixed) 2f * density else 0f
            line.dashWidth = 8f * density
            line.gapWidth = if (checkedId == R.id.kit_test_manual_line_solid) 0f else 4f * density
        }
        orientation.setOnCheckedChangeListener { _, checkedId ->
            finishEditing()
            val vertical: Boolean = checkedId == R.id.kit_test_manual_line_vertical
            val thickness: Int = (3f * density).roundToInt()
            line.orientation = if (vertical) KitLineView.VERTICAL else KitLineView.HORIZONTAL
            // 方向属性不交换布局宽高, 由此处的调用方同步设置预览尺寸.
            line.layoutParams = FrameLayout.LayoutParams(
                if (vertical) thickness else ViewGroup.LayoutParams.MATCH_PARENT,
                if (vertical) ViewGroup.LayoutParams.MATCH_PARENT else thickness,
                Gravity.CENTER,
            )
        }
        enabled.setOnCheckedChangeListener { _, checked ->
            finishEditing()
            line.isEnabled = checked
        }
        selected.setOnCheckedChangeListener { _, checked ->
            finishEditing()
            line.isSelected = checked
        }
    }
}
