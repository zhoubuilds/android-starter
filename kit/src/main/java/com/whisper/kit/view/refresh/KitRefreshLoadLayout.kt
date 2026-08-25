package com.whisper.kit.view.refresh

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.annotation.LayoutRes
import androidx.annotation.Px
import androidx.core.content.withStyledAttributes
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import com.whisper.kit.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 下拉刷新和上拉加载容器.
 *
 * 该容器只处理纵向内容的拖拽冲突、头尾视图露出、状态切换和刷新加载回调,
 * 不持有业务列表数据, 不处理页码、接口请求或业务错误.
 * 普通 LinearLayout 等不滚动内容可通过手势直接拉出 header/footer.
 * 支持 NestedScrolling 的可滚动内容可在滚到边界后连续交给容器处理刷新或加载.
 * 可滚动但不支持 NestedScrolling 的内容可能无法提供连续边界衔接, 也可能无法触发滚动到底自动加载.
 *
 * @author whisper
 * @since 2026/07/30
 */
class KitRefreshLoadLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.kitRefreshLoadLayoutStyle,
) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingParent3 {

    /**
     * 下拉刷新是否可用.
     *
     * 该属性的有效值依赖 header 是否存在. 如果没有 header, 设置为 true 时会保持禁用并输出警告日志.
     * 设置为 false 时, 如果当前正在下拉、释放刷新或刷新中, 会立即收起 header 并回到空闲状态.
     */
    var refreshEnabled: Boolean
        get() = refreshEnabledValue
        set(value) {
            setRefreshEnabled(value, warnIfMissing = true)
        }

    /**
     * 上拉加载是否可用.
     *
     * 该属性的有效值依赖 footer 是否存在. 如果没有 footer, 设置为 true 时会保持禁用并输出警告日志.
     * 设置为 false 时, 如果当前正在上拉、释放加载或加载中, 会立即收起 footer 并回到空闲状态.
     */
    var loadMoreEnabled: Boolean
        get() = loadMoreEnabledValue
        set(value) {
            setLoadMoreEnabled(value, warnIfMissing = true)
        }

    /**
     * 滚动到底部时是否自动触发加载更多.
     *
     * 该属性只负责触发和手动上拉相同的 [RefreshLoadAction.LoadMore] 回调,
     * 具体加载请求、分页和没有更多数据仍由调用方维护.
     */
    var autoLoadMoreEnabled: Boolean = false

    /**
     * 刷新触发距离, 单位 px.
     *
     * 小于 0 时使用 header 高度作为触发距离.
     */
    @Px
    var refreshTriggerDistance: Int = DEFAULT_TRIGGER_DISTANCE

    /**
     * 加载更多触发距离, 单位 px.
     *
     * 小于 0 时使用 footer 高度作为触发距离.
     */
    @Px
    var loadMoreTriggerDistance: Int = DEFAULT_TRIGGER_DISTANCE

    /**
     * 最大拖拽显示距离, 单位 px. 小于等于 0 时使用触发距离的两倍.
     */
    @Px
    var maxDragDistance: Int = 0

    /**
     * 基础拖拽阻尼. 值越大, 内容跟随手指移动越慢.
     *
     * 实际拖拽阻尼会随已拉出距离增加, 越接近最大拖拽距离阻力越大.
     */
    var dragResistance: Float = DEFAULT_DRAG_RESISTANCE
        set(value) {
            field = max(MIN_DRAG_RESISTANCE, value)
        }

    /**
     * 回弹和收起动画时长, 单位毫秒.
     *
     * 该时长用于释放后从拖拽距离回弹到 header/footer 高度, 也用于刷新或加载完成后收起 header/footer.
     */
    var animationDurationMillis: Long = DEFAULT_ANIMATION_DURATION_MILLIS

    /**
     * 当前刷新加载状态.
     */
    var state: RefreshLoadState = RefreshLoadState.Idle
        private set

    /**
     * 当前内容位移, 单位 px. 正数表示下拉, 负数表示上拉.
     */
    @get:Px
    val currentOffset: Int
        get() = contentOffset

    /**
     * 判定触摸开始拖拽的最小距离, 用于过滤点击和轻微抖动.
     */
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * header/footer 回弹和完成收起动画使用的减速插值器.
     */
    private val settleInterpolator: DecelerateInterpolator = DecelerateInterpolator(SETTLE_DECELERATE_FACTOR)

    /**
     * 内容惯性滚到边界后, 用于继续拉出正在刷新或加载中的指示器.
     */
    private val indicatorScroller: OverScroller = OverScroller(context)

    /**
     * 刷新或加载触发监听.
     */
    private var refreshLoadListener: OnRefreshLoadListener? = null

    /**
     * 内容是否还能继续向顶部滚动的外部判定.
     */
    private var childScrollUpCallback: OnChildScrollUpCallback? = null

    /**
     * 当前正在执行的位移动画, 开始新拖拽或新动画前需要取消.
     */
    private var activeAnimator: ValueAnimator? = null

    /**
     * 指示器惯性滚动方向. 为 null 时表示没有正在接管的指示器 fling.
     */
    private var indicatorFlingMode: IndicatorFlingMode? = null

    /**
     * 等待内容滚到边界后继续作用到 header/footer 的 fling 速度.
     */
    private var pendingIndicatorFlingVelocityY: Float = 0f

    /**
     * 当前内容位移, 正数表示 header 方向, 负数表示 footer 方向.
     */
    private var contentOffset: Int = 0

    /**
     * 上一次触摸事件的 y 坐标, 用于计算本次拖拽增量.
     */
    private var lastMotionY: Float = 0f

    /**
     * 本轮触摸按下时的 y 坐标, 用于判断是否超过拖拽阈值.
     */
    private var initialMotionY: Float = 0f

    /**
     * 当前是否已由容器接管手势拖拽.
     */
    private var dragging: Boolean = false

    /**
     * 当前跟踪的触摸指针 id.
     */
    private var activePointerId: Int = INVALID_POINTER_ID

    /**
     * 下拉刷新开关的实际保存值. 对外 setter 会结合 header 是否存在做保护.
     */
    private var refreshEnabledValue: Boolean = true

    /**
     * 调用方期望的下拉刷新开关. header 临时缺失导致禁用时, 该值仍保留恢复意图.
     */
    private var refreshEnabledRequested: Boolean = true

    /**
     * 上拉加载开关的实际保存值. 对外 setter 会结合 footer 是否存在做保护.
     */
    private var loadMoreEnabledValue: Boolean = true

    /**
     * 调用方期望的上拉加载开关. footer 临时缺失导致禁用时, 该值仍保留恢复意图.
     */
    private var loadMoreEnabledRequested: Boolean = true

    /**
     * 子 View 角色是否已完成解析. 运行时移除 child 后会置回 false, 下次测量或布局前重新解析.
     */
    private var childrenResolved: Boolean = false

    @LayoutRes
    private var defaultHeaderLayoutResId: Int = 0

    @LayoutRes
    private var defaultFooterLayoutResId: Int = 0

    private val refreshHeaderView: View?
        get() = findChildByRole(ChildRole.Header)

    private val loadFooterView: View?
        get() = findChildByRole(ChildRole.Footer)

    private val contentView: View?
        get() = findChildByRole(ChildRole.Content)

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.KitRefreshLoadLayout,
            defStyleAttr,
            0,
        ).apply {
            defaultHeaderLayoutResId = getResourceId(
                R.styleable.KitRefreshLoadLayout_kitRefreshLoadHeaderLayout,
                0,
            )
            defaultFooterLayoutResId = getResourceId(
                R.styleable.KitRefreshLoadLayout_kitRefreshLoadFooterLayout,
                0,
            )
            refreshEnabled = getBoolean(
                R.styleable.KitRefreshLoadLayout_kitRefreshLoadRefreshEnabled,
                refreshEnabled,
            )
            loadMoreEnabled = getBoolean(
                R.styleable.KitRefreshLoadLayout_kitRefreshLoadLoadMoreEnabled,
                loadMoreEnabled,
            )
            autoLoadMoreEnabled = getBoolean(
                R.styleable.KitRefreshLoadLayout_kitRefreshLoadAutoLoadMoreEnabled,
                autoLoadMoreEnabled,
            )
            refreshTriggerDistance = getDimensionPixelSize(
                R.styleable.KitRefreshLoadLayout_kitRefreshLoadRefreshTriggerDistance,
                refreshTriggerDistance,
            )
            loadMoreTriggerDistance = getDimensionPixelSize(
                R.styleable.KitRefreshLoadLayout_kitRefreshLoadLoadMoreTriggerDistance,
                loadMoreTriggerDistance,
            )
            dragResistance = getFloat(
                R.styleable.KitRefreshLoadLayout_kitRefreshLoadDragResistance,
                dragResistance,
            )
            animationDurationMillis = getInt(
                R.styleable.KitRefreshLoadLayout_kitRefreshLoadAnimationDuration,
                animationDurationMillis.toInt(),
            ).toLong()
            recycle()
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        LayoutParams(context, attrs)

    override fun generateLayoutParams(params: ViewGroup.LayoutParams): LayoutParams =
        when (params) {
            is LayoutParams -> LayoutParams(params)
            is MarginLayoutParams -> LayoutParams(params)
            else -> LayoutParams(params)
        }

    override fun checkLayoutParams(params: ViewGroup.LayoutParams): Boolean =
        params is LayoutParams

    override fun onFinishInflate() {
        super.onFinishInflate()
        resolveDefaultChildrenIfMissing()
        childrenResolved = true
        updateEnabledByChildAvailability()
        bringIndicatorChildrenToFront()
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        validateChildRoles()
        bringIndicatorChildrenToFront()
        if (childrenResolved) {
            updateEnabledByChildAvailability()
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (childrenResolved) {
            childrenResolved = false
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        resolveChildAvailability()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        resolveChildAvailability()
        for (index: Int in 0 until childCount) {
            val child: View = getChildAt(index)
            if (child.isGone) continue

            val layoutParams: LayoutParams = child.layoutParams as LayoutParams
            when (layoutParams.role) {
                ChildRole.Header -> layoutHeader(child, layoutParams)
                ChildRole.Footer -> layoutFooter(child, layoutParams)
                ChildRole.Content -> layoutContent(child, layoutParams)
            }
            applyChildTranslation(child)
        }
    }

    override fun onDetachedFromWindow() {
        stopIndicatorFling()
        activeAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || isBusyState()) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                lastMotionY = ev.y
                initialMotionY = ev.y
                dragging = false
                activeAnimator?.cancel()
                stopIndicatorFling()
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex: Int = ev.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val y: Float = ev.getY(pointerIndex)
                val dy: Float = y - initialMotionY
                if (abs(dy) > touchSlop && canDragBy(dy)) {
                    dragging = true
                    lastMotionY = y
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(ev)

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP,
            -> {
                dragging = false
                activePointerId = INVALID_POINTER_ID
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    // 该容器不识别点击手势, 只处理刷新加载拖拽; 因此没有可调用 performClick() 的点击分支.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastMotionY = event.y
                initialMotionY = event.y
                activeAnimator?.cancel()
                stopIndicatorFling()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex: Int = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val y: Float = event.getY(pointerIndex)
                val dy: Float = y - lastMotionY
                if (!dragging && abs(y - initialMotionY) > touchSlop && canDragBy(y - initialMotionY)) {
                    dragging = true
                }
                if (dragging) {
                    moveOffsetBy(dy)
                    lastMotionY = y
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP,
            -> {
                if (dragging || contentOffset != 0) {
                    dragging = false
                    activePointerId = INVALID_POINTER_ID
                    settleByCurrentOffset()
                    return true
                }
                activePointerId = INVALID_POINTER_ID
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 该容器只处理刷新加载拖拽, 不额外产生点击行为.
     *
     * 保留该实现用于满足自定义 View 的无障碍点击入口要求.
     */
    override fun performClick(): Boolean =
        super.performClick()

    override fun onStartNestedScroll(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean =
        isEnabled &&
            (
                type == ViewCompat.TYPE_TOUCH ||
                    isBusyState() ||
                    (type == ViewCompat.TYPE_NON_TOUCH && autoLoadMoreEnabled)
                ) &&
            (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0

    override fun onNestedScrollAccepted(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ) = Unit

    override fun onNestedPreScroll(
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        if (isBusyState()) {
            consumeBusyIndicatorPreScroll(target, dy, consumed)
            return
        }
        if (type != ViewCompat.TYPE_TOUCH) return

        if (dy > 0 && contentOffset > 0) {
            val consumeY: Int = min(dy, contentOffset)
            setContentOffset(contentOffset - consumeY)
            consumed[1] += consumeY
        } else if (dy < 0 && contentOffset < 0) {
            val consumeY: Int = max(dy, contentOffset)
            setContentOffset(contentOffset - consumeY)
            consumed[1] += consumeY
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        if (isBusyState()) {
            followIndicatorWithContentScroll(
                target = target,
                dyUnconsumed = dyUnconsumed,
            )
            return
        }
        if (dispatchAutoLoadMoreIfNeeded(target, dyConsumed, dyUnconsumed)) return
        if (type != ViewCompat.TYPE_TOUCH) return

        if (dyUnconsumed < 0 && canStartRefreshDrag(target)) {
            moveOffsetBy(-dyUnconsumed.toFloat())
            consumed[1] += dyUnconsumed
        } else if (dyUnconsumed > 0 && canStartLoadMoreDrag(target)) {
            moveOffsetBy(-dyUnconsumed.toFloat())
            consumed[1] += dyUnconsumed
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
    ) {
        val consumed: IntArray = IntArray(2)
        onNestedScroll(
            target = target,
            dxConsumed = dxConsumed,
            dyConsumed = dyConsumed,
            dxUnconsumed = dxUnconsumed,
            dyUnconsumed = dyUnconsumed,
            type = type,
            consumed = consumed,
        )
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            pendingIndicatorFlingVelocityY = 0f
            return
        }
        if (type != ViewCompat.TYPE_TOUCH) return

        settleByCurrentOffset()
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        pendingIndicatorFlingVelocityY = when {
            state == RefreshLoadState.Refreshing &&
                velocityY < 0f &&
                contentOffset < resolveRefreshHoldDistance() -> velocityY

            state == RefreshLoadState.LoadingMore &&
                velocityY > 0f &&
                contentOffset > -resolveLoadMoreHoldDistance() -> velocityY

            else -> 0f
        }
        return contentOffset != 0 && !isBusyState()
    }

    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean = false

    override fun computeScroll() {
        super.computeScroll()
        continueIndicatorFling()
    }

    /**
     * 设置刷新加载触发监听器.
     *
     * @param listener 用户触发刷新或加载更多时的回调.
     */
    fun setOnRefreshLoadListener(listener: OnRefreshLoadListener?) {
        refreshLoadListener = listener
    }

    /**
     * 设置内容是否还能继续向顶部滚动的外部判定.
     *
     * 嵌套滚动层级复杂时, 直接 content child 的 [View.canScrollVertically] 可能无法代表真实列表边界.
     *
     * @param callback 内容向上滚动能力判定, 传入 null 时恢复默认 View 判定.
     */
    fun setOnChildScrollUpCallback(callback: OnChildScrollUpCallback?) {
        childScrollUpCallback = callback
    }

    /**
     * 结束刷新并收起 header.
     *
     * @param animate 是否使用动画切换位置.
     */
    fun finishRefresh(animate: Boolean = true) {
        resetRefreshState(animate)
    }

    /**
     * 结束加载更多并收起 footer.
     *
     * @param animate 是否使用动画切换位置.
     */
    fun finishLoadMore(animate: Boolean = true) {
        resetLoadMoreState(animate)
    }

    private fun resetRefreshState(animate: Boolean = true) {
        if (!isHeaderState()) return

        changeState(RefreshLoadState.Settling)
        animateToOffset(0, animate)
    }

    private fun resetLoadMoreState(animate: Boolean = true) {
        if (!isFooterState()) return

        changeState(RefreshLoadState.Settling)
        animateToOffset(0, animate)
    }

    private fun setRefreshEnabled(value: Boolean, warnIfMissing: Boolean) {
        refreshEnabledRequested = value
        updateRefreshEnabledByChildAvailability(warnIfMissing)
    }

    private fun setLoadMoreEnabled(value: Boolean, warnIfMissing: Boolean) {
        loadMoreEnabledRequested = value
        updateLoadMoreEnabledByChildAvailability(warnIfMissing)
    }

    private fun updateRefreshEnabledByChildAvailability(warnIfMissing: Boolean) {
        val newValue: Boolean = refreshEnabledRequested && hasRefreshHeader(warnIfMissing)
        if (refreshEnabled == newValue) return

        fieldSetRefreshEnabled(newValue)
    }

    private fun updateLoadMoreEnabledByChildAvailability(warnIfMissing: Boolean) {
        val newValue: Boolean = loadMoreEnabledRequested && hasLoadFooter(warnIfMissing)
        if (loadMoreEnabled == newValue) return

        fieldSetLoadMoreEnabled(newValue)
    }

    private fun fieldSetRefreshEnabled(value: Boolean) {
        refreshEnabledValue = value
        if (!value && isHeaderState()) {
            resetRefreshState()
        }
    }

    private fun fieldSetLoadMoreEnabled(value: Boolean) {
        loadMoreEnabledValue = value
        if (!value && isFooterState()) {
            resetLoadMoreState()
        }
    }

    private fun updateEnabledByChildAvailability() {
        updateRefreshEnabledByChildAvailability(warnIfMissing = refreshEnabled)
        updateLoadMoreEnabledByChildAvailability(warnIfMissing = loadMoreEnabled)
    }

    private fun resolveChildAvailability() {
        resolveDefaultChildrenIfMissing()
        childrenResolved = true
        updateEnabledByChildAvailability()
    }

    private fun hasRefreshHeader(warnIfMissing: Boolean): Boolean {
        if (!childrenResolved || refreshHeaderView != null) return true

        if (warnIfMissing) {
            Log.w(TAG, "Refresh is disabled because KitRefreshLoadLayout has no header child.")
        }
        return false
    }

    private fun hasLoadFooter(warnIfMissing: Boolean): Boolean {
        if (!childrenResolved || loadFooterView != null) return true

        if (warnIfMissing) {
            Log.w(TAG, "Load more is disabled because KitRefreshLoadLayout has no footer child.")
        }
        return false
    }

    private fun handlePointerUp(event: MotionEvent) {
        val pointerIndex: Int = event.actionIndex
        val pointerId: Int = event.getPointerId(pointerIndex)
        if (pointerId != activePointerId) return

        val newPointerIndex: Int = if (pointerIndex == 0) 1 else 0
        if (newPointerIndex >= event.pointerCount) {
            activePointerId = INVALID_POINTER_ID
            return
        }

        activePointerId = event.getPointerId(newPointerIndex)
        lastMotionY = event.getY(newPointerIndex)
        initialMotionY = lastMotionY
    }

    private fun layoutHeader(child: View, layoutParams: LayoutParams) {
        val childLeft: Int = paddingLeft + layoutParams.leftMargin
        val childTop: Int = paddingTop + layoutParams.topMargin - child.measuredHeight
        child.layout(
            childLeft,
            childTop,
            childLeft + child.measuredWidth,
            childTop + child.measuredHeight,
        )
    }

    private fun layoutFooter(child: View, layoutParams: LayoutParams) {
        val childLeft: Int = paddingLeft + layoutParams.leftMargin
        val childTop: Int = measuredHeight - paddingBottom - layoutParams.bottomMargin
        child.layout(
            childLeft,
            childTop,
            childLeft + child.measuredWidth,
            childTop + child.measuredHeight,
        )
    }

    private fun layoutContent(child: View, layoutParams: LayoutParams) {
        val childLeft: Int = paddingLeft + layoutParams.leftMargin
        val childTop: Int = paddingTop + layoutParams.topMargin
        child.layout(
            childLeft,
            childTop,
            childLeft + child.measuredWidth,
            childTop + child.measuredHeight,
        )
    }

    private fun addDefaultChildIfMissing(
        @LayoutRes layoutResId: Int,
        role: ChildRole,
    ) {
        if (layoutResId == 0 || findChildByRole(role) != null) return

        val child: View = LayoutInflater.from(context).inflate(layoutResId, this, false)
        val layoutParams: LayoutParams = child.layoutParams?.let(::generateLayoutParams)
            ?: LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        layoutParams.role = role
        addView(child, layoutParams)
    }

    private fun resolveDefaultChildrenIfMissing() {
        // XML inflate、Kotlin 动态创建和运行时移除子 View 后都会走这里, 保持默认 header/footer 注入时机一致.
        addDefaultChildIfMissing(
            layoutResId = defaultHeaderLayoutResId,
            role = ChildRole.Header,
        )
        addDefaultChildIfMissing(
            layoutResId = defaultFooterLayoutResId,
            role = ChildRole.Footer,
        )
    }

    private fun findChildByRole(role: ChildRole): View? {
        for (index: Int in 0 until childCount) {
            val child: View = getChildAt(index)
            val params: LayoutParams = child.layoutParams as? LayoutParams ?: continue
            if (params.role == role) return child
        }
        return null
    }

    private fun validateChildRoles() {
        var headerCount: Int = 0
        var contentCount: Int = 0
        var footerCount: Int = 0

        for (index: Int in 0 until childCount) {
            val child: View = getChildAt(index)
            val params: LayoutParams = child.layoutParams as? LayoutParams ?: continue
            when (params.role) {
                ChildRole.Header -> headerCount += 1
                ChildRole.Content -> contentCount += 1
                ChildRole.Footer -> footerCount += 1
            }
        }

        check(headerCount <= 1) {
            "KitRefreshLoadLayout can host at most one header child."
        }
        check(contentCount <= 1) {
            "KitRefreshLoadLayout can host at most one content child. Mark header or footer with kitLayoutRefreshLoadRole."
        }
        check(footerCount <= 1) {
            "KitRefreshLoadLayout can host at most one footer child."
        }
    }

    private fun canDragBy(dy: Float): Boolean =
        if (dy > 0) {
            canStartRefreshDrag(contentView)
        } else {
            canStartLoadMoreDrag(contentView)
        }

    private fun canStartRefreshDrag(target: View?): Boolean =
        refreshEnabled &&
            !isBusyState() &&
            state != RefreshLoadState.LoadingMore &&
            !canChildScrollUp(target)

    private fun canStartLoadMoreDrag(target: View?): Boolean =
        loadMoreEnabled &&
            !isBusyState() &&
            state != RefreshLoadState.Refreshing &&
            (target == null || !target.canScrollVertically(1))

    private fun canAutoLoadMore(target: View, dyConsumed: Int, dyUnconsumed: Int): Boolean =
        autoLoadMoreEnabled &&
            loadMoreEnabled &&
            !isBusyState() &&
            state == RefreshLoadState.Idle &&
            contentOffset == 0 &&
            (dyConsumed > 0 || dyUnconsumed > 0) &&
            !target.canScrollVertically(1)

    private fun canChildScrollUp(target: View?): Boolean {
        return childScrollUpCallback?.canChildScrollUp(this, target)
            ?: (target?.canScrollVertically(-1) == true)
    }

    private fun dispatchAutoLoadMoreIfNeeded(
        target: View,
        dyConsumed: Int,
        dyUnconsumed: Int,
    ): Boolean {
        if (!canAutoLoadMore(target, dyConsumed, dyUnconsumed)) return false

        beginLoadMore()
        return true
    }

    private fun isBusyState(): Boolean =
        state == RefreshLoadState.Refreshing || state == RefreshLoadState.LoadingMore

    private fun isHeaderState(): Boolean =
        state == RefreshLoadState.PullingDown ||
            state == RefreshLoadState.ReleaseToRefresh ||
            state == RefreshLoadState.Refreshing ||
            contentOffset > 0

    private fun isFooterState(): Boolean =
        state == RefreshLoadState.PullingUp ||
            state == RefreshLoadState.ReleaseToLoadMore ||
            state == RefreshLoadState.LoadingMore ||
            contentOffset < 0

    private fun moveOffsetBy(deltaY: Float) {
        if (deltaY == 0f) return

        val adjustedDelta: Int = (deltaY / resolveDragResistance(deltaY)).roundToInt()
        if (adjustedDelta == 0) return

        val newOffset: Int = when {
            adjustedDelta > 0 -> min(contentOffset + adjustedDelta, resolveMaxRefreshDistance())
            adjustedDelta < 0 -> max(contentOffset + adjustedDelta, -resolveMaxLoadMoreDistance())
            else -> contentOffset
        }
        setContentOffset(newOffset)
    }

    private fun resolveDragResistance(deltaY: Float): Float {
        val expanding: Boolean =
            contentOffset == 0 ||
                (deltaY > 0f && contentOffset > 0) ||
                (deltaY < 0f && contentOffset < 0)
        if (!expanding) return dragResistance

        val maxDistance: Int = if (deltaY > 0f) {
            resolveMaxRefreshDistance()
        } else {
            resolveMaxLoadMoreDistance()
        }
        val progress: Float = if (maxDistance <= 0) {
            0f
        } else {
            (abs(contentOffset).toFloat() / maxDistance).coerceIn(0f, 1f)
        }
        return dragResistance * (1f + progress * progress * EXTRA_DRAG_RESISTANCE_FACTOR)
    }

    private fun setContentOffset(offset: Int) {
        if (contentOffset == offset) return

        contentOffset = offset
        applyChildTranslations()
        updatePullState()
        notifyComponentsOffsetChanged()
    }

    private fun applyChildTranslations() {
        for (index: Int in 0 until childCount) {
            applyChildTranslation(getChildAt(index))
        }
    }

    private fun applyChildTranslation(child: View) {
        child.translationY = contentOffset.toFloat()
    }

    private fun followIndicatorWithContentScroll(
        target: View,
        dyUnconsumed: Int,
    ) {
        when (state) {
            RefreshLoadState.Refreshing -> followHeaderWithContentScroll(target, dyUnconsumed)
            RefreshLoadState.LoadingMore -> followFooterWithContentScroll(target, dyUnconsumed)
            else -> Unit
        }
    }

    private fun consumeBusyIndicatorPreScroll(target: View, dy: Int, consumed: IntArray) {
        when (state) {
            RefreshLoadState.Refreshing -> consumeRefreshingIndicatorPreScroll(target, dy, consumed)
            RefreshLoadState.LoadingMore -> consumeLoadingMoreIndicatorPreScroll(target, dy, consumed)
            else -> Unit
        }
    }

    /**
     * 刷新中 header 像列表前置条目一样参与滚动消费.
     */
    private fun consumeRefreshingIndicatorPreScroll(target: View, dy: Int, consumed: IntArray) {
        when {
            dy > 0 && contentOffset > 0 -> {
                val consumeY: Int = min(dy, contentOffset)
                cancelOffsetAnimations()
                setContentOffset(contentOffset - consumeY)
                consumed[1] += consumeY
            }

            dy < 0 &&
                contentOffset < resolveRefreshHoldDistance() &&
                !canChildScrollUp(target) -> {
                val consumeY: Int = max(dy, contentOffset - resolveRefreshHoldDistance())
                cancelOffsetAnimations()
                setContentOffset(contentOffset - consumeY)
                consumed[1] += consumeY
                startPendingIndicatorFlingIfNeeded(target)
            }
        }
    }

    /**
     * 加载中 footer 像列表后置条目一样参与滚动消费.
     */
    private fun consumeLoadingMoreIndicatorPreScroll(target: View, dy: Int, consumed: IntArray) {
        when {
            dy < 0 && contentOffset < 0 -> {
                val consumeY: Int = max(dy, contentOffset)
                cancelOffsetAnimations()
                setContentOffset(contentOffset - consumeY)
                consumed[1] += consumeY
            }

            dy > 0 &&
                contentOffset > -resolveLoadMoreHoldDistance() &&
                !target.canScrollVertically(1) -> {
                val consumeY: Int = min(dy, contentOffset + resolveLoadMoreHoldDistance())
                cancelOffsetAnimations()
                setContentOffset(contentOffset - consumeY)
                consumed[1] += consumeY
                startPendingIndicatorFlingIfNeeded(target)
            }
        }
    }

    private fun followHeaderWithContentScroll(
        target: View,
        dyUnconsumed: Int,
    ) {
        val deltaOffset: Int = when {
            dyUnconsumed < 0 && !canChildScrollUp(target) -> -dyUnconsumed
            else -> 0
        }
        if (deltaOffset == 0) return

        cancelOffsetAnimations()
        setContentOffset((contentOffset + deltaOffset).coerceIn(0, resolveRefreshHoldDistance()))
        startPendingIndicatorFlingIfNeeded(target)
    }

    private fun followFooterWithContentScroll(
        target: View,
        dyUnconsumed: Int,
    ) {
        val deltaOffset: Int = when {
            dyUnconsumed > 0 && !target.canScrollVertically(1) -> -dyUnconsumed
            else -> 0
        }
        if (deltaOffset == 0) return

        cancelOffsetAnimations()
        setContentOffset((contentOffset + deltaOffset).coerceIn(-resolveLoadMoreHoldDistance(), 0))
        startPendingIndicatorFlingIfNeeded(target)
    }

    /**
     * 内容惯性滚动到边界后, 用之前记录的 fling 速度继续拉出正在加载中的头尾.
     */
    private fun startPendingIndicatorFlingIfNeeded(target: View) {
        when (state) {
            RefreshLoadState.Refreshing -> startPendingRefreshIndicatorFlingIfNeeded(target)
            RefreshLoadState.LoadingMore -> startPendingLoadMoreIndicatorFlingIfNeeded(target)
            else -> Unit
        }
    }

    /**
     * 刷新中向顶部惯性滚动到边界后, 继续拉出 header.
     */
    private fun startPendingRefreshIndicatorFlingIfNeeded(target: View) {
        val holdDistance: Int = resolveRefreshHoldDistance()
        val shouldContinueFling: Boolean =
            pendingIndicatorFlingVelocityY < 0f &&
                contentOffset < holdDistance &&
                !canChildScrollUp(target)
        if (!shouldContinueFling) return

        startIndicatorFling(
            mode = IndicatorFlingMode.Refresh,
            velocity = resolveIndicatorFlingVelocity(pendingIndicatorFlingVelocityY),
            maxDistance = holdDistance,
        )
    }

    /**
     * 加载中向底部惯性滚动到边界后, 继续拉出 footer.
     */
    private fun startPendingLoadMoreIndicatorFlingIfNeeded(target: View) {
        val holdDistance: Int = resolveLoadMoreHoldDistance()
        val shouldContinueFling: Boolean =
            pendingIndicatorFlingVelocityY > 0f &&
                contentOffset > -holdDistance &&
                !target.canScrollVertically(1)
        if (!shouldContinueFling) return

        startIndicatorFling(
            mode = IndicatorFlingMode.LoadMore,
            velocity = resolveIndicatorFlingVelocity(pendingIndicatorFlingVelocityY),
            maxDistance = holdDistance,
        )
    }

    private fun startIndicatorFling(
        mode: IndicatorFlingMode,
        velocity: Int,
        maxDistance: Int,
    ) {
        if (maxDistance <= 0) return

        activeAnimator?.cancel()
        stopIndicatorFling(clearPendingVelocity = false)
        indicatorFlingMode = mode
        pendingIndicatorFlingVelocityY = 0f
        val startY: Int = when (mode) {
            IndicatorFlingMode.Refresh -> contentOffset
            IndicatorFlingMode.LoadMore -> -contentOffset
        }.coerceIn(0, maxDistance)
        indicatorScroller.fling(
            0,
            startY,
            0,
            velocity,
            0,
            0,
            0,
            maxDistance,
        )
        postInvalidateOnAnimation()
    }

    private fun resolveIndicatorFlingVelocity(velocityY: Float): Int =
        max(abs(velocityY).roundToInt(), MIN_INDICATOR_FLING_VELOCITY)

    private fun continueIndicatorFling() {
        val mode: IndicatorFlingMode = indicatorFlingMode ?: return
        if (!indicatorScroller.computeScrollOffset()) {
            indicatorFlingMode = null
            return
        }

        val offset: Int = when (mode) {
            IndicatorFlingMode.Refresh ->
                indicatorScroller.currY.coerceIn(0, resolveRefreshHoldDistance())

            IndicatorFlingMode.LoadMore ->
                -indicatorScroller.currY.coerceIn(0, resolveLoadMoreHoldDistance())
        }
        setContentOffset(offset)
        postInvalidateOnAnimation()
    }

    private fun cancelOffsetAnimations() {
        activeAnimator?.cancel()
        stopIndicatorFling(clearPendingVelocity = false)
    }

    private fun stopIndicatorFling(clearPendingVelocity: Boolean = true) {
        if (!indicatorScroller.isFinished) {
            indicatorScroller.forceFinished(true)
        }
        indicatorFlingMode = null
        if (clearPendingVelocity) {
            pendingIndicatorFlingVelocityY = 0f
        }
    }

    private fun bringIndicatorChildrenToFront() {
        refreshHeaderView?.bringToFront()
        loadFooterView?.bringToFront()
    }

    private fun updatePullState() {
        if (state == RefreshLoadState.Refreshing || state == RefreshLoadState.LoadingMore) return

        changeState(
            when {
                contentOffset > 0 && contentOffset >= resolveRefreshTriggerDistance() ->
                    RefreshLoadState.ReleaseToRefresh

                contentOffset > 0 -> RefreshLoadState.PullingDown
                contentOffset < 0 && abs(contentOffset) >= resolveLoadMoreTriggerDistance() ->
                    RefreshLoadState.ReleaseToLoadMore

                contentOffset < 0 -> RefreshLoadState.PullingUp
                else -> RefreshLoadState.Idle
            },
        )
    }

    private fun settleByCurrentOffset() {
        if (state == RefreshLoadState.Refreshing || state == RefreshLoadState.LoadingMore) return

        when {
            contentOffset >= resolveRefreshTriggerDistance() && refreshEnabled -> beginRefresh()
            abs(contentOffset) >= resolveLoadMoreTriggerDistance() && loadMoreEnabled -> beginLoadMore()
            contentOffset != 0 -> {
                changeState(RefreshLoadState.Settling)
                animateToOffset(0, animate = true)
            }
        }
    }

    private fun beginRefresh() {
        changeState(RefreshLoadState.Refreshing)
        animateToOffset(resolveRefreshHoldDistance(), animate = true)
        refreshLoadListener?.onRefreshLoad(RefreshLoadAction.Refresh)
    }

    private fun beginLoadMore() {
        changeState(RefreshLoadState.LoadingMore)
        animateToOffset(-resolveLoadMoreHoldDistance(), animate = true)
        refreshLoadListener?.onRefreshLoad(RefreshLoadAction.LoadMore)
    }

    private fun animateToOffset(targetOffset: Int, animate: Boolean) {
        stopIndicatorFling()
        activeAnimator?.cancel()
        if (!animate || animationDurationMillis <= 0L) {
            setContentOffset(targetOffset)
            if (targetOffset == 0 && state == RefreshLoadState.Settling) {
                changeState(RefreshLoadState.Idle)
            }
            return
        }

        val startOffset: Int = contentOffset
        activeAnimator = ValueAnimator.ofInt(startOffset, targetOffset).apply {
            duration = animationDurationMillis
            interpolator = settleInterpolator
            addUpdateListener { animator: ValueAnimator ->
                setContentOffset(animator.animatedValue as Int)
            }
            addListener(
                object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(animation: Animator) {
                        if (targetOffset == 0 && state == RefreshLoadState.Settling) {
                            changeState(RefreshLoadState.Idle)
                        }
                        activeAnimator = null
                    }
                },
            )
            start()
        }
    }

    private fun resolveRefreshTriggerDistance(): Int =
        triggerDistanceOrViewHeight(refreshTriggerDistance, refreshHeaderView?.measuredHeight)

    private fun resolveLoadMoreTriggerDistance(): Int =
        triggerDistanceOrViewHeight(loadMoreTriggerDistance, loadFooterView?.measuredHeight)

    private fun resolveRefreshHoldDistance(): Int =
        positiveOrFallback(refreshHeaderView?.measuredHeight ?: 0, resolveRefreshTriggerDistance())

    private fun resolveLoadMoreHoldDistance(): Int =
        positiveOrFallback(loadFooterView?.measuredHeight ?: 0, resolveLoadMoreTriggerDistance())

    private fun resolveMaxRefreshDistance(): Int =
        positiveOrFallback(maxDragDistance, resolveRefreshTriggerDistance() * MAX_DRAG_FACTOR)

    private fun resolveMaxLoadMoreDistance(): Int =
        positiveOrFallback(maxDragDistance, resolveLoadMoreTriggerDistance() * MAX_DRAG_FACTOR)

    private fun positiveOrFallback(value: Int, fallback: Int?): Int {
        if (value > 0) return value
        val fallbackValue: Int = fallback ?: 0
        return max(1, fallbackValue)
    }

    private fun triggerDistanceOrViewHeight(value: Int, viewHeight: Int?): Int {
        if (value >= 0) return max(1, value)
        return max(1, viewHeight ?: 0)
    }

    private fun changeState(newState: RefreshLoadState) {
        if (state == newState) return

        state = newState
        applyChildTranslations()
        notifyComponentsStateChanged(newState)
    }

    private fun notifyComponentsStateChanged(state: RefreshLoadState) {
        refreshHeaderView.asRefreshLoadComponent()?.onRefreshLoadStateChanged(this, state)
        loadFooterView.asRefreshLoadComponent()?.onRefreshLoadStateChanged(this, state)
    }

    private fun notifyComponentsOffsetChanged() {
        if (contentOffset >= 0) {
            refreshHeaderView.asRefreshLoadComponent()?.onRefreshLoadOffsetChanged(
                layout = this,
                offset = contentOffset,
                triggerDistance = resolveRefreshTriggerDistance(),
                holdDistance = resolveRefreshHoldDistance(),
            )
        }
        if (contentOffset <= 0) {
            loadFooterView.asRefreshLoadComponent()?.onRefreshLoadOffsetChanged(
                layout = this,
                offset = contentOffset,
                triggerDistance = resolveLoadMoreTriggerDistance(),
                holdDistance = resolveLoadMoreHoldDistance(),
            )
        }
    }

    private fun View?.asRefreshLoadComponent(): KitRefreshLoadComponent? =
        this as? KitRefreshLoadComponent

    /**
     * 指示器惯性滚动方向.
     */
    private enum class IndicatorFlingMode {
        /**
         * 刷新 header 方向.
         */
        Refresh,

        /**
         * 加载 footer 方向.
         */
        LoadMore,
    }

    /**
     * 子 View 在刷新加载容器中的角色.
     */
    enum class ChildRole {
        /**
         * 主要内容视图.
         */
        Content,

        /**
         * 下拉刷新头部.
         */
        Header,

        /**
         * 上拉加载尾部.
         */
        Footer,
    }

    /**
     * 刷新加载状态.
     *
     * 该状态描述容器当前唯一交互阶段. 下拉刷新和上拉加载互斥, 同一时刻不会同时进入
     * [Refreshing] 和 [LoadingMore].
     */
    enum class RefreshLoadState {
        /**
         * 空闲状态.
         */
        Idle,

        /**
         * 正在下拉, 尚未达到刷新触发距离.
         */
        PullingDown,

        /**
         * 已达到刷新触发距离.
         */
        ReleaseToRefresh,

        /**
         * 刷新中.
         */
        Refreshing,

        /**
         * 正在上拉, 尚未达到加载触发距离.
         */
        PullingUp,

        /**
         * 已达到加载更多触发距离.
         */
        ReleaseToLoadMore,

        /**
         * 加载更多中.
         */
        LoadingMore,

        /**
         * 正在回弹收起.
         */
        Settling,
    }

    /**
     * 用户触发的刷新加载动作.
     */
    enum class RefreshLoadAction {
        /**
         * 下拉刷新.
         */
        Refresh,

        /**
         * 上拉加载更多.
         */
        LoadMore,
    }

    /**
     * 刷新加载触发回调.
     */
    fun interface OnRefreshLoadListener {

        /**
         * 用户触发刷新或加载更多时回调.
         *
         * @param action 触发动作.
         */
        fun onRefreshLoad(action: RefreshLoadAction)
    }

    /**
     * 内容是否还能继续向顶部滚动的外部判定.
     */
    fun interface OnChildScrollUpCallback {

        /**
         * 返回 true 表示内容仍可向顶部滚动, 此时不应开始下拉刷新.
         *
         * @param layout 当前刷新加载容器.
         * @param target 当前滚动目标; 直接触摸拖拽时可能是 content child.
         */
        fun canChildScrollUp(layout: KitRefreshLoadLayout, target: View?): Boolean
    }

    /**
     * 子 View 布局参数.
     */
    class LayoutParams : FrameLayout.LayoutParams {

        /**
         * 子 View 在刷新加载容器中的角色.
         */
        var role: ChildRole = ChildRole.Content

        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
            context.withStyledAttributes(attrs, R.styleable.KitRefreshLoadLayout_Layout) {
                val roleValue: Int = getInt(
                    R.styleable.KitRefreshLoadLayout_Layout_kitLayoutRefreshLoadRole,
                    ROLE_CONTENT,
                )
                role = childRoleFromValue(roleValue)
            }
        }

        constructor(width: Int, height: Int) : super(width, height)

        constructor(source: ViewGroup.LayoutParams) : super(source)

        constructor(source: MarginLayoutParams) : super(source)

        constructor(source: LayoutParams) : super(source) {
            role = source.role
        }

        private fun childRoleFromValue(value: Int): ChildRole =
            when (value) {
                ROLE_HEADER -> ChildRole.Header
                ROLE_FOOTER -> ChildRole.Footer
                else -> ChildRole.Content
            }
    }

    private companion object {

        /**
         * 默认触发距离. 负数表示跟随 header/footer 自身高度.
         */
        private const val DEFAULT_TRIGGER_DISTANCE: Int = -1

        /**
         * 默认拖拽阻尼.
         */
        private const val DEFAULT_DRAG_RESISTANCE: Float = 2f

        /**
         * 额外拖拽阻尼倍数.
         */
        private const val EXTRA_DRAG_RESISTANCE_FACTOR: Float = 4f

        /**
         * 最小拖拽阻尼.
         */
        private const val MIN_DRAG_RESISTANCE: Float = 1f

        /**
         * 回弹动画减速因子.
         */
        private const val SETTLE_DECELERATE_FACTOR: Float = 2f

        /**
         * 最大拖拽距离倍数.
         */
        private const val MAX_DRAG_FACTOR: Int = 2

        /**
         * 默认回弹动画时长.
         */
        private const val DEFAULT_ANIMATION_DURATION_MILLIS: Long = 240L

        /**
         * 指示器惯性滚动最小速度.
         */
        private const val MIN_INDICATOR_FLING_VELOCITY: Int = 1_200

        /**
         * 无效触摸指针 ID.
         */
        private const val INVALID_POINTER_ID: Int = -1

        /**
         * 内容角色属性值.
         */
        private const val ROLE_CONTENT: Int = 0

        /**
         * 头部角色属性值.
         */
        private const val ROLE_HEADER: Int = 1

        /**
         * 尾部角色属性值.
         */
        private const val ROLE_FOOTER: Int = 2

        /**
         * 日志标签.
         */
        private const val TAG: String = "KitRefreshLoadLayout"
    }
}
