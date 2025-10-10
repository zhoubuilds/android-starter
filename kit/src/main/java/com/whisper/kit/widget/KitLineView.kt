package com.whisper.kit.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PathEffect
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.core.content.withStyledAttributes
import com.whisper.kit.R
import kotlin.math.max

/**
 * 分割线View
 * 可以配置实现
 *  * 1.实线(点划至少有一个不为0, 间隔为0) ————
 *  * 2.虚线(点划相等且不为0或者点划只有一个大于0, 间隔大于O) ----
 *  * 3.点划线(点划不相等且都大于0, 间隔大于0) - · - · - · -
 *
 * @author whisper
 * @since 2025/6/16
 */
class KitLineView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.kitLineViewStyle,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    companion object {

        const val HORIZONTAL: Int = 1

        const val VERTICAL: Int = 2
    }

    @IntDef(HORIZONTAL, VERTICAL)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Orientation

    private var _dotWidth: Int = 1

    private var _dashWidth: Int = 0

    private var _gapWidth: Int = 0

    @ColorInt
    private var _color: Int = 0xFFFFFFFF.toInt()

    @Orientation
    private var _orientation: Int = HORIZONTAL

    private val _paint: Paint = Paint()

    private var _pathEffect: PathEffect? = null

    init {
        initialize(attrs, defStyleAttr, defStyleRes)
    }

    private fun initialize(attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        _paint.style = Paint.Style.STROKE
        _paint.isAntiAlias = true

        context.withStyledAttributes(attrs, R.styleable.KitLineView, defStyleAttr, defStyleRes) {
            _dotWidth = getDimensionPixelOffset(R.styleable.KitLineView_kitDotWidth, 0)
            _dashWidth = getDimensionPixelOffset(R.styleable.KitLineView_kitDashWidth, 0)
            _gapWidth = getDimensionPixelOffset(R.styleable.KitLineView_kitGapWidth, 0)
            _color = getColor(R.styleable.KitLineView_android_color, 0xFFFFFFFF.toInt())
            _orientation = getInt(R.styleable.KitLineView_android_orientation, HORIZONTAL)
        }
        generateEffect()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        when (_orientation) {
            HORIZONTAL -> {
                val w = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
                val h: Int = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
                    MeasureSpec.getSize(heightMeasureSpec)
                } else {
                    1
                }
                setMeasuredDimension(w, h)
            }

            VERTICAL -> {
                val w: Int = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
                    MeasureSpec.getSize(widthMeasureSpec)
                } else {
                    1
                }
                val h = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
                setMeasuredDimension(w, h)
            }

            else -> {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val dot = max(_dotWidth, 0)
        val dash = max(_dashWidth, 0)
        if (dot == 0 && dash == 0) {
            return
        }
        _paint.setColor(_color)
        _paint.setPathEffect(_pathEffect)

        val l: Int = paddingLeft
        val t: Int = paddingTop
        val r: Int = measuredWidth - paddingRight
        val b: Int = measuredHeight - paddingBottom

        val sx: Float
        val sy: Float
        val ex: Float
        val ey: Float

        when (_orientation) {
            HORIZONTAL -> {
                val h = max(b - t, 0).toFloat()
                _paint.strokeWidth = h
                sx = l.toFloat()
                sy = t + h / 2f
                ex = r.toFloat()
                ey = t + h / 2f
            }

            VERTICAL -> {
                val w = max(r - l, 0).toFloat()
                _paint.strokeWidth = w
                sx = l + w / 2f
                sy = t.toFloat()
                ex = l + w / 2f
                ey = b.toFloat()
            }

            else -> {
                return
            }
        }
        canvas.drawLine(sx, sy, ex, ey, _paint)
    }

    private fun generateEffect() {
        val dot: Int = max(_dotWidth, 0)
        val dash: Int = max(_dashWidth, 0)
        val gap: Int = max(_gapWidth, 0)
        if (gap == 0) {
            // 退化为实线(显示为实线还需要一个条件,dot 和 dash 至少有一个大于0)
            _pathEffect = null
        } else {
            if (dot != 0 && dash != 0) {
                // 点划线
                _pathEffect = DashPathEffect(
                    floatArrayOf(dot.toFloat(), gap.toFloat(), dash.toFloat(), gap.toFloat()),
                    0f
                )
            } else if (dot == 0 && dash == 0) {
                // 没有线, 因为点划的尺寸都是0, 绘制时需要判断
                _pathEffect = null
            } else {
                // 退化为虚线
                _pathEffect =
                    DashPathEffect(floatArrayOf(max(dot, dash).toFloat(), gap.toFloat()), 0f)
            }
        }
    }
}
