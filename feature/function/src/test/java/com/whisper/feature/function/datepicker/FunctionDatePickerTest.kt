package com.whisper.feature.function.datepicker

import android.view.ContextThemeWrapper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.whisper.feature.function.R
import com.whisper.foundation.R as FoundationR
import com.whisper.kit.recyclerview.wheel.WheelLayoutManager
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 验证日期滚轮的范围状态及滚动后的年月日联动.
 *
 * @author whisper
 * @since 2026/10/22
 */
@RunWith(RobolectricTestRunner::class)
class FunctionDatePickerTest {

    @Test
    fun setRange_whenInitialDateIsBeforePartialRange_rendersClampedSelection() {
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            FoundationR.style.Foundation_Theme,
        )
        val picker = FunctionDatePicker(context)

        picker.setRange(
            startDate = date(2024, 3, 12),
            endDate = date(2024, 8, 20),
        )
        picker.setDate(date(2024, 1, 1))

        assertEquals(DateValue(2024, 3, 12), DateValue.from(picker.selectedDate))
    }

    @Test
    fun setDate_whenInputIsAfterPartialRange_centersEndBoundaryOnWheels() {
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            FoundationR.style.Foundation_Theme,
        )
        val picker = FunctionDatePicker(context)
        picker.setRange(date(2024, 3, 12), date(2024, 8, 20))
        picker.setDate(date(2024, 3, 12))
        picker.setDate(date(2024, 10, 1))
        layout(picker)

        val monthWheel: RecyclerView = picker.findViewById(R.id.function_date_picker_month)
        val dayWheel: RecyclerView = picker.findViewById(R.id.function_date_picker_day)

        assertEquals(DateValue(2024, 8, 20), DateValue.from(picker.selectedDate))
        assertEquals(5, (monthWheel.layoutManager as WheelLayoutManager).findCenterChildPosition())
        assertEquals(19, (dayWheel.layoutManager as WheelLayoutManager).findCenterChildPosition())
    }

    @Test
    fun scrollWheels_whenMonthAndYearChange_updatesDependentDaySelection() {
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            FoundationR.style.Foundation_Theme,
        )
        val picker = FunctionDatePicker(context)
        picker.setRange(date(2024, 1, 1), date(2025, 12, 31))
        picker.setDate(date(2024, 1, 31))
        layout(picker)

        val monthWheel: RecyclerView = picker.findViewById(R.id.function_date_picker_month)
        val yearWheel: RecyclerView = picker.findViewById(R.id.function_date_picker_year)
        val itemHeight: Int = checkNotNull(monthWheel.getChildAt(0)).height
        monthWheel.scrollBy(0, itemHeight)

        assertEquals(DateValue(2024, 2, 29), DateValue.from(picker.selectedDate))

        yearWheel.scrollBy(0, itemHeight)

        assertEquals(DateValue(2025, 2, 28), DateValue.from(picker.selectedDate))
    }

    private fun layout(picker: FunctionDatePicker) {
        picker.measure(
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST),
        )
        picker.layout(0, 0, picker.measuredWidth, picker.measuredHeight)
    }

    private fun date(year: Int, month: Int, day: Int): Calendar = GregorianCalendar().apply {
        clear()
        set(year, month - 1, day)
    }
}
