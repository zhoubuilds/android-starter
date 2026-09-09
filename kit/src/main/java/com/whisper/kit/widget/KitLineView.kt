package com.whisper.kit.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.annotation.Px
import androidx.core.content.res.use
import androidx.core.content.withStyledAttributes
import com.whisper.kit.R
import kotlin.math.max

/**
 * 支持 XML、样式和运行时属性配置的实线、虚线与点划线.
 *
 * `android:orientation` 沿用 [LinearLayout] 的方向值, 默认水平. 点划尺寸表示沿线方向的长度,
 * 保留亚像素精度; 线条粗细由控件扣除 padding 后的交叉轴尺寸决定.
 * 交叉轴 wrap_content 至少容纳 1 px 内容和 padding, 同时遵守最小尺寸及父布局测量约束.
 *
 * 默认显示实线, 颜色取主题的 android:textColorSecondary, 缺失时回退到灰色; [lineColors] 支持状态色.
 * 间隔为 0 时显示实线; 间隔为正时根据点划长度显示虚线或点划线, 两段均为 0 时不绘制.
 * 线段和间隔长度拒绝非有限值, 负尺寸按 0 处理, 内容宽高不足时跳过绘制.
 *
 * @author whisper
 * @since 2025/06/16
 */
class KitLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.kitLineViewStyle,
    defStyleRes: Int = 0,
) : View(context, attrs, defStyleAttr, defStyleRes) {

    companion object {

        const val HORIZONTAL: Int = LinearLayout.HORIZONTAL

        const val VERTICAL: Int = LinearLayout.VERTICAL
    }

    /** 与 Android 原生 orientation 枚举一致的方向值. */
    @IntDef(HORIZONTAL, VERTICAL)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Orientation

    /** 沿线方向的点长, 单位 px. 默认 0, 负数按 0 处理, 非有限值抛出 IllegalArgumentException. */
    @get:Px
    @setparam:Px
    var dotWidth: Float = 0f
        set(value) {
            val normalizedValue: Float = normalizeLength(value)
            if (field == normalizedValue) {
                return
            }
            field = normalizedValue
            generateEffect()
            invalidate()
        }

    /** 沿线方向的划长, 单位 px. 默认 0, 负数按 0 处理, 非有限值抛出 IllegalArgumentException. */
    @get:Px
    @setparam:Px
    var dashWidth: Float = 0f
        set(value) {
            val normalizedValue: Float = normalizeLength(value)
            if (field == normalizedValue) {
                return
            }
            field = normalizedValue
            generateEffect()
            invalidate()
        }

    /** 点划之间的间隔, 单位 px. 0 表示实线, 负数按 0 处理, 非有限值抛出 IllegalArgumentException. */
    @get:Px
    @setparam:Px
    var gapWidth: Float = 0f
        set(value) {
            val normalizedValue: Float = normalizeLength(value)
            if (field == normalizedValue) {
                return
            }
            field = normalizedValue
            generateEffect()
            invalidate()
        }

    /** 随 View drawableState 解析的线条颜色. 纯色可通过 [setLineColor] 设置. */
    var lineColors: ColorStateList = ColorStateList.valueOf(Color.GRAY)
        set(value) {
            if (field === value) {
                return
            }
            field = value
            invalidate()
        }

    /** 线条方向, 仅接受 [HORIZONTAL] 和 [VERTICAL]. 修改后重新测量, 不自动交换布局宽高. */
    @get:Orientation
    @setparam:Orientation
    var orientation: Int = HORIZONTAL
        set(value) {
            require(value == HORIZONTAL || value == VERTICAL) { "Unsupported line orientation: $value" }
            if (field == value) {
                return
            }
            field = value
            requestLayout()
            invalidate()
        }

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var pathEffect: PathEffect? = null

    private val linePath: Path = Path()

    private val pathBounds: Rect = Rect()

    private var pathOrientation: Int = HORIZONTAL

    private var pathDirty: Boolean = true

    init {
        context.withStyledAttributes(attrs, R.styleable.KitLineView, defStyleAttr, defStyleRes) {
            dotWidth = getDimension(R.styleable.KitLineView_kitDotWidth, 0f)
            dashWidth = getDimension(R.styleable.KitLineView_kitDashWidth, 0f)
            gapWidth = getDimension(R.styleable.KitLineView_kitGapWidth, 0f)
            lineColors = getColorStateList(R.styleable.KitLineView_android_color) ?: resolveDefaultLineColors()
            orientation = getInt(R.styleable.KitLineView_android_orientation, HORIZONTAL)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        when (orientation) {
            HORIZONTAL -> {
                val w = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
                val desiredHeight: Int = max(suggestedMinimumHeight, paddingTop + paddingBottom + 1)
                val h: Int = resolveSizeAndState(desiredHeight, heightMeasureSpec, 0)
                setMeasuredDimension(w, h)
            }

            VERTICAL -> {
                val desiredWidth: Int = max(suggestedMinimumWidth, paddingLeft + paddingRight + 1)
                val w: Int = resolveSizeAndState(desiredWidth, widthMeasureSpec, 0)
                val h = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
                setMeasuredDimension(w, h)
            }

            else -> {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (gapWidth > 0f && dotWidth == 0f && dashWidth == 0f) {
            return
        }
        paint.color = lineColors.getColorForState(drawableState, lineColors.defaultColor)
        paint.pathEffect = pathEffect

        val l: Int = paddingLeft
        val t: Int = paddingTop
        val r: Int = width - paddingRight
        val b: Int = height - paddingBottom
        // strokeWidth 为 0 会绘制设备细线, 起终点反转也会画线, 因此空内容区必须提前退出.
        if (r <= l || b <= t) {
            return
        }

        val sx: Float
        val sy: Float
        val ex: Float
        val ey: Float

        when (orientation) {
            HORIZONTAL -> {
                val h: Float = (b - t).toFloat()
                paint.strokeWidth = h
                sx = l.toFloat()
                sy = t + h / 2f
                ex = r.toFloat()
                ey = t + h / 2f
            }

            VERTICAL -> {
                val w: Float = (r - l).toFloat()
                paint.strokeWidth = w
                sx = l + w / 2f
                sy = t.toFloat()
                ex = l + w / 2f
                ey = b.toFloat()
            }

            else -> {
                return
            }
        }
        if (pathEffect == null) {
            canvas.drawLine(sx, sy, ex, ey, paint)
        } else {
            // 旧版 HWUI 的 drawLine 不支持 PathEffect, 使用 drawPath 保留点划效果.
            if (pathDirty || pathBounds.left != l || pathBounds.top != t || pathBounds.right != r ||
                pathBounds.bottom != b || pathOrientation != orientation
            ) {
                linePath.rewind()
                linePath.moveTo(sx, sy)
                linePath.lineTo(ex, ey)
                pathBounds.set(l, t, r, b)
                pathOrientation = orientation
                pathDirty = false
            }
            canvas.drawPath(linePath, paint)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        // View 构造期也可能触发回调, 此处只请求重绘, 状态色在 onDraw 中解析.
        invalidate()
    }

    /** 使用纯色替换 [lineColors] 中的整个状态色列表. */
    fun setLineColor(@ColorInt color: Int) {
        lineColors = ColorStateList.valueOf(color)
    }

    private fun resolveDefaultLineColors(): ColorStateList {
        return context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary)).use {
            it.getColorStateList(0)
        } ?: ColorStateList.valueOf(Color.GRAY)
    }

    private fun normalizeLength(value: Float): Float {
        require(value.isFinite()) { "Line segment and gap lengths must be finite." }
        return max(value, 0f)
    }

    private fun generateEffect() {
        pathEffect = when {
            gapWidth == 0f || (dotWidth == 0f && dashWidth == 0f) -> null
            dotWidth > 0f && dashWidth > 0f -> DashPathEffect(
                floatArrayOf(dotWidth, gapWidth, dashWidth, gapWidth),
                0f,
            )
            else -> DashPathEffect(floatArrayOf(max(dotWidth, dashWidth), gapWidth), 0f)
        }
        // 旧版 HWUI 的纹理键包含 effect 原生地址和 Path 版本, 线型变化时也必须更新路径版本,
        // 避免旧 effect 回收后地址复用命中旧纹理; 仅颜色变化仍复用路径.
        pathDirty = true
    }
}
