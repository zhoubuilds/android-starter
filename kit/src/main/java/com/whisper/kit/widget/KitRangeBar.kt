package com.whisper.kit.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.annotation.IntRange
import androidx.annotation.Px
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import com.whisper.kit.R
import com.whisper.kit.widget.KitRangeBar.Companion.THUMB_UNLIMITED
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 范围选择控件
 *
 * **至少需要设置`kitBarForeground`或`kitBarBackground` 和 `kitThumb`**
 *
 * @author whisper
 * @since 2025/6/17
 */
class KitRangeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.kitRangeBarStyle,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    companion object {
        private const val THUMB_SIZE_INNER: Int = -1

        const val THUMB_UNLIMITED: Int = -1

        private val DRAWABLE_STATUS_ENABLE_DRAG =
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_drag_hovered)

        private val DRAWABLE_STATUS_ENABLE = intArrayOf(android.R.attr.state_enabled)

        private val DRAWABLE_STATUS_NONE = intArrayOf()

        /* default value */
        private const val DEFAULT_BAR_SIZE_PX = 8

        private const val DEFAULT_STEP = 1

        private const val DEFAULT_TOTAL = 100
    }

    /* xml */
    @Px
    private var mBarSize: Int = 0

    private var mBarForeground: Drawable? = null

    private var mBarBackground: Drawable? = null

    private var mThumb: Drawable? = null

    @Px
    private var mThumbWidth: Int = -1

    @Px
    private var mThumbHeight: Int = -1

    private var mStartThumbEnable: Boolean = true

    private var mEndThumbEnable: Boolean = true

    private var mGravity: Int = 0

    @IntRange(from = 1)
    private var mStep: Int = 1

    @IntRange(from = 1)
    private var mTotal: Int = 1

    @IntRange(from = -1)
    private var mStartMax: Int = -1

    @IntRange(from = -1)
    private var mEndMin: Int = -1

    @IntRange(from = 0)
    private var mMinSpan: Int = 0

    /* filed */
    private val mGraphicsPaint = Paint()

    private val mClearMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    private var mStart = 0

    private var mEnd = 0

    private var mTrackingStartPointerId = -1

    private var mTrackingEndPointerId = -1

    /* measured value */
    @Px
    var mContentVerticalStart: Float = 0f

    @Px
    var mBarLength: Int = 0

    @Px
    var mBarCenterX: Float = 0f

    @Px
    var mBarCenterY: Float = 0f

    private var mOnRangeChangedListener: OnRangeChangeListener? = null

    /* getter */
    private val mThumbStartCenterX: Float
        get() = if (mTotal <= 0) {
            mBarCenterX
        } else {
            mBarCenterX - mBarLength / 2f + (this.mStartInner * 1.0f / mTotal) * mBarLength
        }

    private val mThumbEndCenterX: Float
        get() = if (mTotal <= 0) {
            mBarCenterX
        } else {
            mBarCenterX - mBarLength / 2f + (this.mEndInner * 1.0f / mTotal) * mBarLength
        }

    private val mStartInner: Int
        get() = min(max(min(mStart, mEnd), 0), mTotal)

    private val mEndInner: Int
        get() = min(max(max(mStart, mEnd), 0), mTotal)

    init {
        mGraphicsPaint.isAntiAlias = true
        readAttrs(context, attrs, defStyleAttr, defStyleRes)
    }

    private fun readAttrs(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) {
        context.withStyledAttributes(attrs, R.styleable.KitRangeBar, defStyleAttr, defStyleRes) {
            mBarSize =
                getDimensionPixelSize(R.styleable.KitRangeBar_kitBarSize, DEFAULT_BAR_SIZE_PX)
            mBarForeground = getDrawable(R.styleable.KitRangeBar_kitBarForeground)
            mBarBackground = getDrawable(R.styleable.KitRangeBar_kitBarBackground)
            mThumb = getDrawable(R.styleable.KitRangeBar_kitThumb)

            val typedValue = TypedValue()

            getValue(R.styleable.KitRangeBar_kitThumbWidth, typedValue)
            mThumbWidth = if (typedValue.type == TypedValue.TYPE_DIMENSION) {
                getDimensionPixelSize(R.styleable.KitRangeBar_kitThumbWidth, THUMB_SIZE_INNER)
            } else {
                //TypedValue.TYPE_INT_DEC || TypedValue.TYPE_INT_HEX
                getInt(R.styleable.KitRangeBar_kitThumbWidth, THUMB_SIZE_INNER)
            }
            if (mThumbWidth < 0) {
                mThumb?.let { mThumbWidth = it.intrinsicWidth }
            }

            getValue(R.styleable.KitRangeBar_kitThumbHeight, typedValue)
            mThumbHeight = if (typedValue.type == TypedValue.TYPE_DIMENSION) {
                getDimensionPixelSize(
                    R.styleable.KitRangeBar_kitThumbHeight,
                    THUMB_SIZE_INNER
                )
            } else {
                //TypedValue.TYPE_INT_DEC || TypedValue.TYPE_INT_HEX
                getInt(R.styleable.KitRangeBar_kitThumbHeight, THUMB_SIZE_INNER)
            }
            if (mThumbHeight < 0) {
                mThumb?.let { mThumbHeight = it.intrinsicHeight }
            }

            isEnabled = getBoolean(R.styleable.KitRangeBar_android_enabled, true)
            mStartThumbEnable = getBoolean(R.styleable.KitRangeBar_kitStartThumbEnable, true)
            mEndThumbEnable = getBoolean(R.styleable.KitRangeBar_kitEndThumbEnable, true)
            mGravity = getInt(R.styleable.KitRangeBar_android_gravity, Gravity.BOTTOM)
            mStep = getInt(R.styleable.KitRangeBar_kitStep, DEFAULT_STEP)
            mTotal = getInt(R.styleable.KitRangeBar_kitTotal, DEFAULT_TOTAL)
            mStartMax = getInt(R.styleable.KitRangeBar_kitStartMax, THUMB_UNLIMITED)
            mEndMin = getInt(R.styleable.KitRangeBar_kitEndMin, THUMB_UNLIMITED)
            mMinSpan = getInt(R.styleable.KitRangeBar_kitMinSpan, 0)

            mStart = getInt(R.styleable.KitRangeBar_kitStart, 0)
            mEnd = getInt(R.styleable.KitRangeBar_kitEnd, mTotal)
            checkRange()
        }
    }

    private fun checkRange() {
        if (mStartMax > 0) {
            mStart = mStart.coerceIn(0, mStartMax.coerceAtMost(mTotal))
        }
        if (mEndMin > 0) {
            mEnd = mEnd.coerceIn(max(mStart, mEndMin), mTotal)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 总是想获取到尽可能大的宽度
        val width: Int = MeasureSpec.getSize(widthMeasureSpec)
        // 高度
        val contentHeight: Int = max(mBarSize, mThumbHeight)
        val verticalSpace: Int = paddingTop + contentHeight + paddingBottom
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            val height: Int = max(MeasureSpec.getSize(heightMeasureSpec), suggestedMinimumWidth)
            setMeasuredDimension(width, height)
        } else {
            setMeasuredDimension(width, verticalSpace)
        }

        // 计算各个元素的位置
        mContentVerticalStart = when (mGravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> paddingTop.toFloat()
            Gravity.BOTTOM -> (measuredHeight - paddingBottom - contentHeight).toFloat()

            Gravity.CENTER_VERTICAL -> paddingTop + (measuredHeight - paddingTop - paddingBottom - contentHeight) / 2.0f

            else -> paddingTop + (measuredHeight - paddingTop - paddingBottom - contentHeight) / 2.0f
        }

        mBarLength = measuredWidth - paddingStart - paddingEnd - mThumbWidth
        mBarCenterX = paddingStart + mThumbWidth / 2.0f + mBarLength / 2f
        mBarCenterY = mContentVerticalStart + max(mThumbHeight, mBarSize) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        val thumbStartCenterX = this.mThumbStartCenterX
        val thumbEndCenterX = this.mThumbEndCenterX

        // draw bar
        canvas.withTranslation(mBarCenterX - mBarLength / 2f, mBarCenterY - mBarSize / 2f) {
            mBarBackground?.let { drawable ->
                drawable.setBounds(0, 0, mBarLength, mBarSize)
                drawable.setState(drawableState)
                drawable.draw(this)
            }
            mBarForeground?.let { drawable ->
                withSave {
                    drawable.setBounds(0, 0, mBarLength, mBarSize)
                    drawable.setState(drawableState)
                    drawable.draw(this)
                    val startX: Float = if (mTotal <= 0) {
                        mBarCenterX
                    } else {
                        this@KitRangeBar.mStartInner * 1.0f / mTotal * mBarLength
                    }
                    val endX: Float = if (mTotal <= 0) {
                        mBarCenterX
                    } else {
                        this@KitRangeBar.mEndInner * 1.0f / mTotal * mBarLength
                    }
                    mGraphicsPaint.xfermode = mClearMode
                    drawRect(0f, 0f, startX, mBarSize.toFloat(), mGraphicsPaint)
                    drawRect(endX, 0f, mBarLength.toFloat(), mBarSize.toFloat(), mGraphicsPaint)
                }
            }
        }

        // draw thumb
        mThumb?.let { drawable ->
            drawable.setBounds(0, 0, mThumbWidth, mThumbHeight)
            if (mTrackingStartPointerId >= 0) {
                setEndThumbStatus(drawable)
                drawThumb(canvas, drawable, thumbEndCenterX, mBarCenterY)

                setStartThumbStatus(drawable)
                drawThumb(canvas, drawable, thumbStartCenterX, mBarCenterY)
            } else {
                setStartThumbStatus(drawable)
                drawThumb(canvas, drawable, thumbStartCenterX, mBarCenterY)

                setEndThumbStatus(drawable)
                drawThumb(canvas, drawable, thumbEndCenterX, mBarCenterY)
            }
        }
    }


    private fun drawThumb(canvas: Canvas, thumb: Drawable, cx: Float, xy: Float) {
        canvas.withTranslation(cx - mThumbWidth / 2f, xy - mThumbHeight / 2.0f) {
            thumb.draw(this)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val x = event.getX(event.actionIndex)
                val y = event.getY(event.actionIndex)

                val pointId = event.actionIndex

                val startExpectTrack: Boolean =
                    mStartThumbEnable && pressedStart(x, y) && mTrackingStartPointerId == -1
                val endExpectTrack: Boolean =
                    mEndThumbEnable && pressedEnd(x, y) && mTrackingEndPointerId == -1
                if (startExpectTrack && endExpectTrack) {
                    // 开始和结束都希望跟踪这个触控点,在MotionEvent.ACTION_MOVE中仲裁
                } else if (startExpectTrack) {
                    mTrackingStartPointerId = pointId
                } else if (endExpectTrack) {
                    mTrackingEndPointerId = pointId
                } else {
                    // 开始和结束都不希望跟踪这个触控点
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val x: Float = event.getX(event.actionIndex)
                val y: Float = event.getY(event.actionIndex)

                val pointId: Int = event.actionIndex

                val startExpectTrack =
                    mStartThumbEnable && pressedStart(x, y) && mTrackingStartPointerId == -1
                val endExpectTrack =
                    mEndThumbEnable && pressedEnd(x, y) && mTrackingEndPointerId == -1
                if (startExpectTrack && endExpectTrack) {
                    // 开始和结束都希望跟踪这个触控点,在MotionEvent.ACTION_MOVE中仲裁
                } else if (startExpectTrack) {
                    mTrackingStartPointerId = pointId
                } else if (endExpectTrack) {
                    mTrackingEndPointerId = pointId
                } else {
                    // 开始和结束都不希望跟踪这个触控点
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i: Int in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (mTrackingStartPointerId == pid) {
                        moveStart(event.getX(i))
                    } else if (mTrackingEndPointerId == pid) {
                        moveEnd(event.getX(i))
                    } else {
                        val historySize = event.historySize
                        if (historySize > 0) {
                            val dx = event.getX(i) - event.getHistoricalX(i, historySize - 1)
                            val hx = event.getHistoricalX(i, 0)
                            val hy = event.getHistoricalY(i, 0)

                            val startExpect = mStartThumbEnable
                                    && mTrackingStartPointerId == -1
                                    && pressedStart(hx, hy)
                            val endExpect = mEndThumbEnable
                                    && mTrackingEndPointerId == -1
                                    && pressedEnd(hx, hy)
                            if (startExpect && endExpect) {
                                // 开始和结束位置都希望处理这个触控点,进行仲裁
                                if (dx > 0) {
                                    // 往右移动的点仲裁给结束位置
                                    mTrackingEndPointerId = pid
                                    moveEnd(event.getX(i))
                                } else if (dx < 0) {
                                    // 往左移动的点仲裁给开始位置
                                    mTrackingStartPointerId = pid
                                    moveStart(event.getX(i))
                                } else {
                                    // 没有移动,不确定仲裁给哪个位置,等待下次仲裁
                                }
                            } else if (startExpect) {
                                // 这个分支仅容错,不应该执行到,如果只有一个位置期望跟踪,那么在发生DOWN事件时就应该将触控点分配给位置
                                mTrackingStartPointerId = pid
                                moveStart(event.getX(i))
                            } else if (endExpect) {
                                // 这个分支仅容错,不应该执行到,如果只有一个位置期望跟踪,那么在发生DOWN事件时就应该将触控点分配给位置
                                mTrackingEndPointerId = pid
                                moveEnd(event.getX(i))
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (mOnRangeChangedListener != null) {
                    mOnRangeChangedListener?.onRangeChange(true, this.mStartInner, this.mEndInner)
                }
                val pointerId: Int = event.getPointerId(event.actionIndex)
                if (pointerId == mTrackingStartPointerId) {
                    mTrackingStartPointerId = -1
                    invalidate()
                } else if (pointerId == mTrackingEndPointerId) {
                    mTrackingEndPointerId = -1
                    invalidate()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId: Int = event.getPointerId(event.actionIndex)
                if (pointerId == mTrackingStartPointerId) {
                    mTrackingStartPointerId = -1
                    invalidate()
                } else if (pointerId == mTrackingEndPointerId) {
                    mTrackingEndPointerId = -1
                    invalidate()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                mTrackingStartPointerId = -1
                mTrackingEndPointerId = -1
                if (mOnRangeChangedListener != null) {
                    mOnRangeChangedListener?.onRangeChange(true, this.mStartInner, this.mEndInner)
                }
                invalidate()
            }
        }
        return true
    }

    private fun setStartThumbStatus(drawable: Drawable): Boolean =
        if (isEnabled && mStartThumbEnable) {
            if (mTrackingStartPointerId == -1) {
                drawable.setState(DRAWABLE_STATUS_ENABLE)
            } else {
                drawable.setState(DRAWABLE_STATUS_ENABLE_DRAG)
            }
        } else {
            drawable.setState(DRAWABLE_STATUS_NONE)
        }

    private fun setEndThumbStatus(drawable: Drawable): Boolean =
        if (isEnabled && mEndThumbEnable) {
            if (mTrackingEndPointerId == -1) {
                drawable.setState(DRAWABLE_STATUS_ENABLE)
            } else {
                drawable.setState(DRAWABLE_STATUS_ENABLE_DRAG)
            }
        } else {
            drawable.setState(DRAWABLE_STATUS_NONE)
        }

    private fun pressedStart(x: Float, y: Float): Boolean {
        val thumbStartCenterX: Float = this.mThumbStartCenterX
        // 扩充一些触控区域
        val expandTouchHorizontal: Float = mThumbWidth / 2f
        val expandTouchVertical: Float = mThumbHeight / 2f
        return x >= thumbStartCenterX - expandTouchHorizontal && x <= thumbStartCenterX + expandTouchHorizontal && y >= mBarCenterY - expandTouchVertical && y <= mBarCenterY + expandTouchVertical
    }

    private fun pressedEnd(x: Float, y: Float): Boolean {
        val thumbEndCenterX: Float = this.mThumbEndCenterX
        // 扩充一些触控区域
        val expandTouchHorizontal: Float = mThumbWidth / 2f
        val expandTouchVertical: Float = mThumbHeight / 2f
        return x >= thumbEndCenterX - expandTouchHorizontal && x <= thumbEndCenterX + expandTouchHorizontal && y >= mBarCenterY - expandTouchVertical && y <= mBarCenterY + expandTouchVertical
    }

    private fun moveStart(x: Float) {
        if (!mStartThumbEnable) {
            return
        }
        val length: Float = min(max(x - (mBarCenterX - mBarLength / 2f), 0f), mBarLength.toFloat())
        val max: Int = if (mStartMax > 0) {
            mStartMax.coerceAtLeast(0)
        } else {
            mTotal
        }.coerceAtMost(mEnd - mMinSpan)
        mStart = ((length / mBarLength * mTotal / mStep).roundToInt() * mStep).coerceIn(0, max)
        if (mOnRangeChangedListener != null) {
            mOnRangeChangedListener?.onRangeChange(false, this.mStartInner, this.mEndInner)
        }
        invalidate()
    }

    private fun moveEnd(x: Float) {
        if (!mEndThumbEnable) {
            return
        }
        val length: Float = min(max(x - (mBarCenterX - mBarLength / 2f), 0f), mBarLength.toFloat())
        val min: Int = if (mEndMin > 0) {
            mEndMin.coerceAtMost(mTotal)
        } else {
            0
        }.coerceAtLeast(mStart + mMinSpan)
        mEnd = ((length / mBarLength * mTotal / mStep).roundToInt() * mStep).coerceIn(min, mTotal)
        if (mOnRangeChangedListener != null) {
            mOnRangeChangedListener?.onRangeChange(false, this.mStartInner, this.mEndInner)
        }
        invalidate()
    }

    /**
     * 设置范围条的背景
     *
     *
     * 背景用在未选择范围上
     *
     * @param drawable 范围条的背景, 设置为`null`不会显示
     */
    @Suppress("unused")
    fun setBarBackground(drawable: Drawable?) {
        mBarBackground = drawable
    }

    /**
     * 设置范围条的前景
     *
     *
     * 前景用在已经选择范围上
     *
     * @param drawable 范围条的前景, 设置为`null`不会显示
     */
    @Suppress("unused")
    fun setBarForeground(drawable: Drawable?) {
        mBarForeground = drawable
    }

    /**
     * 设置拖动按钮
     *
     * @param drawable 拖动按钮, 设置为`null`不会显示
     */
    @Suppress("unused")
    fun setThumb(drawable: Drawable?) {
        mThumb = drawable
        if (mThumbWidth < 0) {
            mThumb?.let { mThumbWidth = it.intrinsicWidth }
        }
        if (mThumbHeight < 0) {
            mThumb?.let { mThumbHeight = it.intrinsicHeight }
        }
    }

    /**
     * 设置范围改变监听器
     *
     * @param listener 监听器
     */
    @Suppress("unused")
    fun setOnRangeChangedListener(listener: OnRangeChangeListener?) {
        this.setOnRangeChangedListener(false, listener)
    }

    /**
     * 设置范围改变监听器
     *
     * @param invokeImmediately 设置后是否立即通知
     * @param listener          监听器
     */
    fun setOnRangeChangedListener(invokeImmediately: Boolean, listener: OnRangeChangeListener?) {
        mOnRangeChangedListener = listener
        if (invokeImmediately && listener != null) {
            listener.onRangeChange(true, this.mStartInner, this.mEndInner)
        }
    }

    /**
     * 设置可选范围大小
     *
     * @param total 可选范围大小
     */
    @Suppress("unused")
    fun setTotal(@IntRange(from = 1) total: Int) {
        mTotal = max(total, 1)
        invalidate()
    }

    @set:Suppress("unused")
    var start: Int
        /**
         * 获取开始位置
         *
         * @return 开始位置
         */
        get() = mStart
        /**
         * 设置开始位置
         *
         * @param start 开始位置
         */
        set(start) {
            mStart = max(start, 0)
            if (mOnRangeChangedListener != null) {
                mOnRangeChangedListener?.onRangeChange(false, this.mStartInner, this.mEndInner)
            }
            invalidate()
        }

    @set:Suppress("unused")
    var end: Int
        /**
         * 获取结束位置
         *
         * @return 结束位置
         */
        get() = mEnd
        /**
         * 设置结束位置
         *
         * @param end 结束位置
         */
        set(end) {
            mEnd = max(end, 0)
            if (mOnRangeChangedListener != null) {
                mOnRangeChangedListener?.onRangeChange(false, this.mStartInner, this.mEndInner)
            }
            invalidate()
        }

    /**
     * 设置选择的范围
     *
     * @param start 开始
     * @param end   结束
     */
    @Suppress("unused")
    fun setSelectedRange(@IntRange(from = 0) start: Int, @IntRange(from = 0) end: Int) {
        mStart = max(start, end)
        mEnd = max(start, end)
        invalidate()
    }

    /**
     * 设置开始位置最大范围限制
     *
     * @param startMax 开始位置最大范围, 或者 [THUMB_UNLIMITED]不限制
     */
    @Suppress("unused")
    fun setStartMax(@IntRange(from = -1) startMax: Int) {
        mStartMax = startMax.coerceIn(-1, mTotal)
        invalidate()
    }

    /**
     * 设置结束位置最小范围限制
     *
     * @param endMin 结束位置最小范围, 或者 [THUMB_UNLIMITED]不限制
     */
    @Suppress("unused")
    fun setEndMin(@IntRange(from = -1) endMin: Int) {
        mEndMin = endMin.coerceIn(-1, mTotal)
        invalidate()
    }

    /**
     * 设置最小选择范围限制
     *
     * @param minSpan 最小范围
     */
    @Suppress("unused")
    fun setMinSpan(@IntRange(from = 0) minSpan: Int) {
        mMinSpan = minSpan.coerceAtLeast(0)
        invalidate()
    }

    fun interface OnRangeChangeListener {
        /**
         * 范围改变
         *
         * @param release 是否已经释放按钮. 拖动时为false, 手指离开时为true
         * @param start   开始
         * @param end     结束
         */
        fun onRangeChange(release: Boolean, start: Int, end: Int)
    }


}
