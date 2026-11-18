package com.whisper.feature.function.datepicker

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * 不依赖 View 的日期范围与年月日联动状态.
 */
internal class DatePickerState(
    startDate: DateValue,
    endDate: DateValue,
    selectedDate: DateValue,
) {

    var startDate: DateValue = startDate
        private set

    var endDate: DateValue = endDate
        private set

    var selectedDate: DateValue = selectedDate
        private set

    init {
        updateRange(startDate, endDate)
        selectDate(selectedDate)
    }

    val yearRange: IntRange
        get() = startDate.year..endDate.year

    val monthRange: IntRange
        get() = monthRange(selectedDate.year)

    val dayRange: IntRange
        get() = dayRange(selectedDate.year, selectedDate.month)

    fun updateRange(startDate: DateValue, endDate: DateValue): Boolean {
        require(startDate <= endDate) { "startDate must not be after endDate" }
        this.startDate = startDate
        this.endDate = endDate
        return updateSelectedDate(normalize(selectedDate))
    }

    fun selectDate(date: DateValue): Boolean = updateSelectedDate(normalize(date))

    fun selectYear(year: Int): Boolean = selectDate(selectedDate.copy(year = year))

    fun selectMonth(month: Int): Boolean = selectDate(selectedDate.copy(month = month))

    fun selectDay(day: Int): Boolean = selectDate(selectedDate.copy(day = day))

    /**
     * 完整日期越界时先收敛到区间边界, 范围内再修正年月变化带来的非法月日.
     */
    private fun normalize(date: DateValue): DateValue {
        if (date < startDate) {
            return startDate
        }
        if (date > endDate) {
            return endDate
        }
        val year: Int = date.year.coerceIn(startDate.year, endDate.year)
        val month: Int = date.month.coerceIn(monthRange(year))
        val day: Int = date.day.coerceIn(dayRange(year, month))
        return DateValue(year, month, day)
    }

    private fun monthRange(year: Int): IntRange {
        val firstMonth: Int = if (year == startDate.year) startDate.month else 1
        val lastMonth: Int = if (year == endDate.year) endDate.month else 12
        return firstMonth..lastMonth
    }

    /**
     * 返回当前月份的连续可选日号区间.
     *
     * 默认 GregorianCalendar 在 1582-10-15 从儒略历切换到格里高利历. 为校正旧历
     * 累积的日期偏差, 1582-10-04 的次日是 1582-10-15, 中间的 5..14 日不存在.
     * 这是 Java 采用的默认切换点; 历史上各地区实际改历日期并不相同.
     *
     * getActualMaximum(DAY_OF_MONTH) 返回该月的最大日号 31, 并不枚举有效日期.
     * 本方法用连续 IntRange 生成滚轮日号, 因此范围覆盖 1582 年 10 月时仍会显示
     * 5..14 日. 选中这些日号后, 转回 Calendar 的宽松解析会将它们顺延 10 日.
     */
    private fun dayRange(year: Int, month: Int): IntRange {
        val firstDay: Int = if (year == startDate.year && month == startDate.month) {
            startDate.day
        } else {
            1
        }
        val lastDay: Int = if (year == endDate.year && month == endDate.month) {
            endDate.day
        } else {
            GregorianCalendar(year, month - 1, 1).getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        return firstDay..lastDay
    }

    private fun updateSelectedDate(date: DateValue): Boolean {
        if (selectedDate == date) {
            return false
        }
        selectedDate = date
        return true
    }
}

/**
 * 以 1 至 12 表示月份、仅支持公元后年份的不可变公历日期.
 */
internal data class DateValue(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<DateValue> {

    init {
        require(year > 0) { "year must be positive" }
        require(month in 1..12) { "month must be in 1..12" }
        require(day in 1..31) { "day must be in 1..31" }
    }

    override fun compareTo(other: DateValue): Int = compareValuesBy(
        this,
        other,
        DateValue::year,
        DateValue::month,
        DateValue::day,
    )

    fun toCalendar(): Calendar = GregorianCalendar().apply {
        clear()
        set(year, month - 1, day)
    }

    companion object {

        /**
         * 从公元后 Calendar 提取日期, 不支持公元前输入.
         *
         * Calendar 使用 ERA=BC 和正数 YEAR 表示公元前年份, 而非负数年份.
         * DateValue 不保存 ERA, 此处只读取 YEAR; 公元前输入会被当作同年的公元后日期,
         * 转回 Calendar 时无法恢复原始纪元. 调用方必须保证传入日期的 ERA=AD.
         */
        fun from(calendar: Calendar): DateValue = DateValue(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }
}

/**
 * GregorianCalendar 的正纪元从公元 1 年开始, 不存在公元 0 年.
 */
internal fun getDefaultDatePickerStartYear(currentYear: Int): Int =
    (currentYear - DEFAULT_YEAR_DISTANCE).coerceAtLeast(1)

internal const val DEFAULT_YEAR_DISTANCE: Int = 100
