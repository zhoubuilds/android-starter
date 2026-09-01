package com.whisper.kit.extension

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
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
 * 与 [relativeSize] 连续调用时, 绝对字号作为基准, 再应用相对比例, 调用顺序不影响结果.
 */
fun CharSequence.absoluteSize(@Px @IntRange(from = 1) size: Int): CharSequence {
    require(size > 0) { "size must be greater than 0" }
    return withAbsoluteSize(size)
}

/**
 * 为整段文本设置相对于 TextView 基准字号或 [absoluteSize] 的缩放比例, 并保留文本已有的 Span.
 */
fun CharSequence.relativeSize(
    @FloatRange(from = 0.0, fromInclusive = false) proportion: Float,
): CharSequence {
    require(proportion > 0f && proportion.isFinite()) {
        "proportion must be finite and greater than 0"
    }
    return withRelativeSize(proportion)
}

/**
 * 为整段文本设置前景色, 并保留文本已有的 Span.
 */
fun CharSequence.color(@ColorInt color: Int): CharSequence {
    return withReplacingSpan(
        span = ForegroundColorSpan(color),
        spanClass = ForegroundColorSpan::class.java,
    )
}

/**
 * 为整段文本设置字形, 并保留文本已有的 Span.
 *
 * [Typeface.NORMAL] 重置已有字形. 粗体和斜体连续设置时合并为粗斜体,
 * 与 [typeface] 连续调用时不受调用顺序影响.
 */
fun CharSequence.textStyle(
    @IntRange(from = Typeface.NORMAL.toLong(), to = Typeface.BOLD_ITALIC.toLong()) style: Int,
): CharSequence {
    require(style in Typeface.NORMAL..Typeface.BOLD_ITALIC) {
        "style must be a Typeface style constant"
    }
    return withFontStyle(style)
}

/**
 * 为整段文本设置指定字体, 并保留文本已有的 Span.
 *
 * 字体与已有字形同时生效, 重复设置字体时使用最后一次传入的字体.
 */
fun CharSequence.typeface(typeface: Typeface): CharSequence {
    return withTypeface(typeface)
}

/**
 * 为整段文本添加下划线, 并保留文本已有的 Span.
 */
fun CharSequence.underline(): CharSequence {
    return withUnderlineSpan()
}

/**
 * 为整段文本添加删除线, 并保留文本已有的 Span.
 */
fun CharSequence.strikethrough(): CharSequence {
    return withReplacingSpan(
        span = StrikethroughSpan(),
        spanClass = StrikethroughSpan::class.java,
    )
}

/**
 * 为整段文本设置点击行为, 并保留文本已有的非点击 Span.
 *
 * 该扩展只负责点击和下划线, 不修改文本颜色. 调用方可以与 [color] 组合设置点击文本颜色.
 * 重复设置点击行为时, 后一次设置会替换与目标范围重叠的 [ClickableSpan].
 * 承载返回文本的 TextView 仍需启用 `LinkMovementMethod` 才能响应点击.
 */
fun CharSequence.onClick(
    underline: Boolean = false,
    action: (View) -> Unit,
): CharSequence {
    return withClickSpan(
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

private fun CharSequence.withClickSpan(span: ClickableSpan): CharSequence {
    if (isEmpty()) {
        return this
    }
    return SpannableString(this).apply {
        val targetStart = 0
        val targetEnd = length
        getSpans(targetStart, targetEnd, ClickableSpan::class.java)
            .filter { existingSpan: ClickableSpan ->
                spansOverlap(
                    firstStart = getSpanStart(existingSpan),
                    firstEnd = getSpanEnd(existingSpan),
                    secondStart = targetStart,
                    secondEnd = targetEnd,
                )
            }
            .forEach(::removeSpan)
        getSpans(targetStart, targetEnd, UnderlineSpan::class.java)
            .filter { existingSpan: UnderlineSpan ->
                spansOverlap(
                    firstStart = getSpanStart(existingSpan),
                    firstEnd = getSpanEnd(existingSpan),
                    secondStart = targetStart,
                    secondEnd = targetEnd,
                )
            }
            .forEach(::removeSpan)
        setSpan(span, targetStart, targetEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun CharSequence.withUnderlineSpan(): CharSequence {
    if (isEmpty()) {
        return this
    }
    return SpannableString(this).apply {
        val targetStart = 0
        val targetEnd = length
        getSpans(targetStart, targetEnd, ClickableSpan::class.java)
            .filter { existingSpan: ClickableSpan ->
                spansOverlap(
                    firstStart = getSpanStart(existingSpan),
                    firstEnd = getSpanEnd(existingSpan),
                    secondStart = targetStart,
                    secondEnd = targetEnd,
                )
            }
            .filterNot { existingSpan: ClickableSpan ->
                existingSpan is UnderlineClickableSpan
            }
            .forEach { existingSpan: ClickableSpan ->
                val start: Int = getSpanStart(existingSpan)
                val end: Int = getSpanEnd(existingSpan)
                val flags: Int = getSpanFlags(existingSpan)
                removeSpan(existingSpan)
                setSpan(UnderlineClickableSpan(existingSpan), start, end, flags)
            }
        getSpans(targetStart, targetEnd, UnderlineSpan::class.java)
            .filter { existingSpan: UnderlineSpan ->
                spansOverlap(
                    firstStart = getSpanStart(existingSpan),
                    firstEnd = getSpanEnd(existingSpan),
                    secondStart = targetStart,
                    secondEnd = targetEnd,
                )
            }
            .forEach(::removeSpan)
        setSpan(
            UnderlineSpan(),
            targetStart,
            targetEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}

private fun spansOverlap(
    firstStart: Int,
    firstEnd: Int,
    secondStart: Int,
    secondEnd: Int,
): Boolean {
    return firstStart < secondEnd && firstEnd > secondStart
}

private fun <T : Any> CharSequence.withReplacingSpan(
    span: T,
    spanClass: Class<T>,
): CharSequence {
    if (isEmpty()) {
        return this
    }
    return SpannableString(this).apply {
        val targetStart = 0
        val targetEnd = length
        getSpans(targetStart, targetEnd, spanClass)
            .filter { existingSpan: T ->
                spansOverlap(
                    firstStart = getSpanStart(existingSpan),
                    firstEnd = getSpanEnd(existingSpan),
                    secondStart = targetStart,
                    secondEnd = targetEnd,
                )
            }
            .forEach(::removeSpan)
        setSpan(span, targetStart, targetEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun CharSequence.withAbsoluteSize(size: Int): CharSequence {
    return withSizeSpan { currentSpan: ComposableSizeSpan? ->
        ComposableSizeSpan(
            absoluteSize = size,
            relativeProportion = currentSpan?.relativeProportion,
        )
    }
}

private fun CharSequence.withRelativeSize(proportion: Float): CharSequence {
    return withSizeSpan { currentSpan: ComposableSizeSpan? ->
        ComposableSizeSpan(
            absoluteSize = currentSpan?.absoluteSize,
            relativeProportion = proportion,
        )
    }
}

private fun CharSequence.withSizeSpan(
    createSpan: (ComposableSizeSpan?) -> ComposableSizeSpan,
): CharSequence {
    if (isEmpty()) {
        return this
    }
    return SpannableString(this).apply {
        val fullRangeSpans: List<ComposableSizeSpan> = getSpans(
            0,
            length,
            ComposableSizeSpan::class.java,
        ).filter { span: ComposableSizeSpan ->
            getSpanStart(span) == 0 && getSpanEnd(span) == length
        }
        val currentSpan: ComposableSizeSpan? = fullRangeSpans.lastOrNull()
        fullRangeSpans.forEach(::removeSpan)
        setSpan(
            createSpan(currentSpan),
            0,
            length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}

private fun CharSequence.withFontStyle(style: Int): CharSequence {
    return withFontSpan { currentSpan: ComposableFontSpan? ->
        val currentStyle: Int? = currentSpan?.requestedStyle
        val mergedStyle: Int = when {
            style == Typeface.NORMAL -> Typeface.NORMAL
            currentStyle == null || currentStyle == Typeface.NORMAL -> style
            else -> currentStyle or style
        }
        ComposableFontSpan(
            requestedTypeface = currentSpan?.requestedTypeface,
            requestedStyle = mergedStyle,
        )
    }
}

private fun CharSequence.withTypeface(typeface: Typeface): CharSequence {
    return withFontSpan { currentSpan: ComposableFontSpan? ->
        ComposableFontSpan(
            requestedTypeface = typeface,
            requestedStyle = currentSpan?.requestedStyle,
        )
    }
}

private fun CharSequence.withFontSpan(
    createSpan: (ComposableFontSpan?) -> ComposableFontSpan,
): CharSequence {
    if (isEmpty()) {
        return this
    }
    return SpannableString(this).apply {
        val fullRangeSpans: List<ComposableFontSpan> = getSpans(
            0,
            length,
            ComposableFontSpan::class.java,
        ).filter { span: ComposableFontSpan ->
            getSpanStart(span) == 0 && getSpanEnd(span) == length
        }
        val currentSpan: ComposableFontSpan? = fullRangeSpans.lastOrNull()
        fullRangeSpans.forEach(::removeSpan)
        setSpan(
            createSpan(currentSpan),
            0,
            length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}

private class ComposableFontSpan(
    val requestedTypeface: Typeface?,
    val requestedStyle: Int?,
) : MetricAffectingSpan() {

    override fun updateDrawState(drawState: TextPaint) {
        applyFont(drawState)
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        applyFont(textPaint)
    }

    private fun applyFont(textPaint: TextPaint) {
        val currentTypeface: Typeface = textPaint.typeface ?: Typeface.DEFAULT
        val currentStyle: Int = currentTypeface.style or
            (if (textPaint.isFakeBoldText) Typeface.BOLD else Typeface.NORMAL) or
            (if (textPaint.textSkewX != 0f) Typeface.ITALIC else Typeface.NORMAL)
        val baseTypeface: Typeface = requestedTypeface ?: currentTypeface
        val targetStyle: Int = when (requestedStyle) {
            null -> currentStyle or baseTypeface.style
            Typeface.NORMAL -> Typeface.NORMAL
            else -> currentStyle or baseTypeface.style or requestedStyle
        }
        val resolvedTypeface: Typeface = Typeface.create(baseTypeface, targetStyle)
        val missingStyle: Int = targetStyle and resolvedTypeface.style.inv()

        textPaint.isFakeBoldText = missingStyle and Typeface.BOLD != 0
        textPaint.textSkewX = if (missingStyle and Typeface.ITALIC != 0) {
            ITALIC_TEXT_SKEW_X
        } else {
            0f
        }
        textPaint.typeface = resolvedTypeface
    }

    private companion object {
        private const val ITALIC_TEXT_SKEW_X: Float = -0.25f
    }
}

private class ComposableSizeSpan(
    val absoluteSize: Int?,
    val relativeProportion: Float?,
) : MetricAffectingSpan() {

    override fun updateDrawState(drawState: TextPaint) {
        applySize(drawState)
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        applySize(textPaint)
    }

    private fun applySize(textPaint: TextPaint) {
        val baseSize: Float = absoluteSize?.toFloat() ?: textPaint.textSize
        textPaint.textSize = baseSize * (relativeProportion ?: 1f)
    }
}

private class UnderlineClickableSpan(
    private val delegate: ClickableSpan,
) : ClickableSpan() {

    override fun onClick(widget: View) {
        delegate.onClick(widget)
    }

    override fun updateDrawState(drawState: TextPaint) {
        delegate.updateDrawState(drawState)
        drawState.isUnderlineText = true
    }
}
