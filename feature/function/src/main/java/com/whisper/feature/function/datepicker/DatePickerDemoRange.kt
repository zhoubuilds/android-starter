package com.whisper.feature.function.datepicker

import java.util.Calendar

/**
 * 日期选择用例根据设备日期生成的可选闭区间.
 *
 * @property startDate 可选起始日期.
 * @property endDate 可选结束日期.
 *
 * @author whisper
 * @since 2026/10/22
 */
internal data class DatePickerDemoRange(
    val startDate: DateValue,
    val endDate: DateValue,
) {

    companion object {

        fun from(currentDate: Calendar): DatePickerDemoRange {
            val currentDateValue: DateValue = DateValue.from(currentDate)
            val epochDate: DateValue = DateValue(EPOCH_YEAR, Calendar.JANUARY + 1, 1)
            if (currentDateValue < epochDate) {
                return DatePickerDemoRange(
                    startDate = currentDateValue,
                    endDate = epochDate,
                )
            }
            val oneNaturalYearLater: Calendar = (currentDate.clone() as Calendar).apply {
                add(Calendar.YEAR, 1)
            }
            return DatePickerDemoRange(
                startDate = epochDate,
                endDate = DateValue.from(oneNaturalYearLater),
            )
        }

        private const val EPOCH_YEAR: Int = 1970
    }
}
