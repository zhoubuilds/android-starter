package com.whisper.kit.extension

import android.util.TypedValue
import com.whisper.kit.KitApplicationHolder


/**
 *
 * @author whisper
 * @since 2025/10/9
 */

val Number.dp: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        KitApplicationHolder.application.resources.displayMetrics
    )

val Number.sp: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        this.toFloat(),
        KitApplicationHolder.application.resources.displayMetrics
    )

