package com.whisper.feature.function

import android.os.Bundle
import android.view.View
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.whisper.feature.function.databinding.FunctionCatalogFragmentBinding
import com.whisper.feature.function.datepicker.DatePickerBottomSheetDialog
import com.whisper.feature.function.datepicker.DatePickerDemoRange
import com.whisper.feature.function.recyclerview.RecyclerViewDemoActivity
import com.whisper.kit.extension.dp
import com.whisper.kit.extension.viewBinding
import com.whisper.kit.recyclerview.decoration.RegularItemSpaceDecoration
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * 使用列表展示可进入的组件演示.
 *
 * 当前类型由 app 组合根直接创建, 不作为跨模块路由或能力契约.
 * 目录项之间及末项后的间距由 Kit 装饰器维护, 与列表自身的页面 padding 分开计算.
 *
 * @author whisper
 * @since 2026/09/14
 */
class FunctionFragment : Fragment(R.layout.function_catalog_fragment) {

    @get:MainThread
    private val viewBinding by viewBinding(FunctionCatalogFragmentBinding::bind)
    private var datePickerDialog: DatePickerBottomSheetDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding.functionCatalogList.layoutManager = LinearLayoutManager(requireContext())
        val itemSpace = 8.dp(requireContext()).roundToInt()
        viewBinding.functionCatalogList.addItemDecoration(
            RegularItemSpaceDecoration(
                mainAxisSpace = itemSpace,
                crossAxisSpace = 0,
                endSpace = itemSpace,
            )
        )
        viewBinding.functionCatalogList.adapter = FunctionCatalogAdapter { demo: FunctionDemo ->
            when (demo) {
                FunctionDemo.DATE_PICKER -> showDatePicker()
                FunctionDemo.RECYCLER_DIVIDERS -> startActivity(
                    RecyclerViewDemoActivity.createIntent(requireContext()),
                )
                else -> startActivity(FunctionDemoActivity.createIntent(requireContext(), demo))
            }
        }
    }

    override fun onDestroyView() {
        datePickerDialog?.dismiss()
        datePickerDialog = null
        super.onDestroyView()
    }

    private fun showDatePicker() {
        val currentDate: Calendar = Calendar.getInstance()
        val selectableRange: DatePickerDemoRange = DatePickerDemoRange.from(currentDate)
        datePickerDialog?.dismiss()
        datePickerDialog = DatePickerBottomSheetDialog.Builder(requireContext())
            .setRange(
                selectableRange.startDate.toCalendar(),
                selectableRange.endDate.toCalendar(),
            )
            .setInitialDate(currentDate)
            .setOnDateConfirmed { date: Calendar ->
                Snackbar.make(
                    viewBinding.root,
                    getString(
                        R.string.function_date_picker_selected,
                        date.get(Calendar.YEAR),
                        date.get(Calendar.MONTH) + 1,
                        date.get(Calendar.DAY_OF_MONTH),
                    ),
                    Snackbar.LENGTH_SHORT,
                ).show()
            }
            .build()
            .also(DatePickerBottomSheetDialog::show)
    }

}
