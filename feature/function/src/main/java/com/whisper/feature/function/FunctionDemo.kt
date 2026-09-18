package com.whisper.feature.function

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Function 目录当前可进入的演示项目.
 *
 * @property titleRes 演示的用户可见名称, 同时用于目录和详情工具栏.
 * @property componentNameRes 目录中显示的底层组件名称, 方便开发者对应到实现.
 * @property iconRes 目录项图标资源.
 */
internal enum class FunctionDemo(
    @param:StringRes val titleRes: Int,
    @param:StringRes val componentNameRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    RANGE(
        titleRes = R.string.function_range_title,
        componentNameRes = R.string.function_range_component,
        iconRes = R.drawable.function_ic_tune,
    ),
    CODE_INPUT(
        titleRes = R.string.function_code_input_title,
        componentNameRes = R.string.function_code_input_component,
        iconRes = R.drawable.function_ic_dialpad,
    ),
    LINE(
        titleRes = R.string.function_line_title,
        componentNameRes = R.string.function_line_component,
        iconRes = R.drawable.function_ic_line,
    ),
    RECYCLER_DIVIDERS(
        titleRes = R.string.function_recycler_title,
        componentNameRes = R.string.function_recycler_component,
        iconRes = R.drawable.function_ic_recycler,
    );

    companion object {

        fun fromName(name: String?): FunctionDemo? = entries.firstOrNull { demo: FunctionDemo ->
            demo.name == name
        }
    }
}
