package com.whisper.feature.function.datepicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 验证日期范围边界、大小月和闰年的联动归一化.
 *
 * @author whisper
 * @since 2026/10/22
 */
class DatePickerStateTest {

    @Test
    fun defaultStartYear_whenCurrentYearIsAtBeginningOfCommonEra_staysPositive() {
        assertEquals(1, getDefaultDatePickerStartYear(1))
        assertEquals(1, getDefaultDatePickerStartYear(100))
        assertEquals(1, getDefaultDatePickerStartYear(101))
        assertEquals(2, getDefaultDatePickerStartYear(102))
    }

    @Test
    fun selectMonth_whenMovingFromJanuary31ToLeapFebruary_clampsToFebruary29() {
        val state = DatePickerState(
            startDate = DateValue(2024, 1, 1),
            endDate = DateValue(2024, 12, 31),
            selectedDate = DateValue(2024, 1, 31),
        )

        state.selectMonth(2)

        assertEquals(DateValue(2024, 2, 29), state.selectedDate)
        assertEquals(1..29, state.dayRange)
    }

    @Test
    fun selectYear_whenLeavingLeapYear_clampsFebruary29ToFebruary28() {
        val state = DatePickerState(
            startDate = DateValue(2023, 1, 1),
            endDate = DateValue(2025, 12, 31),
            selectedDate = DateValue(2024, 2, 29),
        )

        state.selectYear(2025)

        assertEquals(DateValue(2025, 2, 28), state.selectedDate)
    }

    @Test
    fun updateRange_whenSelectionIsOutsidePartialBoundary_clampsAllFields() {
        val state = DatePickerState(
            startDate = DateValue(2020, 1, 1),
            endDate = DateValue(2030, 12, 31),
            selectedDate = DateValue(2024, 1, 1),
        )

        state.updateRange(
            startDate = DateValue(2024, 3, 12),
            endDate = DateValue(2024, 8, 20),
        )

        assertEquals(DateValue(2024, 3, 12), state.selectedDate)
        assertEquals(3..8, state.monthRange)
        assertEquals(12..31, state.dayRange)
    }

    @Test
    fun selectDate_whenInputIsOutsidePartialRange_clampsToChronologicalBoundary() {
        val state = DatePickerState(
            startDate = DateValue(2024, 3, 12),
            endDate = DateValue(2024, 8, 20),
            selectedDate = DateValue(2024, 6, 1),
        )

        state.selectDate(DateValue(2024, 10, 1))
        assertEquals(DateValue(2024, 8, 20), state.selectedDate)

        state.selectDate(DateValue(2023, 8, 31))
        assertEquals(DateValue(2024, 3, 12), state.selectedDate)
    }

    @Test
    fun updateRange_whenSelectionIsAfterNewEnd_clampsToEndDate() {
        val state = DatePickerState(
            startDate = DateValue(2024, 1, 1),
            endDate = DateValue(2024, 12, 31),
            selectedDate = DateValue(2024, 10, 1),
        )

        state.updateRange(DateValue(2024, 3, 12), DateValue(2024, 8, 20))

        assertEquals(DateValue(2024, 8, 20), state.selectedDate)
        assertEquals(3..8, state.monthRange)
        assertEquals(1..20, state.dayRange)
    }

    @Test
    fun updateRange_whenStartIsAfterEnd_rejectsRange() {
        val state = DatePickerState(
            startDate = DateValue(2024, 1, 1),
            endDate = DateValue(2024, 12, 31),
            selectedDate = DateValue(2024, 6, 1),
        )

        assertThrows(IllegalArgumentException::class.java) {
            state.updateRange(
                startDate = DateValue(2025, 1, 1),
                endDate = DateValue(2024, 12, 31),
            )
        }
    }
}
