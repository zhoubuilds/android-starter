package com.whisper.kit.widget

import android.app.Activity
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import com.whisper.kit.test.R
import com.whisper.kit.widget.rangeselection.KitRangeBar
import kotlin.math.roundToInt

/**
 * 提供可从桌面打开的范围选择, 短码输入和线条绘制手动测试页面, 不参与正式应用打包.
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
        configureRangeControls(finishEditing)
        configureLineControls(finishEditing)
        if (savedInstanceState == null) {
            content.requestFocus()
        }
    }

    private fun configureRangeControls(finishEditing: () -> Unit) {
        val range: KitRangeBar = findViewById(R.id.kit_test_manual_range)
        val available: TextView = findViewById(R.id.kit_test_manual_range_available)
        val value: TextView = findViewById(R.id.kit_test_manual_range_value)
        val status: TextView = findViewById(R.id.kit_test_manual_range_status)
        val enabled: CheckBox = findViewById(R.id.kit_test_manual_range_enabled)
        val rtl: CheckBox = findViewById(R.id.kit_test_manual_range_rtl)
        val availableStart = resources.getInteger(R.integer.kit_test_range_available_start)
        val total = resources.getInteger(R.integer.kit_test_range_total)
        available.text = getString(R.string.kit_test_manual_range_available, availableStart, total)
        range.setBarBackground(
            UnavailableRangeTrackDrawable(
                availableStart = availableStart,
                total = total,
                availableColor = getColor(R.color.kit_test_range_track_available),
                unavailableColor = getColor(R.color.kit_test_range_track_unavailable),
            ),
        )
        value.text = getString(R.string.kit_test_manual_range_value, range.start, range.end)
        range.setOnRangeChangedListener { start, end ->
            value.text = getString(R.string.kit_test_manual_range_value, start, end)
        }
        range.setOnInteractionListener(object : KitRangeBar.OnInteractionListener {
            override fun onInteractionStart(start: Int, end: Int) {
                getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(range.windowToken, 0)
                status.setText(R.string.kit_test_manual_range_active)
            }

            override fun onInteractionEnd(start: Int, end: Int) {
                status.setText(R.string.kit_test_manual_range_ended)
            }

            override fun onInteractionCancel(start: Int, end: Int) {
                status.setText(R.string.kit_test_manual_range_cancelled)
            }
        })
        findViewById<View>(R.id.kit_test_manual_range_overlap).setOnClickListener {
            finishEditing()
            range.setSelectedRange(50, 50)
            status.setText(R.string.kit_test_manual_range_idle)
        }
        findViewById<View>(R.id.kit_test_manual_range_reset).setOnClickListener {
            finishEditing()
            range.setSelectedRange(availableStart, 80)
            status.setText(R.string.kit_test_manual_range_idle)
        }
        enabled.setOnCheckedChangeListener { _, checked ->
            finishEditing()
            range.isEnabled = checked
        }
        rtl.setOnCheckedChangeListener { _, checked ->
            finishEditing()
            range.layoutDirection = if (checked) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
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

/** 手动测试页的轨道背景, 标出数值下界之前的不可选区域. */
private class UnavailableRangeTrackDrawable(
    private val availableStart: Int,
    private val total: Int,
    availableColor: Int,
    unavailableColor: Int,
) : Drawable() {

    private val availablePaint: Paint = Paint().apply { color = availableColor }
    private val unavailablePaint: Paint = Paint().apply { color = unavailableColor }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, availablePaint)
        val unavailableWidth = ((bounds.width().toLong() * availableStart + total / 2) / total).toInt()
        val left = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right - unavailableWidth else bounds.left
        canvas.drawRect(
            left.toFloat(),
            bounds.top.toFloat(),
            (left + unavailableWidth).toFloat(),
            bounds.bottom.toFloat(),
            unavailablePaint,
        )
    }

    override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean = true

    override fun setAlpha(alpha: Int) {
        availablePaint.alpha = alpha
        unavailablePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        availablePaint.colorFilter = colorFilter
        unavailablePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
