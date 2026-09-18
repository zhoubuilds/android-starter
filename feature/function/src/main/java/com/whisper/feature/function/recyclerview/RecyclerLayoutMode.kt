package com.whisper.feature.function.recyclerview

import androidx.annotation.IdRes
import com.whisper.feature.function.R

/**
 * 分割线演示支持的布局模式, 同时关联对应单选按钮.
 *
 * @property buttonId 页面布局模式分段组中的按钮 ID.
 */
internal enum class RecyclerLayoutMode(@param:IdRes val buttonId: Int) {
    LINEAR(R.id.function_recycler_linear),
    GRID(R.id.function_recycler_grid),
    STAGGERED(R.id.function_recycler_staggered);

    companion object {

        /**
         * 恢复保存的布局选项, 未保存或名称不再受支持时使用默认列表.
         */
        fun fromName(name: String?): RecyclerLayoutMode =
            entries.firstOrNull { it.name == name } ?: LINEAR

        /**
         * 解析布局模式分段组中的按钮, 传入其它按钮 ID 属于页面配置错误.
         *
         * @throws NoSuchElementException 按钮 ID 未与任何布局模式关联.
         */
        fun fromButtonId(@IdRes buttonId: Int): RecyclerLayoutMode =
            entries.first { it.buttonId == buttonId }
    }
}
