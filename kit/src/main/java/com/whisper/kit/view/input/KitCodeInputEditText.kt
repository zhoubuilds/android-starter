package com.whisper.kit.view.input

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.util.AttributeSet
import android.view.View
import androidx.annotation.IntRange
import androidx.annotation.Px
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat
import com.whisper.kit.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 使用单个原生输入连接绘制分格文本的 EditText.
 *
 * 控件保留 EditText 的文本、粘贴、删除、TextWatcher、Autofill 和无障碍语义,
 * 只接管文本区域绘制. 已输入字符使用 TextView 标准文本样式,
 * 未输入位置重复绘制 [getHint] 返回的提示文本.
 *
 * @author whisper
 * @since 2026/08/14
 */
class KitCodeInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.kitCodeInputEditTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /**
     * 分格数量, 同时作为最大输入字符数.
     *
     * XML 应使用原生 `android:maxLength`; 未配置时默认为 6.
     */
    @get:IntRange(from = 1)
    var codeLength: Int
        get() {
            val length: Int = readCodeLengthFilter() ?: DEFAULT_CODE_LENGTH
            check(length > 0) { "android:maxLength must be greater than 0." }
            return length
        }
        set(@IntRange(from = 1) value) {
            require(value > 0) { "codeLength must be greater than 0." }
            if (codeLength == value) {
                return
            }
            applyCodeLengthFilter(value)
            trimTextToCodeLength(value)
            requestLayout()
            invalidate()
        }

    /**
     * 单个输入格背景.
     *
     * Drawable 状态跟随控件状态, 同一个实例会依次绘制到每个输入格.
     */
    var itemBackground: Drawable? = null
        set(value) {
            if (field === value) {
                return
            }
            field?.callback = null
            field = value?.mutate()
            field?.let { background: Drawable ->
                background.callback = this
                background.setVisible(visibility == View.VISIBLE, false)
                background.layoutDirection = layoutDirection
                if (background.isStateful) {
                    background.state = drawableState
                }
            }
            requestLayout()
            invalidate()
        }

    /**
     * 单个输入格期望宽度, 单位 px. 0 表示优先使用背景固有宽度或自适应宽度.
     */
    @get:Px
    var itemWidth: Float = 0f
        set(value) {
            val normalizedValue: Float = max(0f, value)
            if (field == normalizedValue) {
                return
            }
            field = normalizedValue
            requestLayout()
            invalidate()
        }

    /**
     * 单个输入格期望高度, 单位 px. 0 表示优先使用背景固有高度或控件内容高度.
     */
    @get:Px
    var itemHeight: Float = 0f
        set(value) {
            val normalizedValue: Float = max(0f, value)
            if (field == normalizedValue) {
                return
            }
            field = normalizedValue
            requestLayout()
            invalidate()
        }

    /**
     * 内容决定控件宽度时相邻输入格的期望间距, 单位 px.
     *
     * 父布局限制控件宽度时, 实际间距由可用宽度、格宽和分格数量重新计算.
     */
    @get:Px
    var itemSpacing: Float = 0f
        set(value) {
            val normalizedValue: Float = max(0f, value)
            if (field == normalizedValue) {
                return
            }
            field = normalizedValue
            requestLayout()
            invalidate()
        }

    /**
     * 空输入格提示文本使用的字体.
     */
    var hintTypeface: Typeface = typeface ?: Typeface.DEFAULT
        set(value) {
            if (field == value) {
                return
            }
            field = value
            requestLayout()
            invalidate()
        }

    private val characterPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var changingSelection: Boolean = false
    private var widthConstrainedByParent: Boolean = false

    init {
        val configuredCodeLength: Int? = readCodeLengthFilter()
        if (configuredCodeLength == null) {
            applyCodeLengthFilter(DEFAULT_CODE_LENGTH)
        } else {
            require(configuredCodeLength > 0) { "android:maxLength must be greater than 0." }
        }
        trimTextToCodeLength(codeLength)

        context.obtainStyledAttributes(
            attrs,
            R.styleable.KitCodeInputEditText,
            defStyleAttr,
            0,
        ).apply {
            itemBackground = getDrawable(
                R.styleable.KitCodeInputEditText_kitCodeItemBackground,
            )
            itemWidth = getDimension(
                R.styleable.KitCodeInputEditText_kitCodeItemWidth,
                itemWidth,
            )
            itemHeight = getDimension(
                R.styleable.KitCodeInputEditText_kitCodeItemHeight,
                itemHeight,
            )
            itemSpacing = getDimension(
                R.styleable.KitCodeInputEditText_kitCodeItemSpacing,
                itemSpacing,
            )
            hintTypeface = readTypeface(
                R.styleable.KitCodeInputEditText_kitCodeHintFontFamily,
                hintTypeface,
            )
            recycle()
        }
        isCursorVisible = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val length: Int = codeLength
        val horizontalPadding: Int = paddingLeft + paddingRight
        val verticalPadding: Int = paddingTop + paddingBottom
        val desiredItemWidth: Float = resolveDesiredItemWidth()
        val desiredItemHeight: Float = resolveDesiredItemHeight()
        val desiredWidth: Int = ceil(
            horizontalPadding + desiredItemWidth * length + itemSpacing * (length - 1),
        ).toInt()
        val desiredHeight: Int = ceil(verticalPadding + desiredItemHeight).toInt()
        val resolvedWidth: Int = resolveSizeAndState(
            max(desiredWidth, suggestedMinimumWidth),
            widthMeasureSpec,
            0,
        )
        val resolvedHeight: Int = resolveSizeAndState(
            max(desiredHeight, suggestedMinimumHeight),
            heightMeasureSpec,
            0,
        )
        val measuredWidth: Int = resolvedWidth and View.MEASURED_SIZE_MASK
        val widthMode: Int = MeasureSpec.getMode(widthMeasureSpec)
        widthConstrainedByParent = widthMode == MeasureSpec.EXACTLY ||
            widthMode == MeasureSpec.AT_MOST && measuredWidth < desiredWidth
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val length: Int = codeLength
        val contentLeft: Float = paddingLeft.toFloat()
        val contentTop: Float = paddingTop.toFloat()
        val contentRight: Float = (width - paddingRight).toFloat()
        val contentBottom: Float = (height - paddingBottom).toFloat()
        val contentWidth: Float = contentRight - contentLeft
        val contentHeight: Float = contentBottom - contentTop
        val fixedItemWidth: Float? = resolveFixedItemWidth()
        val drawItemWidth: Float
        val drawItemSpacing: Float

        if (widthConstrainedByParent) {
            if (fixedItemWidth == null) {
                drawItemWidth = contentWidth / length
                drawItemSpacing = 0f
            } else {
                drawItemWidth = fixedItemWidth
                drawItemSpacing = if (length == 1) {
                    0f
                } else {
                    (contentWidth - drawItemWidth * length) / (length - 1)
                }
            }
        } else {
            drawItemWidth = fixedItemWidth ?: resolveDesiredItemWidth()
            drawItemSpacing = itemSpacing
        }

        val drawItemHeight: Float = resolveFixedItemHeight() ?: contentHeight
        val itemTop: Float = contentTop + (contentHeight - drawItemHeight) / 2f
        val itemBottom: Float = itemTop + drawItemHeight
        val value: CharSequence = text ?: ""
        val emptyItemHint: CharSequence = hint ?: ""

        for (index: Int in 0 until length) {
            val itemLeft: Float = contentLeft + index * (drawItemWidth + drawItemSpacing)
            val itemRight: Float = itemLeft + drawItemWidth
            itemBackground?.let { background: Drawable ->
                background.setBounds(
                    itemLeft.roundToInt(),
                    itemTop.roundToInt(),
                    itemRight.roundToInt(),
                    itemBottom.roundToInt(),
                )
                background.draw(canvas)
            }

            val character: CharSequence = if (index < value.length) {
                value.subSequence(index, index + 1)
            } else {
                emptyItemHint
            }
            if (character.isEmpty()) {
                continue
            }

            syncCharacterPaint()
            characterPaint.color = if (index < value.length) {
                currentTextColor
            } else {
                currentHintTextColor
            }
            characterPaint.typeface = if (index < value.length) typeface else hintTypeface
            val fontMetrics: Paint.FontMetrics = characterPaint.fontMetrics
            val baseline: Float = (itemTop + itemBottom) / 2f -
                (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(
                character,
                0,
                character.length,
                (itemLeft + itemRight) / 2f,
                baseline,
                characterPaint,
            )
        }
    }

    override fun onSelectionChanged(selectionStart: Int, selectionEnd: Int) {
        super.onSelectionChanged(selectionStart, selectionEnd)
        val textEnd: Int = text?.length ?: return
        if (changingSelection || selectionStart == textEnd && selectionEnd == textEnd) {
            return
        }
        changingSelection = true
        setSelection(textEnd)
        changingSelection = false
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        who === itemBackground || super.verifyDrawable(who)

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        itemBackground?.let { background: Drawable ->
            if (background.isStateful && background.setState(drawableState)) {
                invalidateDrawable(background)
            }
        }
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        itemBackground?.jumpToCurrentState()
    }

    override fun drawableHotspotChanged(x: Float, y: Float) {
        super.drawableHotspotChanged(x, y)
        itemBackground?.setHotspot(x, y)
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        itemBackground?.layoutDirection = layoutDirection
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        itemBackground?.setVisible(visibility == View.VISIBLE, false)
    }

    private fun resolveDesiredItemWidth(): Float {
        resolveFixedItemWidth()?.let { return it }
        syncCharacterPaint()
        var measuredTextWidth: Float = characterPaint.measureText(hint?.toString().orEmpty())
        val value: CharSequence = text ?: ""
        for (index: Int in value.indices) {
            measuredTextWidth = max(
                measuredTextWidth,
                characterPaint.measureText(value, index, index + 1),
            )
        }
        return max(MINIMUM_DRAW_SIZE_PX, max(textSize, measuredTextWidth))
    }

    private fun resolveDesiredItemHeight(): Float {
        resolveFixedItemHeight()?.let { return it }
        syncCharacterPaint()
        val fontMetrics: Paint.FontMetrics = characterPaint.fontMetrics
        return max(MINIMUM_DRAW_SIZE_PX, fontMetrics.descent - fontMetrics.ascent)
    }

    private fun resolveFixedItemWidth(): Float? {
        if (itemWidth > 0f) {
            return itemWidth
        }
        val intrinsicWidth: Int = itemBackground?.intrinsicWidth ?: 0
        return intrinsicWidth.takeIf { it > 0 }?.toFloat()
    }

    private fun resolveFixedItemHeight(): Float? {
        if (itemHeight > 0f) {
            return itemHeight
        }
        val intrinsicHeight: Int = itemBackground?.intrinsicHeight ?: 0
        return intrinsicHeight.takeIf { it > 0 }?.toFloat()
    }

    private fun syncCharacterPaint() {
        characterPaint.textSize = textSize
        characterPaint.typeface = typeface
    }

    private fun readCodeLengthFilter(): Int? = filters
        .filterIsInstance<InputFilter.LengthFilter>()
        .minOfOrNull { filter: InputFilter.LengthFilter -> filter.max }

    private fun applyCodeLengthFilter(length: Int) {
        val filtersWithoutLength: List<InputFilter> = filters.filterNot {
            it is InputFilter.LengthFilter
        }
        filters = (filtersWithoutLength + InputFilter.LengthFilter(length)).toTypedArray()
    }

    private fun trimTextToCodeLength(length: Int) {
        val currentText = text ?: return
        if (currentText.length > length) {
            currentText.delete(length, currentText.length)
        }
    }

    private fun android.content.res.TypedArray.readTypeface(
        index: Int,
        fallback: Typeface,
    ): Typeface {
        val fontResourceId: Int = getResourceId(index, 0)
        if (fontResourceId != 0) {
            try {
                return ResourcesCompat.getFont(context, fontResourceId) ?: fallback
            } catch (_: Resources.NotFoundException) {
                return fallback
            }
        }
        val familyName: String = getString(index) ?: return fallback
        return Typeface.create(familyName, Typeface.NORMAL)
    }

    private companion object {

        private const val DEFAULT_CODE_LENGTH: Int = 6
        private const val MINIMUM_DRAW_SIZE_PX: Float = 1f
    }
}
