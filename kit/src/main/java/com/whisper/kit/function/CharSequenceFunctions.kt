package com.whisper.kit.function

import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.Px

/**
 * 为整段文本设置绝对字号, 并保留文本已有的 Span.
 *
 * [size] 的单位是 px. 需要使用 sp 字号时, 应传入由 sp dimension 解析得到的像素值.
 */
fun CharSequence.absoluteSize(@Px @IntRange(from = 1) size: Int): CharSequence {
    require(size > 0) { "size must be greater than 0" }
    return withSpan(AbsoluteSizeSpan(size))
}

/**
 * 为整段文本设置相对于 TextView 基准字号的缩放比例, 并保留文本已有的 Span.
 */
fun CharSequence.relativeSize(
    @FloatRange(from = 0.0, fromInclusive = false) proportion: Float,
): CharSequence {
    require(proportion > 0f && proportion.isFinite()) {
        "proportion must be finite and greater than 0"
    }
    return withSpan(RelativeSizeSpan(proportion))
}

/**
 * 为整段文本设置前景色, 并保留文本已有的 Span.
 */
fun CharSequence.color(@ColorInt color: Int): CharSequence {
    return withSpan(ForegroundColorSpan(color))
}

/**
 * 为整段文本设置粗体、斜体或粗斜体样式, 并保留文本已有的 Span.
 */
fun CharSequence.textStyle(
    @IntRange(from = Typeface.NORMAL.toLong(), to = Typeface.BOLD_ITALIC.toLong()) style: Int,
): CharSequence {
    require(style in Typeface.NORMAL..Typeface.BOLD_ITALIC) {
        "style must be a Typeface style constant"
    }
    return withSpan(StyleSpan(style))
}

/**
 * 为整段文本设置指定字体, 并保留文本已有的 Span.
 */
fun CharSequence.typeface(typeface: Typeface): CharSequence {
    return withSpan(createTypefaceSpan(typeface))
}

/**
 * 为整段文本添加下划线, 并保留文本已有的 Span.
 */
fun CharSequence.underline(): CharSequence {
    return withSpan(UnderlineSpan())
}

/**
 * 为整段文本添加删除线, 并保留文本已有的 Span.
 */
fun CharSequence.strikethrough(): CharSequence {
    return withSpan(StrikethroughSpan())
}

/**
 * 为整段文本设置点击行为, 并保留文本已有的 Span.
 *
 * 该扩展只负责点击和下划线, 不修改文本颜色. 调用方可以与 [color] 组合设置点击文本颜色.
 * 承载返回文本的 TextView 仍需启用 `LinkMovementMethod` 才能响应点击.
 */
fun CharSequence.onClick(
    underline: Boolean = false,
    action: (View) -> Unit,
): CharSequence {
    return withSpan(
        object : ClickableSpan() {

            override fun onClick(widget: View) {
                action(widget)
            }

            override fun updateDrawState(drawState: TextPaint) {
                drawState.isUnderlineText = underline
            }
        }
    )
}

private fun CharSequence.withSpan(span: Any): CharSequence {
    if (isEmpty()) {
        return this
    }
    return SpannableString(this).apply {
        setSpan(span, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun createTypefaceSpan(typeface: Typeface): MetricAffectingSpan {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return TypefaceSpan(typeface)
    }
    return object : MetricAffectingSpan() {

        override fun updateDrawState(drawState: TextPaint) {
            drawState.typeface = typeface
        }

        override fun updateMeasureState(textPaint: TextPaint) {
            textPaint.typeface = typeface
        }
    }
}
