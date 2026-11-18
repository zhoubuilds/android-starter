package com.whisper.feature.function.datepicker

import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证日期选择用例在纪元边界两侧生成有序范围.
 *
 * @author whisper
 * @since 2026/10/22
 */
class DatePickerDemoRangeTest {

    @Test
    fun from_whenCurrentDateIsOnOrAfterEpoch_startsAtEpochAndAddsNaturalYear() {
        val range: DatePickerDemoRange = DatePickerDemoRange.from(date(2024, 2, 29))

        assertEquals(DateValue(1970, 1, 1), range.startDate)
        assertEquals(DateValue(2025, 2, 28), range.endDate)
    }

    @Test
    fun from_whenCurrentDateEqualsEpoch_endsOneNaturalYearLater() {
        val range: DatePickerDemoRange = DatePickerDemoRange.from(date(1970, 1, 1))

        assertEquals(DateValue(1970, 1, 1), range.startDate)
        assertEquals(DateValue(1971, 1, 1), range.endDate)
    }

    @Test
    fun from_whenCurrentDateIsBeforeEpoch_usesCurrentDateAsStartAndEpochAsEnd() {
        val range: DatePickerDemoRange = DatePickerDemoRange.from(date(1969, 12, 31))

        assertEquals(DateValue(1969, 12, 31), range.startDate)
        assertEquals(DateValue(1970, 1, 1), range.endDate)
    }

    private fun date(year: Int, month: Int, day: Int): Calendar = GregorianCalendar().apply {
        clear()
        set(year, month - 1, day)
    }
}
