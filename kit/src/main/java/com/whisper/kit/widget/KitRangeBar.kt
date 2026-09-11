package com.whisper.kit.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.annotation.IntRange
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.core.util.isEmpty
import androidx.core.util.size
import androidx.core.view.ViewCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.whisper.kit.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 水平整数范围选择控件, 支持双指独立拖动, 两端可重合但不能交叉.
 *
 * 至少配置一种轨道 Drawable 及滑块外观. 两端独立外观优先于通用 [setThumb] 配置.
 * 所有赋值和拖动只选择从 0 开始的步长整数倍, total 不能整除步长时不会补齐最后一个刻度.
 * XML、双端赋值和约束修改选择两端平方位移之和最小的合法范围, 等距时依次取较小起点和终点.
 * 无合法范围时抛出 IllegalArgumentException, 运行时保留旧配置. 数值和约束运算使用 Int/Long.
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
        const val THUMB_UNLIMITED: Int = -1

        /** 独立端点继承通用外观的哨兵, 仅用于 [setStartThumb] 和 [setEndThumb]; null 表示清空. */
        @JvmField
        val INHERIT_THUMB: Drawable = ColorDrawable()

        private const val THUMB_SIZE_INNER: Int = -1
        private const val NO_POINTER: Int = -1
        private const val START: Int = 1
        private const val END: Int = 2
        private const val TOUCH: Int = 1
        private const val KEYBOARD: Int = 2
        private const val ACCESSIBILITY: Int = 3
        private const val DEFAULT_BAR_SIZE_PX: Int = 8
        private const val DEFAULT_STEP: Int = 1
        private const val DEFAULT_TOTAL: Int = 100
    }

    /** 固定终点, 将起点吸附到最近合法刻度; 等距时取较小值, 仅实际变化时通知. */
    var start: Int
        get() = selectedStart
        set(value) {
            applySelection(KitRangeSelection(nearestAllowedValue(value, startLowerBound(), startUpperBound()), selectedEnd))
        }

    /** 固定起点, 将终点吸附到最近合法刻度; 等距时取较小值, 仅实际变化时通知. */
    var end: Int
        get() = selectedEnd
        set(value) {
            applySelection(KitRangeSelection(selectedStart, nearestAllowedValue(value, endLowerBound(), total.toLong())))
        }

    /** 起点的无障碍名称, null 使用通用名称; 业务名称由调用方提供. */
    var startThumbLabel: CharSequence? = null
        set(value) {
            field = value
            if (initialized) {
                accessibilityHelper.invalidateNodes()
            }
        }

    /** 终点的无障碍名称, null 使用通用名称. */
    var endThumbLabel: CharSequence? = null
        set(value) {
            field = value
            if (initialized) {
                accessibilityHelper.invalidateNodes()
            }
        }

    /** 默认随 RTL 镜像数值方向. false 固定数值从左到右, 不改变 View 的布局方向和 padding. */
    var isRtlMirroringEnabled: Boolean = true
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (initialized) {
                rebasePointers()
                accessibilityHelper.invalidateRoot()
                invalidate()
            }
        }

    private var initialized: Boolean = false
    private var barSize: Int = DEFAULT_BAR_SIZE_PX
    private var barForeground: Drawable? = null
    private var barBackground: Drawable? = null
    private var commonThumb: Drawable? = null
    private var startThumbOverride: Drawable? = INHERIT_THUMB
    private var endThumbOverride: Drawable? = INHERIT_THUMB
    private val startThumb: Thumb = Thumb()
    private val endThumb: Thumb = Thumb()
    private var thumbWidth: Int = THUMB_SIZE_INNER
    private var thumbHeight: Int = THUMB_SIZE_INNER
    private var startThumbEnabled: Boolean = true
    private var endThumbEnabled: Boolean = true
    private var gravity: Int = Gravity.BOTTOM
    private var step: Int = DEFAULT_STEP
    private var total: Int = DEFAULT_TOTAL
    private var startMin: Int = THUMB_UNLIMITED
    private var startMax: Int = THUMB_UNLIMITED
    private var endMin: Int = THUMB_UNLIMITED
    private var minSpan: Int = 0
    private var selectedStart: Int = 0
    private var selectedEnd: Int = DEFAULT_TOTAL
    private var barLeft: Int = 0
    private var barLength: Int = 0
    private var barCenterY: Int = 0
    private var drawablesVisible: Boolean = false
    private var gestureActive: Boolean = false
    private var drawableMutationDepth: Int = 0
    private var onRangeChangedListener: OnRangeChangeListener? = null
    private var onInteractionListener: OnInteractionListener? = null
    private var interaction: Interaction? = null
    private val pointers: SparseArray<Pointer> = SparseArray()
    private val deferredDrawableTasks: MutableList<DeferredDrawableTask> = mutableListOf()
    // ExploreByTouchHelper 构造时会强制启用焦点, 需提前保留 XML 配置及 API 26 起的 auto 模式.
    private val initiallyFocusable: Boolean = isFocusable
    private val initialFocusability: Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusable
    } else {
        null
    }
    private val accessibilityHelper: KitRangeAccessibilityHelper = KitRangeAccessibilityHelper(
        this, ::accessibilityNode, ::adjustFromAccessibility,
    ) {
        if (initialized) {
            if (interaction?.kind == KEYBOARD) {
                finishInteraction(cancelled = true)
            }
            updateDrawableStates()
        }
    }
    private val isValueRtl: Boolean
        get() = isRtlMirroringEnabled && layoutDirection == LAYOUT_DIRECTION_RTL

    init {
        context.withStyledAttributes(attrs, R.styleable.KitRangeBar, defStyleAttr, defStyleRes) {
            isEnabled = getBoolean(R.styleable.KitRangeBar_android_enabled, true)
            if (!hasValue(R.styleable.KitRangeBar_android_focusable)) {
                isFocusable = true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialFocusability != null) {
                setFocusable(initialFocusability)
            } else {
                isFocusable = initiallyFocusable
            }
            startThumbLabel = getText(R.styleable.KitRangeBar_kitStartThumbLabel)
            endThumbLabel = getText(R.styleable.KitRangeBar_kitEndThumbLabel)
            isRtlMirroringEnabled = getBoolean(R.styleable.KitRangeBar_kitRtlMirroringEnabled, true)
            barSize = getDimensionPixelSize(R.styleable.KitRangeBar_kitBarSize, DEFAULT_BAR_SIZE_PX).coerceAtLeast(0)
            thumbWidth = readThumbSize(peekValue(R.styleable.KitRangeBar_kitThumbWidth))
            thumbHeight = readThumbSize(peekValue(R.styleable.KitRangeBar_kitThumbHeight))
            startThumbEnabled = getBoolean(R.styleable.KitRangeBar_kitStartThumbEnable, true)
            endThumbEnabled = getBoolean(R.styleable.KitRangeBar_kitEndThumbEnable, true)
            gravity = getInt(R.styleable.KitRangeBar_android_gravity, Gravity.BOTTOM)
            step = getInt(R.styleable.KitRangeBar_kitStep, DEFAULT_STEP)
            require(step > 0) { "kitStep must be positive" }
            total = getInt(R.styleable.KitRangeBar_kitTotal, DEFAULT_TOTAL).coerceAtLeast(1)
            startMin = getInt(R.styleable.KitRangeBar_kitStartMin, THUMB_UNLIMITED).coerceIn(-1, total)
            startMax = getInt(R.styleable.KitRangeBar_kitStartMax, THUMB_UNLIMITED).coerceIn(-1, total)
            endMin = getInt(R.styleable.KitRangeBar_kitEndMin, THUMB_UNLIMITED).coerceIn(-1, total)
            minSpan = getInt(R.styleable.KitRangeBar_kitMinSpan, 0).coerceAtLeast(0)
            val initialStart = getInt(R.styleable.KitRangeBar_kitStart, 0)
            val initialEnd = getInt(R.styleable.KitRangeBar_kitEnd, total)
            val selection = nearestSelection(min(initialStart, initialEnd), max(initialStart, initialEnd))
            selectedStart = selection.start
            selectedEnd = selection.end
            barBackground = getDrawable(R.styleable.KitRangeBar_kitBarBackground)?.mutate()
            barForeground = getDrawable(R.styleable.KitRangeBar_kitBarForeground)?.mutate()
            replaceThumbs(
                getDrawable(R.styleable.KitRangeBar_kitThumb)?.mutate(),
                readThumb(R.styleable.KitRangeBar_kitStartThumb),
                readThumb(R.styleable.KitRangeBar_kitEndThumb)
            )
        }
        initialized = true
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        ownedDrawables().forEach(::attachDrawable)
        updateDrawableStates()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateThumbSizes()
        val thumbSpace = max(startThumb.width, endThumb.width)
        val desiredWidth = max(suggestedMinimumWidth, saturatedSize(paddingLeft, thumbSpace, paddingRight))
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            desiredWidth
        } else {
            MeasureSpec.getSize(widthMeasureSpec)
        }
        val desiredHeight = max(suggestedMinimumHeight, saturatedSize(paddingTop, contentHeight(), paddingBottom))
        setMeasuredDimension(
            resolveSizeAndState(width, widthMeasureSpec, 0),
            resolveSizeAndState(desiredHeight, heightMeasureSpec, 0)
        )
        updateGeometry(measuredWidth, measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateGeometry(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (barLength > 0 && barSize > 0) {
            canvas.withTranslation(barLeft.toFloat(), (barCenterY - barSize / 2).toFloat()) {
                barBackground?.let {
                    drawDrawable(this, it, drawableState, 0, 0, barLength, barSize)
                }
                barForeground?.let {
                    withSave {
                        val startOffset = valueOffset(selectedStart)
                        val endOffset = valueOffset(selectedEnd)
                        clipRect(min(startOffset, endOffset), 0, max(startOffset, endOffset), barSize)
                        drawDrawable(this, it, drawableState, 0, 0, barLength, barSize)
                    }
                }
            }
        }
        if (startThumb.pointerId != NO_POINTER) {
            drawThumb(canvas, endThumb, selectedEnd)
            drawThumb(canvas, startThumb, selectedStart)
        } else {
            drawThumb(canvas, startThumb, selectedStart)
            drawThumb(canvas, endThumb, selectedEnd)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || visibility != VISIBLE) {
            finishInteraction(cancelled = true)
            return false
        }
        if (event.actionMasked != MotionEvent.ACTION_DOWN && !gestureActive) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                finishInteraction(cancelled = true)
                if (interaction != null || !isEnabled || visibility != VISIBLE) {
                    return false
                }
                clearPointers()
                if (!addPointer(event, event.actionIndex)) {
                    return false
                }
                gestureActive = true
                parent?.requestDisallowInterceptTouchEvent(true)
                beginInteraction(TOUCH)
            }
            MotionEvent.ACTION_POINTER_DOWN -> addPointer(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> updateTouchSelection(event)
            MotionEvent.ACTION_POINTER_UP -> {
                val active = interaction
                val pointerId = event.getPointerId(event.actionIndex)
                val cancelled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    event.flags and MotionEvent.FLAG_CANCELED != 0
                // 单指误触取消不采纳其最终坐标, 其余触点继续更新并允许等待触点接管.
                if (cancelled) {
                    releasePointer(pointerId)
                }
                updateTouchSelection(event)
                if (gestureActive && interaction === active) {
                    if (!cancelled) {
                        releasePointer(pointerId)
                    }
                    resolvePendingPointers()
                    if (pointers.isEmpty()) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val active = interaction
                updateTouchSelection(event)
                // 最终值回调可能取消当前手势或开启新交互, 旧 UP 只结束原会话.
                if (gestureActive && interaction === active) {
                    finishInteraction(cancelled = false)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                finishInteraction(cancelled = true)
            }
        }
        return true
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (initialized && !enabled) {
            finishInteraction(cancelled = true)
            accessibilityHelper.invalidateRoot()
        }
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (initialized && visibility != VISIBLE) {
            finishInteraction(cancelled = true)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (initialized) {
            updateDrawableStates()
            accessibilityHelper.invalidateNodes()
        }
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        initialized && ownedDrawables().any { it === who } || super.verifyDrawable(who)

    override fun invalidateDrawable(drawable: Drawable) {
        if (initialized && ownedDrawables().any { it === drawable }) {
            if (drawableMutationDepth > 0) {
                return
            }
            if (updateThumbSizes()) {
                requestLayout()
            }
            invalidate()
        } else {
            super.invalidateDrawable(drawable)
        }
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        if (initialized && ownedDrawables().any { it === who }) {
            if (isSharedDrawable(who)) {
                return
            }
            if (!isAttachedToWindow) {
                // View 的未连接队列无法按 Drawable 清理, 暂存后在连接时转交原生调度.
                deferredDrawableTasks.add(DeferredDrawableTask(who, what, (`when` - SystemClock.uptimeMillis()).coerceAtLeast(0L)))
                return
            }
        }
        super.scheduleDrawable(who, what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        if (initialized) {
            deferredDrawableTasks.removeAll { it.drawable === who && it.action === what }
        }
        super.unscheduleDrawable(who, what)
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        if (initialized) {
            ownedDrawables().forEach { it.jumpToCurrentState() }
        }
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        if (initialized) {
            rebasePointers()
            ownedDrawables().forEach { it.layoutDirection = layoutDirection }
            updateThumbSizes()
            requestLayout()
            invalidate()
            accessibilityHelper.invalidateRoot()
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        drawablesVisible = isVisible
        if (initialized) {
            ownedDrawables().forEach { it.setVisible(isVisible && isAttachedToWindow, false) }
            if (!isVisible) {
                finishInteraction(cancelled = true)
            }
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (initialized && visibility != VISIBLE) {
            finishInteraction(cancelled = true)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ownedDrawables().forEach { it.setVisible(drawablesVisible && isShown, false) }
        val pending = deferredDrawableTasks.toList()
        deferredDrawableTasks.clear()
        val now = SystemClock.uptimeMillis()
        pending.forEach { scheduleDrawable(it.drawable, it.action, now + it.delay) }
    }

    override fun onDetachedFromWindow() {
        finishInteraction(cancelled = true)
        ownedDrawables().forEach {
            it.setVisible(false, false)
            cancelDrawableTasks(it)
        }
        super.onDetachedFromWindow()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (initialized) {
            if (!gainFocus && interaction?.kind == KEYBOARD) {
                finishInteraction(cancelled = true)
            }
            accessibilityHelper.onFocusChanged(false, direction, previouslyFocusedRect)
            if (gainFocus) {
                val target = when (direction) {
                    FOCUS_BACKWARD -> END
                    FOCUS_LEFT -> if (isValueRtl) START else END
                    FOCUS_RIGHT -> if (isValueRtl) END else START
                    else -> START
                }
                accessibilityHelper.requestKeyboardFocusForVirtualView(target)
            }
            updateDrawableStates()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (initialized && !hasWindowFocus) {
            finishInteraction(cancelled = true)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val finishing = interaction?.takeIf {
            it.kind == KEYBOARD && event.action == KeyEvent.ACTION_UP && it.keyCode == event.keyCode
        }
        val handled = super.dispatchKeyEvent(event)
        // OnKeyListener 优先处理事件; 即使消费 UP, 已开始的按键交互也必须在物理释放时收尾.
        if (finishing != null && interaction === finishing) {
            finishInteraction(cancelled = event.isCanceled)
            return true
        }
        return handled
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isFocused || !isEnabled) {
            return super.onKeyDown(keyCode, event)
        }
        if (keyCode == KeyEvent.KEYCODE_TAB &&
            (event.hasNoModifiers() || event.hasModifiers(KeyEvent.META_SHIFT_ON))
        ) {
            if (interaction?.kind == KEYBOARD) {
                finishInteraction(cancelled = true)
            }
            val current = accessibilityHelper.keyboardFocusedVirtualViewId
            val next = if (event.isShiftPressed) {
                if (current == END) START else ExploreByTouchHelper.INVALID_ID
            } else {
                if (current == START) END else ExploreByTouchHelper.INVALID_ID
            }
            if (next != ExploreByTouchHelper.INVALID_ID) {
                accessibilityHelper.requestKeyboardFocusForVirtualView(next)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return super.onKeyDown(keyCode, event)
        }
        if (!event.hasNoModifiers()) {
            return super.onKeyDown(keyCode, event)
        }
        if (interaction != null && interaction?.kind != KEYBOARD) {
            return true
        }
        if (interaction?.keyCode != keyCode) {
            if (interaction != null) {
                finishInteraction(cancelled = true)
            }
            val target = accessibilityHelper.keyboardFocusedVirtualViewId
            if (!isFocused || target != START && target != END || !isThumbEnabled(target)) {
                return true
            }
            if (beginInteraction(KEYBOARD, keyCode) == null) {
                return true
            }
        }
        val target = accessibilityHelper.keyboardFocusedVirtualViewId
        if (target != START && target != END || !isThumbEnabled(target)) {
            return true
        }
        val increase = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != isValueRtl
        adjustThumb(target, increase)
        return true
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        // 只通过 DOWN/UP 和 DOWN.repeat 调节, 防止旧式 MULTIPLE 合成的回调绕过会话收尾.
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB -> true
            else -> super.onKeyMultiple(keyCode, repeatCount, event)
        }
    }

    override fun onSaveInstanceState(): Parcelable = SavedState(
        super.onSaveInstanceState(), selectedStart, selectedEnd, total, step, startMax, endMin, minSpan, startMin,
    )

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val selection = nearestKitRangeSelection(
            state.start, state.end, state.total, state.step, state.startMax, state.endMin, state.minSpan, state.startMin,
        )
        val constraintsChanged = total != state.total || step != state.step || startMax != state.startMax ||
            endMin != state.endMin || minSpan != state.minSpan || startMin != state.startMin
        // 一致快照直接提交, 避免逐个 setter 在中间约束下失败, 也不恢复历史交互.
        interaction = null
        clearPointers()
        super.onRestoreInstanceState(state.superState)
        total = state.total
        step = state.step
        startMin = state.startMin
        startMax = state.startMax
        endMin = state.endMin
        minSpan = state.minSpan
        applySelection(selection, constraintsChanged)
    }

    /** 设置未选中部分使用的轨道背景, null 表示不绘制. */
    fun setBarBackground(drawable: Drawable?) {
        require(drawable !== INHERIT_THUMB) { "INHERIT_THUMB is only valid for individual thumb overrides" }
        if (barBackground === drawable) {
            return
        }
        val previous = barBackground
        barBackground = drawable
        releaseDrawable(previous)
        barBackground?.let(::attachDrawable)
        updateDrawableStates()
    }

    /** 设置选中部分使用的轨道前景, null 表示不绘制. */
    fun setBarForeground(drawable: Drawable?) {
        require(drawable !== INHERIT_THUMB) { "INHERIT_THUMB is only valid for individual thumb overrides" }
        if (barForeground === drawable) {
            return
        }
        val previous = barForeground
        barForeground = drawable
        releaseDrawable(previous)
        barForeground?.let(::attachDrawable)
        updateDrawableStates()
    }

    /**
     * 设置两端共用的外观配置, 独立端点配置优先. null 清除通用外观.
     *
     * 直接使用传入实例, 不复制、mutate 或改写透明度、滤镜和 tint.
     * 从资源加载的实例应由调用方先 mutate, 再配置外观, 避免容器 mutate 重建子项丢失运行时配置.
     * 同一实例供多个端点或轨道使用时, 临时切换状态和边界, 跳过状态过渡并停止动画调度.
     * 需要动画或分别改色时提供不同实例. Drawable 由本控件管理, 不要同时交给其它 View 使用.
     */
    fun setThumb(drawable: Drawable?) {
        replaceThumbs(drawable, startThumbOverride, endThumbOverride)
    }

    /** 设置起点独立外观, null 清空; [INHERIT_THUMB] 恢复通用外观. */
    fun setStartThumb(drawable: Drawable?) {
        replaceThumbs(commonThumb, drawable, endThumbOverride)
    }

    /** 设置终点独立外观, null 清空; [INHERIT_THUMB] 恢复通用外观. */
    fun setEndThumb(drawable: Drawable?) {
        replaceThumbs(commonThumb, startThumbOverride, drawable)
    }

    /** 当前起点实际绘制的 Drawable, 继承通用外观时可与终点为同一对象. */
    fun getStartThumb(): Drawable? = startThumb.drawable

    /** 当前终点实际绘制的 Drawable, 继承通用外观时可与起点为同一对象. */
    fun getEndThumb(): Drawable? = endThumb.drawable

    /** 仅有效选区实际变化时通知; 注册不立即回调, 初始值可读取 [start] 和 [end]. */
    fun setOnRangeChangedListener(listener: OnRangeChangeListener?) {
        onRangeChangedListener = listener
    }

    /** 监听完整交互. 已开始的交互继续使用开始时的监听器, 替换只影响后续交互. */
    fun setOnInteractionListener(listener: OnInteractionListener?) {
        onInteractionListener = listener
    }

    /** 设置数值上界, 至少为 1, 并调整到最近合法范围. 无解时拒绝设置, 不放宽其它约束. */
    fun setTotal(@IntRange(from = 1) total: Int) {
        val newTotal = total.coerceAtLeast(1)
        if (this.total == newTotal) {
            return
        }
        val selection = nearestSelection(total = newTotal)
        this.total = newTotal
        applySelection(selection, constraintsChanged = true)
    }

    /** 将参数排序后联合吸附到最近合法范围, 选区实际变化时通知一次. */
    fun setSelectedRange(@IntRange(from = 0) start: Int, @IntRange(from = 0) end: Int) {
        applySelection(nearestSelection(min(start, end), max(start, end)))
    }

    /**
     * 设置包含边界的起点下限, [THUMB_UNLIMITED] 表示不限制, 参数归一化到 -1..total.
     *
     * 步长仍从 0 起算, 非刻度下限取其上的首个合法刻度; 两端不能交叉, 因而终点也不能低于此下限.
     * 调整到最近合法范围, 无解时拒绝设置并保留原状态; 不改变轨道的 0..total 数值域或外观.
     */
    fun setStartMin(@IntRange(from = -1) startMin: Int) {
        val newStartMin = startMin.coerceIn(-1, total)
        if (this.startMin == newStartMin) {
            return
        }
        val selection = nearestSelection(startMin = newStartMin)
        this.startMin = newStartMin
        applySelection(selection, constraintsChanged = true)
    }

    /** 配置的起点下限, 未配置时为 [THUMB_UNLIMITED]; 实际首个合法值还需满足步长. */
    fun getStartMin(): Int = startMin

    /** 设置起点上限, [THUMB_UNLIMITED] 表示不限制, 并调整到最近合法范围. */
    fun setStartMax(@IntRange(from = -1) startMax: Int) {
        val newStartMax = startMax.coerceIn(-1, total)
        if (this.startMax == newStartMax) {
            return
        }
        val selection = nearestSelection(startMax = newStartMax)
        this.startMax = newStartMax
        applySelection(selection, constraintsChanged = true)
    }

    /** 设置终点下限, [THUMB_UNLIMITED] 表示不限制. 无合法刻度时拒绝设置并保留原状态. */
    fun setEndMin(@IntRange(from = -1) endMin: Int) {
        val newEndMin = endMin.coerceIn(-1, total)
        if (this.endMin == newEndMin) {
            return
        }
        val selection = nearestSelection(endMin = newEndMin)
        this.endMin = newEndMin
        applySelection(selection, constraintsChanged = true)
    }

    /** 设置两端最小数值差, 并调整到最近合法范围. 无解时拒绝设置并保留原状态. */
    fun setMinSpan(@IntRange(from = 0) minSpan: Int) {
        val newMinSpan = minSpan.coerceAtLeast(0)
        if (this.minSpan == newMinSpan) {
            return
        }
        val selection = nearestSelection(minSpan = newMinSpan)
        this.minSpan = newMinSpan
        applySelection(selection, constraintsChanged = true)
    }

    /** 合法选区实际变化后的通知, 所有赋值与交互入口使用同一语义. */
    fun interface OnRangeChangeListener {
        fun onRangeChanged(start: Int, end: Int)
    }

    /** 触摸、键盘或无障碍交互的生命周期. 取消保留实时选区; 程序赋值不产生交互事件. */
    interface OnInteractionListener {
        fun onInteractionStart(start: Int, end: Int) {}
        fun onInteractionEnd(start: Int, end: Int) {}
        fun onInteractionCancel(start: Int, end: Int) {}
    }

    private fun nearestSelection(
        start: Int = selectedStart,
        end: Int = selectedEnd,
        total: Int = this.total,
        startMax: Int = this.startMax,
        endMin: Int = this.endMin,
        minSpan: Int = this.minSpan,
        startMin: Int = this.startMin,
    ): KitRangeSelection = nearestKitRangeSelection(start, end, total, step, startMax, endMin, minSpan, startMin)

    private fun applySelection(selection: KitRangeSelection, constraintsChanged: Boolean = false) {
        val oldStart = selectedStart
        val oldEnd = selectedEnd
        val changed = oldStart != selection.start || oldEnd != selection.end
        // 同值绑定回写保留触摸锚点的像素余量; 实际约束变化仍需更新映射和节点范围.
        if (!changed && !constraintsChanged) {
            return
        }
        selectedStart = selection.start
        selectedEnd = selection.end
        rebasePointers()
        invalidate()
        if (!changed) {
            accessibilityHelper.invalidateNodes()
        }
        publishRangeChange(oldStart, oldEnd)
    }

    private fun startLowerBound(): Long = startMin.coerceAtLeast(0).toLong()

    private fun startUpperBound(): Long =
        min(if (startMax == THUMB_UNLIMITED) total.toLong() else min(startMax, total).toLong(), selectedEnd.toLong() - minSpan)

    private fun endLowerBound(): Long =
        max(if (endMin == THUMB_UNLIMITED) 0L else endMin.toLong(), selectedStart.toLong() + minSpan)

    private fun nearestAllowedValue(value: Int, lower: Long, upper: Long): Int {
        val firstStep = (lower + step - 1) / step
        val lastStep = upper / step
        val nonnegativeValue = value.toLong().coerceAtLeast(0L)
        val nearestStep = nonnegativeValue / step + if (nonnegativeValue % step * 2L > step) 1L else 0L
        return (nearestStep.coerceIn(firstStep, lastStep) * step).toInt()
    }

    private fun isThumbEnabled(target: Int): Boolean =
        isEnabled && if (target == START) startThumbEnabled else endThumbEnabled

    private fun adjustedThumbValue(target: Int, increase: Boolean): Int {
        val current = if (target == START) selectedStart else selectedEnd
        val requested = (current.toLong() + if (increase) step.toLong() else -step.toLong())
            .coerceIn(0L, total.toLong()).toInt()
        return if (target == START) {
            nearestAllowedValue(requested, startLowerBound(), startUpperBound())
        } else {
            nearestAllowedValue(requested, endLowerBound(), total.toLong())
        }
    }

    private fun adjustThumb(target: Int, increase: Boolean) {
        if (!isThumbEnabled(target)) {
            return
        }
        val value = adjustedThumbValue(target, increase)
        if (target == START) {
            start = value
        } else {
            end = value
        }
    }

    private fun adjustFromAccessibility(target: Int, increase: Boolean): Boolean {
        if (target != START && target != END || !isThumbEnabled(target) || interaction != null || !isShown) {
            return false
        }
        val current = if (target == START) selectedStart else selectedEnd
        if (adjustedThumbValue(target, increase) == current) {
            return false
        }
        val started = beginInteraction(ACCESSIBILITY) ?: return false
        try {
            adjustThumb(target, increase)
        } finally {
            if (interaction === started) {
                finishInteraction(cancelled = false)
            }
        }
        return true
    }

    private fun accessibilityNode(target: Int): KitRangeAccessibilityNode {
        val thumb = if (target == START) startThumb else endThumb
        val value = if (target == START) selectedStart else selectedEnd
        val lower = if (target == START) startLowerBound() else endLowerBound()
        val upper = if (target == START) startUpperBound() else total.toLong()
        val minimum = ((lower + step - 1L) / step * step).toInt()
        val maximum = (upper / step * step).toInt()
        val centerX = barLeft + valueOffset(value)
        val halfWidth = max(thumb.width, max(barSize, 1)) / 2
        val halfHeight = max(thumb.height, max(barSize, 1)) / 2
        val left = (centerX - halfWidth).coerceIn(0, (width - 1).coerceAtLeast(0))
        val top = (barCenterY - halfHeight).coerceIn(0, (height - 1).coerceAtLeast(0))
        val bounds = Rect(
            left, top, (centerX.toLong() + halfWidth + 1L).coerceIn(left + 1L, max(width, left + 1).toLong()).toInt(),
            (barCenterY.toLong() + halfHeight + 1L).coerceIn(top + 1L, max(height, top + 1).toLong()).toInt(),
        )
        val label = if (target == START) {
            startThumbLabel ?: context.getText(R.string.kit_range_start_label)
        } else {
            endThumbLabel ?: context.getText(R.string.kit_range_end_label)
        }
        return KitRangeAccessibilityNode(label, value, minimum, maximum, bounds, isThumbEnabled(target))
    }

    private fun TypedArray.readThumb(index: Int): Drawable? {
        if (!hasValueOrEmpty(index)) {
            return INHERIT_THUMB
        }
        val value = peekValue(index) ?: return null
        if (value.type == TypedValue.TYPE_INT_DEC || value.type == TypedValue.TYPE_INT_HEX) {
            require(value.data == -1 || value.data == 0) { "Thumb must be a drawable, inherit or none" }
            return if (value.data == -1) INHERIT_THUMB else null
        }
        return getDrawable(index)?.mutate()
    }

    private fun readThumbSize(value: TypedValue?): Int = when (value?.type) {
        null -> THUMB_SIZE_INNER
        TypedValue.TYPE_DIMENSION -> TypedValue.complexToDimensionPixelSize(value.data, resources.displayMetrics)
        else -> value.data.coerceAtLeast(THUMB_SIZE_INNER)
    }

    private fun contentHeight(): Int = max(barSize, max(startThumb.height, endThumb.height))

    private fun saturatedSize(first: Int, second: Int, third: Int): Int =
        (first.toLong() + second + third).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private fun updateGeometry(viewWidth: Int, viewHeight: Int) {
        val oldLeft = barLeft
        val oldLength = barLength
        val thumbSpace = max(startThumb.width, endThumb.width)
        val contentWidth = (viewWidth.toLong() - paddingLeft - paddingRight).coerceAtLeast(0)
        barLength = (contentWidth - thumbSpace).coerceAtLeast(0).toInt()
        barLeft = paddingLeft + min(contentWidth / 2, thumbSpace.toLong() / 2).toInt()
        val contentHeight = contentHeight()
        val verticalStart = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> paddingTop
            Gravity.BOTTOM -> viewHeight - paddingBottom - contentHeight
            else -> paddingTop + (viewHeight - paddingTop - paddingBottom - contentHeight) / 2
        }
        barCenterY = verticalStart + contentHeight / 2
        if (oldLeft != barLeft || oldLength != barLength) {
            rebasePointers()
        }
        if (initialized) {
            accessibilityHelper.invalidateRoot()
        }
    }

    private fun valueOffset(value: Int): Int {
        val offset = ((value.toLong() * barLength + total / 2) / total).toInt()
        return if (isValueRtl) barLength - offset else offset
    }

    private fun drawThumb(canvas: Canvas, thumb: Thumb, value: Int) {
        val centerX = barLeft + valueOffset(value)
        thumb.drawable?.let {
            val left = centerX - thumb.width / 2
            val top = barCenterY - thumb.height / 2
            drawDrawable(canvas, it, thumb.state, left, top, left + thumb.width, top + thumb.height)
        }
    }

    private fun drawDrawable(canvas: Canvas, drawable: Drawable, state: IntArray, left: Int, top: Int, right: Int, bottom: Int) {
        val previousBounds = if (isSharedDrawable(drawable)) Rect(drawable.bounds) else null
        withDrawableState(drawable, state) {
            try {
                mutateDrawable { drawable.setBounds(left, top, right, bottom) }
                drawable.draw(canvas)
            } finally {
                if (previousBounds != null) {
                    mutateDrawable { drawable.bounds = previousBounds }
                }
            }
        }
    }

    private fun isSharedDrawable(drawable: Drawable): Boolean =
        listOf(barBackground, barForeground, startThumb.drawable, endThumb.drawable).count { it === drawable } > 1

    private inline fun <T> withDrawableState(drawable: Drawable, state: IntArray, action: () -> T): T {
        if (!isSharedDrawable(drawable)) {
            return action()
        }
        val previousState = drawable.state
        return try {
            mutateDrawable {
                drawable.state = state
                drawable.jumpToCurrentState()
            }
            action()
        } finally {
            mutateDrawable {
                drawable.state = previousState
                drawable.jumpToCurrentState()
            }
        }
    }

    private inline fun <T> mutateDrawable(action: () -> T): T {
        // 抑制临时状态/边界切换的同步刷新, 实际 draw 和外部修改仍可请求重绘.
        drawableMutationDepth++
        return try {
            action()
        } finally {
            drawableMutationDepth--
        }
    }

    private fun updateThumbSizes(): Boolean {
        var changed = false
        for (thumb in listOf(startThumb, endThumb)) {
            var width = thumbWidth.coerceAtLeast(0)
            var height = thumbHeight.coerceAtLeast(0)
            thumb.drawable?.let { drawable ->
                withDrawableState(drawable, thumb.state) {
                    if (thumbWidth < 0) {
                        width = drawable.intrinsicWidth.coerceAtLeast(0)
                    }
                    if (thumbHeight < 0) {
                        height = drawable.intrinsicHeight.coerceAtLeast(0)
                    }
                }
            }
            if (width != thumb.width || height != thumb.height) {
                changed = true
            }
            thumb.width = width
            thumb.height = height
        }
        return changed
    }

    private fun replaceThumbs(common: Drawable?, start: Drawable?, end: Drawable?) {
        require(common !== INHERIT_THUMB) { "INHERIT_THUMB is only valid for individual thumb overrides" }
        if (commonThumb === common && startThumbOverride === start && endThumbOverride === end) {
            return
        }
        val startSource = if (start === INHERIT_THUMB) common else start
        val endSource = if (end === INHERIT_THUMB) common else end
        val previousStart = startThumb.drawable
        val previousEnd = endThumb.drawable
        commonThumb = common
        startThumbOverride = start
        endThumbOverride = end
        startThumb.drawable = startSource
        endThumb.drawable = endSource
        releaseDrawable(previousStart)
        releaseDrawable(previousEnd)
        if (initialized) {
            ownedDrawables().forEach(::attachDrawable)
            updateDrawableStates()
            requestLayout()
        }
    }

    private fun ownedDrawables(): List<Drawable> = buildList {
        for (drawable in listOfNotNull(barBackground, barForeground, startThumb.drawable, endThumb.drawable)) {
            if (none { it === drawable }) {
                add(drawable)
            }
        }
    }

    private fun attachDrawable(drawable: Drawable) {
        drawable.callback = this
        drawable.layoutDirection = layoutDirection
        drawable.setVisible(drawablesVisible && isAttachedToWindow, false)
        if (isSharedDrawable(drawable)) {
            mutateDrawable { drawable.jumpToCurrentState() }
            cancelDrawableTasks(drawable)
        }
    }

    private fun releaseDrawable(drawable: Drawable?) {
        if (drawable == null || ownedDrawables().any { it === drawable }) {
            return
        }
        drawable.setVisible(false, false)
        cancelDrawableTasks(drawable)
        drawable.callback = null
    }

    private fun cancelDrawableTasks(drawable: Drawable) {
        deferredDrawableTasks.removeAll { it.drawable === drawable }
        unscheduleDrawable(drawable)
    }

    private fun updateDrawableStates() {
        barBackground?.let { if (!isSharedDrawable(it)) it.state = drawableState }
        barForeground?.let { if (!isSharedDrawable(it)) it.state = drawableState }
        updateThumbState(startThumb, startThumbEnabled)
        updateThumbState(endThumb, endThumbEnabled)
        if (updateThumbSizes()) {
            requestLayout()
        }
        invalidate()
    }

    private fun updateThumbState(thumb: Thumb, enabled: Boolean) {
        val states = drawableState.filter {
            it != android.R.attr.state_enabled && it != android.R.attr.state_pressed &&
                it != android.R.attr.state_drag_hovered && it != android.R.attr.state_focused
        }.toMutableList()
        val target = if (thumb === startThumb) START else END
        if (isFocused && accessibilityHelper.keyboardFocusedVirtualViewId == target) {
            states.add(android.R.attr.state_focused)
        }
        if (isEnabled && enabled) {
            states.add(android.R.attr.state_enabled)
            if (thumb.pointerId != NO_POINTER) {
                states.add(android.R.attr.state_drag_hovered)
                states.add(android.R.attr.state_pressed)
            }
        }
        thumb.state = states.toIntArray()
        thumb.drawable?.let { if (!isSharedDrawable(it)) it.state = thumb.state }
    }

    private fun addPointer(event: MotionEvent, index: Int): Boolean {
        val x = event.getX(index)
        val y = event.getY(index)
        if (!x.isFinite() || !y.isFinite()) {
            return false
        }
        var candidates = 0
        if (startThumbEnabled && hitThumb(startThumb, selectedStart, x.roundToInt(), y.roundToInt())) {
            candidates = candidates or START
        }
        if (endThumbEnabled && hitThumb(endThumb, selectedEnd, x.roundToInt(), y.roundToInt())) {
            candidates = candidates or END
        }
        if (candidates == 0) {
            return false
        }
        val id = event.getPointerId(index)
        val pointer = Pointer(x.roundToInt(), y.roundToInt(), candidates)
        pointers.put(id, pointer)
        if (gestureActive) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val available = availableCandidates(pointer)
        pointer.waitingForOccupiedThumb = available == 0
        if (available == START) {
            capture(startThumb, id, pointer, selectedStart)
        }
        if (available == END) {
            capture(endThumb, id, pointer, selectedEnd)
        }
        updateDrawableStates()
        return true
    }

    private fun hitThumb(thumb: Thumb, value: Int, x: Int, y: Int): Boolean {
        if (thumb.width <= 0 || thumb.height <= 0) {
            return false
        }
        val left = barLeft + valueOffset(value) - thumb.width / 2
        val top = barCenterY - thumb.height / 2
        return x.toLong() in left.toLong()..(left.toLong() + thumb.width) &&
            y.toLong() in top.toLong()..(top.toLong() + thumb.height)
    }

    private fun availableCandidates(pointer: Pointer): Int {
        var available = pointer.candidates
        if (startThumb.pointerId != NO_POINTER || !startThumbEnabled) {
            available = available and START.inv()
        }
        if (endThumb.pointerId != NO_POINTER || !endThumbEnabled) {
            available = available and END.inv()
        }
        return available
    }

    private fun capture(thumb: Thumb, id: Int, pointer: Pointer, value: Int, originX: Int = pointer.x) {
        thumb.pointerId = id
        thumb.anchorX = originX
        thumb.anchorValue = value
        thumb.lastX = originX
        pointer.assigned = true
    }

    private fun updatePointerPositions(event: MotionEvent) {
        for (index in 0 until event.pointerCount) {
            val pointer = pointers[event.getPointerId(index)] ?: continue
            val x = event.getX(index)
            val y = event.getY(index)
            if (x.isFinite() && y.isFinite()) {
                pointer.x = x.roundToInt()
                pointer.y = y.roundToInt()
            }
        }
    }

    private fun updateTouchSelection(event: MotionEvent) {
        val oldStart = selectedStart
        val oldEnd = selectedEnd
        updatePointerPositions(event)
        resolvePendingPointers()
        // MOVE 和正常抬起使用同一提交路径, 数值同向增大时先移动上端点释放空间.
        val endDelta = (pointers[endThumb.pointerId]?.x ?: endThumb.lastX).toLong() - endThumb.lastX
        if (if (isValueRtl) endDelta < 0L else endDelta > 0L) {
            moveThumb(endThumb, END)
            moveThumb(startThumb, START)
        } else {
            moveThumb(startThumb, START)
            moveThumb(endThumb, END)
        }
        publishRangeChange(oldStart, oldEnd)
    }

    private fun resolvePendingPointers() {
        var changed = false
        for (index in 0 until pointers.size) {
            val id = pointers.keyAt(index)
            val pointer = pointers.valueAt(index)
            if (pointer.assigned) {
                continue
            }
            var available = availableCandidates(pointer)
            if (available == 0) {
                pointer.waitingForOccupiedThumb = true
                continue
            }
            if (available == (START or END)) {
                available = when {
                    pointer.x < pointer.downX -> if (isValueRtl) END else START
                    pointer.x > pointer.downX -> if (isValueRtl) START else END
                    else -> 0
                }
            }
            // 重合仲裁保留按下原点; 已被其它手指占用的端点在接管时重新建立锚点.
            val origin = if (pointer.waitingForOccupiedThumb) {
                pointer.x
            } else {
                pointer.downX
            }
            when (available) {
                START -> capture(startThumb, id, pointer, selectedStart, origin)
                END -> capture(endThumb, id, pointer, selectedEnd, origin)
                else -> continue
            }
            changed = true
        }
        if (changed) {
            updateDrawableStates()
        }
    }

    private fun moveThumb(thumb: Thumb, target: Int) {
        val pointer = pointers[thumb.pointerId] ?: return
        if (barLength <= 0 || pointer.x == thumb.lastX) {
            return
        }
        thumb.lastX = pointer.x
        val lower = if (target == START) startLowerBound() else endLowerBound()
        val upper = if (target == END) total.toLong() else startUpperBound()
        val firstStep = (lower + step - 1) / step
        val lastStep = upper / step
        if (upper < lower || firstStep > lastStep) {
            return
        }

        // 像素坐标仅在入口取整; 数值映射和步长舍入全程使用 Long, 避免大整数经 Float 丢失精度.
        val physicalDelta = (pointer.x.toLong() - thumb.anchorX).coerceIn(-barLength.toLong(), barLength.toLong())
        val delta = if (isValueRtl) -physicalDelta else physicalDelta
        val numerator = (thumb.anchorValue.toLong() * barLength + delta * total).coerceIn(0L, total.toLong() * barLength)
        val denominator = barLength.toLong() * step
        val remainder = numerator % denominator
        val nearestStep = numerator / denominator + if (remainder > denominator - remainder) 1 else 0
        val value = (nearestStep.coerceIn(firstStep, lastStep) * step).toInt()
        if (target == START) {
            selectedStart = value
        } else {
            selectedEnd = value
        }
    }

    private fun releasePointer(id: Int) {
        pointers.remove(id)
        if (startThumb.pointerId == id) {
            startThumb.pointerId = NO_POINTER
        }
        if (endThumb.pointerId == id) {
            endThumb.pointerId = NO_POINTER
        }
        updateDrawableStates()
    }

    private fun clearPointers() {
        gestureActive = false
        pointers.clear()
        startThumb.pointerId = NO_POINTER
        endThumb.pointerId = NO_POINTER
        parent?.requestDisallowInterceptTouchEvent(false)
        if (initialized) {
            updateDrawableStates()
        }
    }

    private fun rebasePointers() {
        for (thumb in listOf(startThumb, endThumb)) {
            val pointer = pointers[thumb.pointerId] ?: continue
            thumb.anchorX = pointer.x
            thumb.lastX = pointer.x
            thumb.anchorValue = if (thumb === startThumb) selectedStart else selectedEnd
        }
    }

    private fun publishRangeChange(oldStart: Int, oldEnd: Int) {
        if (oldStart != selectedStart || oldEnd != selectedEnd) {
            invalidate()
            accessibilityHelper.invalidateNodes()
            onRangeChangedListener?.onRangeChanged(selectedStart, selectedEnd)
        }
    }

    private fun beginInteraction(kind: Int, keyCode: Int = KeyEvent.KEYCODE_UNKNOWN): Interaction? {
        if (interaction != null || !isEnabled || visibility != VISIBLE) {
            return null
        }
        val started = Interaction(kind, onInteractionListener, keyCode)
        interaction = started
        started.listener?.onInteractionStart(selectedStart, selectedEnd)
        return started.takeIf { interaction === it }
    }

    private fun finishInteraction(cancelled: Boolean) {
        val finished = interaction
        interaction = null
        clearPointers()
        if (cancelled) {
            finished?.listener?.onInteractionCancel(selectedStart, selectedEnd)
        } else {
            finished?.listener?.onInteractionEnd(selectedStart, selectedEnd)
        }
    }

    /** 一次完整交互持有开始时的监听器, 以保持开始和结束配对. */
    private class Interaction(val kind: Int, val listener: OnInteractionListener?, val keyCode: Int)

    /** 尚未连接窗口的 Drawable 任务, 延迟保持 View 队列的连接后计时语义. */
    private class DeferredDrawableTask(val drawable: Drawable, val action: Runnable, val delay: Long)

    /** 原子数值快照. internal 保证 JVM 类可见, 供 Android Parcel 反射读取 CREATOR. */
    internal class SavedState : BaseSavedState {
        val start: Int
        val end: Int
        val total: Int
        val step: Int
        val startMax: Int
        val endMin: Int
        val minSpan: Int
        val startMin: Int

        constructor(
            superState: Parcelable?,
            start: Int,
            end: Int,
            total: Int,
            step: Int,
            startMax: Int,
            endMin: Int,
            minSpan: Int,
            startMin: Int,
        ) : super(superState) {
            this.start = start
            this.end = end
            this.total = total
            this.step = step
            this.startMax = startMax
            this.endMin = endMin
            this.minSpan = minSpan
            this.startMin = startMin
        }

        constructor(parcel: Parcel) : super(parcel) {
            start = parcel.readInt()
            end = parcel.readInt()
            total = parcel.readInt()
            step = parcel.readInt()
            startMax = parcel.readInt()
            endMin = parcel.readInt()
            minSpan = parcel.readInt()
            startMin = parcel.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(start)
            out.writeInt(end)
            out.writeInt(total)
            out.writeInt(step)
            out.writeInt(startMax)
            out.writeInt(endMin)
            out.writeInt(minSpan)
            out.writeInt(startMin)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    /** 单端的绘制实例与当前拖动锚点. */
    private class Thumb {
        var drawable: Drawable? = null
        var state: IntArray = intArrayOf()
        var width: Int = 0
        var height: Int = 0
        var pointerId: Int = NO_POINTER
        var anchorX: Int = 0
        var anchorValue: Int = 0
        var lastX: Int = 0
    }

    /** 按下时的候选端点保持不变, 重合时等待移动方向, 已占用时等待接管. */
    private class Pointer(val downX: Int, var y: Int, val candidates: Int) {
        var x: Int = downX
        var assigned: Boolean = false
        var waitingForOccupiedThumb: Boolean = false
    }
}
