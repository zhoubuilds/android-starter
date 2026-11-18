package com.whisper.feature.function.datepicker

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.whisper.feature.function.databinding.FunctionDatePickerBinding
import com.whisper.foundation.recyclerview.adapter.IntSequenceAdapter
import com.whisper.foundation.recyclerview.decoration.WheelTextColorDecoration
import com.whisper.kit.recyclerview.wheel.WheelLayoutManager
import com.whisper.kit.recyclerview.wheel.WheelSnapHelper
import java.util.Calendar

/**
 * 使用三个纵向滚轮选择范围内的公历年月日.
 *
 * 年月变化会先归一化整体日期, 再更新下游序列和位置. 组件不在某个滚轮自己的中心回调中
 * 更新同一个 RecyclerView Adapter, 避免进入 RecyclerView 布局或滚动临界区.
 *
 * @author whisper
 * @since 2026/10/22
 */
class FunctionDatePicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    /**
     * 标识触发当前日期联动的滚轮.
     */
    private enum class Wheel {
        YEAR,
        MONTH,
        DAY,
    }

    private val viewBinding: FunctionDatePickerBinding = FunctionDatePickerBinding.inflate(
        LayoutInflater.from(context),
        this,
    )
    private val yearAdapter: IntSequenceAdapter = IntSequenceAdapter()
    private val monthAdapter: IntSequenceAdapter = IntSequenceAdapter()
    private val dayAdapter: IntSequenceAdapter = IntSequenceAdapter()
    private val state: DatePickerState

    val selectedDate: Calendar
        get() = state.selectedDate.toCalendar()

    init {
        val today: DateValue = DateValue.from(Calendar.getInstance())
        state = DatePickerState(
            startDate = DateValue(getDefaultDatePickerStartYear(today.year), 1, 1),
            endDate = DateValue(today.year + DEFAULT_YEAR_DISTANCE, 12, 31),
            selectedDate = today,
        )
        setupWheel(viewBinding.functionDatePickerYear, yearAdapter, Wheel.YEAR)
        setupWheel(viewBinding.functionDatePickerMonth, monthAdapter, Wheel.MONTH)
        setupWheel(viewBinding.functionDatePickerDay, dayAdapter, Wheel.DAY)
        renderSelection(source = null)
    }

    /**
     * 更新可选日期闭区间, 当前日期超出范围时收敛到最近边界.
     */
    fun setRange(startDate: Calendar, endDate: Calendar) {
        state.updateRange(DateValue.from(startDate), DateValue.from(endDate))
        renderSelection(source = null)
    }

    /**
     * 更新当前日期, 超出范围或自然月天数时收敛到最近合法日期.
     */
    fun setDate(date: Calendar) {
        state.selectDate(DateValue.from(date))
        renderSelection(source = null)
    }

    private fun setupWheel(
        recyclerView: RecyclerView,
        adapter: IntSequenceAdapter,
        wheel: Wheel,
    ) {
        val layoutManager = WheelLayoutManager()
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        WheelSnapHelper().attachToRecyclerView(recyclerView)
        recyclerView.addItemDecoration(
            WheelTextColorDecoration(
                selectedColor = MaterialColors.getColor(
                    this,
                    androidx.appcompat.R.attr.colorPrimary,
                ),
                centerColor = MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurface,
                ),
                edgeColor = MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                ),
                segmentCount = WHEEL_SEGMENT_COUNT,
            ),
        )
        layoutManager.setOnCenterYPositionChangeListener { position: Int ->
            val value: Int = adapter.getValue(position) ?: return@setOnCenterYPositionChangeListener
            val selectionChanged: Boolean = when (wheel) {
                Wheel.YEAR -> state.selectYear(value)
                Wheel.MONTH -> state.selectMonth(value)
                Wheel.DAY -> state.selectDay(value)
            }
            if (selectionChanged) {
                renderSelection(wheel)
            }
        }
    }

    /**
     * 只更新发生级联的下游 Adapter; source 自身保持当前布局调用栈内只读.
     */
    private fun renderSelection(source: Wheel?) {
        if (source == null) {
            yearAdapter.updateSequence(state.yearRange.first, state.yearRange.count())
            scrollToValue(viewBinding.functionDatePickerYear, yearAdapter, state.selectedDate.year)
        }
        if (source == null || source == Wheel.YEAR) {
            monthAdapter.updateSequence(state.monthRange.first, state.monthRange.count())
            scrollToValue(viewBinding.functionDatePickerMonth, monthAdapter, state.selectedDate.month)
        }
        if (source == null || source == Wheel.YEAR || source == Wheel.MONTH) {
            dayAdapter.updateSequence(state.dayRange.first, state.dayRange.count())
            scrollToValue(viewBinding.functionDatePickerDay, dayAdapter, state.selectedDate.day)
        }
    }

    private fun scrollToValue(
        recyclerView: RecyclerView,
        adapter: IntSequenceAdapter,
        value: Int,
    ) {
        val position: Int = checkNotNull(adapter.getPosition(value))
        recyclerView.scrollToPosition(position)
    }

    private companion object {

        private const val WHEEL_SEGMENT_COUNT: Float = 5f
    }
}
