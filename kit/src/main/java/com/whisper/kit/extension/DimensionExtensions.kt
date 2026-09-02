package com.whisper.kit.extension

import android.app.Dialog
import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.annotation.Px
import androidx.fragment.app.Fragment

/**
 * 使用当前 Context 的显示指标将 dp 转换为 px.
 *
 * 该属性需要 Context 环境, 可在 `with(context)` 或其它具有隐式 Context 接收者的作用域中访问.
 * 返回值保留亚像素精度, 需要整数像素时由调用方根据具体布局语义决定舍入方式.
 *
 * ```kotlin
 * val spacingPx = with(context) { 16.dp }
 * ```
 */
context(androidContext: Context)
@get:Px
val Number.dp: Float
    get() = toDpPixels(androidContext)

/**
 * 使用当前 Context 的显示指标将 sp 转换为 px.
 *
 * 转换使用当前 Context 的字体缩放配置. 返回值保留亚像素精度,
 * 需要整数像素时由调用方根据具体文本或布局语义决定舍入方式.
 *
 * ```kotlin
 * val textSizePx = with(context) { 14.sp }
 * ```
 */
context(androidContext: Context)
@get:Px
val Number.sp: Float
    get() = toSpPixels(androidContext)

/**
 * 使用 Fragment 当前附加的 Context 将 dp 转换为 px.
 *
 * 仅可在 Fragment 已附加 Context 时访问; 否则 [Fragment.requireContext] 会抛出异常.
 */
context(fragment: Fragment)
@get:Px
val Number.dp: Float
    get() = toDpPixels(fragment.requireContext())

/**
 * 使用 Fragment 当前附加的 Context 将 sp 转换为 px.
 *
 * 仅可在 Fragment 已附加 Context 时访问; 否则 [Fragment.requireContext] 会抛出异常.
 */
context(fragment: Fragment)
@get:Px
val Number.sp: Float
    get() = toSpPixels(fragment.requireContext())

/**
 * 使用 View 的 Context 将 dp 转换为 px.
 */
context(view: View)
@get:Px
val Number.dp: Float
    get() = toDpPixels(view.context)

/**
 * 使用 View 的 Context 将 sp 转换为 px.
 */
context(view: View)
@get:Px
val Number.sp: Float
    get() = toSpPixels(view.context)

/**
 * 使用 Dialog 的 Context 将 dp 转换为 px.
 */
context(dialog: Dialog)
@get:Px
val Number.dp: Float
    get() = toDpPixels(dialog.context)

/**
 * 使用 Dialog 的 Context 将 sp 转换为 px.
 */
context(dialog: Dialog)
@get:Px
val Number.sp: Float
    get() = toSpPixels(dialog.context)

/**
 * 使用明确指定的 [context] 将 dp 转换为 px.
 *
 * 当多个组件 context parameter 同时可用而无法确定属性重载时, 使用该函数显式选择 Context.
 */
@Px
fun Number.dp(context: Context): Float = toDpPixels(context)

/**
 * 使用明确指定的 [context] 将 sp 转换为 px.
 *
 * 当多个组件 context parameter 同时可用而无法确定属性重载时, 使用该函数显式选择 Context.
 */
@Px
fun Number.sp(context: Context): Float = toSpPixels(context)

private fun Number.toDpPixels(context: Context): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    toFloat(),
    context.resources.displayMetrics,
)

private fun Number.toSpPixels(context: Context): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    toFloat(),
    context.resources.displayMetrics,
)
