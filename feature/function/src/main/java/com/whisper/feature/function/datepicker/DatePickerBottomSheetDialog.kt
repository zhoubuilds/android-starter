package com.whisper.feature.function.datepicker

import android.content.Context
import android.os.Bundle
import com.whisper.feature.function.databinding.FunctionDatePickerBottomSheetBinding
import com.whisper.foundation.widget.FoundationBottomSheetDialog
import com.whisper.kit.extension.dp
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * 在 Foundation Bottom Sheet 容器中组合年月日滚轮和确认操作.
 */
internal class DatePickerBottomSheetDialog private constructor(
    context: Context,
) : FoundationBottomSheetDialog(context) {

    private val viewBinding: FunctionDatePickerBottomSheetBinding =
        FunctionDatePickerBottomSheetBinding.inflate(layoutInflater)
    private var startDate: DateValue = DateValue.from(Calendar.getInstance())
    private var endDate: DateValue = startDate
    private var initialDate: DateValue = startDate
    private var onDateConfirmed: ((Calendar) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        viewBinding.functionDatePicker.setRange(startDate.toCalendar(), endDate.toCalendar())
        viewBinding.functionDatePicker.setDate(initialDate.toCalendar())
        viewBinding.functionDatePickerCancel.setOnClickListener {
            dismiss()
        }
        viewBinding.functionDatePickerConfirm.setOnClickListener {
            onDateConfirmed?.invoke(viewBinding.functionDatePicker.selectedDate)
            dismiss()
        }
    }

    override fun getMaximumAllowedHeight(availableHeight: Int): Int = minOf(
        availableHeight,
        MAXIMUM_HEIGHT_DP.dp(context).roundToInt(),
    )

    /**
     * 构造日期滚轮 Bottom Sheet, 并在 build 时校验日期区间.
     */
    internal class Builder(private val context: Context) {

        private val today: DateValue = DateValue.from(Calendar.getInstance())
        private var startDate: DateValue = DateValue(getDefaultDatePickerStartYear(today.year), 1, 1)
        private var endDate: DateValue = DateValue(today.year + DEFAULT_YEAR_DISTANCE, 12, 31)
        private var initialDate: DateValue = today
        private var isBackDismissEnabled: Boolean = true
        private var isOutsideDismissEnabled: Boolean = true
        private var onDateConfirmed: ((Calendar) -> Unit)? = null

        fun setStartDate(date: Calendar): Builder = apply {
            startDate = DateValue.from(date)
        }

        fun setEndDate(date: Calendar): Builder = apply {
            endDate = DateValue.from(date)
        }

        fun setRange(startDate: Calendar, endDate: Calendar): Builder = apply {
            this.startDate = DateValue.from(startDate)
            this.endDate = DateValue.from(endDate)
        }

        fun setInitialDate(date: Calendar): Builder = apply {
            initialDate = DateValue.from(date)
        }

        fun setBackDismissEnabled(enabled: Boolean): Builder = apply {
            isBackDismissEnabled = enabled
        }

        fun setOutsideDismissEnabled(enabled: Boolean): Builder = apply {
            isOutsideDismissEnabled = enabled
        }

        fun setOnDateConfirmed(listener: (Calendar) -> Unit): Builder = apply {
            onDateConfirmed = listener
        }

        fun build(): DatePickerBottomSheetDialog {
            require(startDate <= endDate) { "startDate must not be after endDate" }
            return DatePickerBottomSheetDialog(context).also { dialog: DatePickerBottomSheetDialog ->
                dialog.startDate = startDate
                dialog.endDate = endDate
                dialog.initialDate = initialDate
                dialog.onDateConfirmed = onDateConfirmed
                dialog.setBackDismissEnabled(isBackDismissEnabled)
                dialog.setOutsideDismissEnabled(isOutsideDismissEnabled)
            }
        }
    }

    private companion object {

        private const val MAXIMUM_HEIGHT_DP: Int = 520
    }
}
