package com.whisper.kit.widget.refreshloadlayout

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.annotation.FloatRange
import androidx.annotation.LayoutRes
import androidx.annotation.Px
import androidx.core.content.withStyledAttributes
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
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
 * 原生 ScrollView 需开启 nestedScrollingEnabled, 其旧版嵌套回调按触摸处理;
 * 原生惯性或程序滚动不发送嵌套增量, 这类滚动不保证自动加载和指示器衔接.
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
     * header 为 GONE 时禁止该方向的手势和无障碍动作, 不改变开关值或结束已提交任务.
     * 设置为 false 时, 如果当前正在下拉、释放刷新或刷新中, 会开始收起 header;
     * 收起期间为 [RefreshLoadState.Settling], 完成后回到空闲状态, 不取消调用方的业务请求.
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
     * footer 为 GONE 时禁止该方向的手势、自动加载和无障碍动作, 不改变开关值或结束已提交任务.
     * 设置为 false 时, 如果当前正在上拉、释放加载或加载中, 会开始收起 footer;
     * 收起期间为 [RefreshLoadState.Settling], 完成后回到空闲状态, 不取消调用方的业务请求.
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
        set(value) {
            if (field == value) return
            field = value
            scrollRevision += 1
        }

    /**
     * 刷新触发距离, 单位 px.
     *
     * 小于 0 时使用 header 高度作为触发距离, 0 会归一化为 1 px.
     * 运行时修改会同步拖拽状态和组件距离, 不主动提交动作或重启忙碌、收起动画.
     */
    @get:Px
    @setparam:Px
    var refreshTriggerDistance: Int = DEFAULT_TRIGGER_DISTANCE
        set(value) {
            if (field == value) return
            val previousDistance: Int = resolveRefreshTriggerDistance()
            field = value
            if (initialized && previousDistance != resolveRefreshTriggerDistance()) {
                onEffectiveDistancesChanged()
            }
        }

    /**
     * 加载更多触发距离, 单位 px.
     *
     * 小于 0 时使用 footer 高度作为触发距离, 0 会归一化为 1 px.
     * 运行时修改会同步拖拽状态和组件距离, 不主动提交动作或重启忙碌、收起动画.
     */
    @get:Px
    @setparam:Px
    var loadMoreTriggerDistance: Int = DEFAULT_TRIGGER_DISTANCE
        set(value) {
            if (field == value) return
            val previousDistance: Int = resolveLoadMoreTriggerDistance()
            field = value
            if (initialized && previousDistance != resolveLoadMoreTriggerDistance()) {
                onEffectiveDistancesChanged()
            }
        }

    /**
     * 最大拖拽显示距离, 单位 px. 小于等于 0 时使用触发距离的两倍.
     */
    @get:Px
    @setparam:Px
    var maxDragDistance: Int = 0

    /**
     * 基础拖拽阻尼. 值越大, 内容跟随手指移动越慢.
     *
     * 实际拖拽阻尼会随已拉出距离增加, 越接近最大拖拽距离阻力越大.
     * 有限值小于 1 时按 1 处理; NaN 或无穷值抛出 IllegalArgumentException, 保留原值.
     */
    @get:FloatRange(from = 1.0, to = Double.MAX_VALUE)
    @setparam:FloatRange(from = -Double.MAX_VALUE, to = Double.MAX_VALUE)
    var dragResistance: Float = DEFAULT_DRAG_RESISTANCE
        set(value) {
            require(value.isFinite()) { "Drag resistance must be finite." }
            field = max(MIN_DRAG_RESISTANCE, value)
        }

    /**
     * 回弹和收起动画时长, 单位毫秒.
     *
     * 该时长用于 header/footer 展开及收起的位置动画, 小于等于 0 时直接定位.
     * 展开目标为组件高度与上下 margin 之和; 总和非正时使用该方向的有效触发距离.
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
     * 内容是否还能继续向指定方向滚动的外部判定.
     */
    private var scrollBoundaryCallback: ScrollBoundaryCallback? = null

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

    /** 阻尼换算后不足一个像素的位移, 在同一拖动内继续累计. */
    private var dragRemainder: Float = 0f

    /** 已取消或完成的触摸会话不再提交, 仅新的 DOWN 或嵌套会话解除阻止. */
    private var touchInteractionBlocked: Boolean = false

    /** 非触摸滚动独立开启会话, 不依赖下一次手指按下恢复. */
    private var nonTouchInteractionBlocked: Boolean = false

    private var nestedTouchTarget: View? = null
    private var nestedNonTouchTarget: View? = null
    private val nestedScrollingParentHelper: NestedScrollingParentHelper = NestedScrollingParentHelper(this)
    private var touchSequenceActive: Boolean = false
    private var nestedTouchStopPending: Boolean = false
    private var dispatchingTouchCancel: Boolean = false

    /** 组件同步回调可以结束操作, 用版本判断后续通知是否仍然有效. */
    private var stateRevision: Long = 0L
    private var offsetRevision: Long = 0L
    private var distanceRevision: Long = 0L
    /** 状态、位移及会话变化后, 外部边界回调所在的旧滚动计算不再有效. */
    private var scrollRevision: Long = 0L
    private var startingAction: Boolean = false
    private var initialized: Boolean = false

    /** 程序同步或主动操作可早于首次布局, 保留目标直到对应组件的尺寸可用. */
    private var pendingActionPosition: RefreshLoadAction? = null
    private var animatePendingActionPosition: Boolean = false

    // 首次状态通知可能改变组件尺寸; 尺寸重测时只补位移快照, 不重复通知同一状态.
    private val pendingComponentStates: MutableSet<View> = linkedSetOf()
    private val pendingComponentSnapshots: MutableSet<View> = linkedSetOf()
    private val deliveredComponentSnapshots: MutableMap<View, ComponentOffsetSnapshot> = mutableMapOf()
    private var synchronizedRefreshTriggerDistance: Int = 1
    private var synchronizedLoadMoreTriggerDistance: Int = 1
    private var synchronizedRefreshHoldDistance: Int = 1
    private var synchronizedLoadMoreHoldDistance: Int = 1
    private val synchronizeComponentsAfterLayout: Runnable = Runnable {
        synchronizeComponentDistances()
        synchronizePendingComponents()
        positionPendingActionIfReady()
    }

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
        initialized = true
        if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
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
            is FrameLayout.LayoutParams -> LayoutParams(params)
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
        scrollRevision += 1
        if (child is KitRefreshLoadComponent) {
            pendingComponentStates.add(child)
            pendingComponentSnapshots.add(child)
        }
        validateChildRoles()
        bringIndicatorChildrenToFront()
        if (childrenResolved) {
            updateEnabledByChildAvailability()
        }
        notifyAccessibilityStateChanged()
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        scrollRevision += 1
        pendingComponentStates.remove(child)
        pendingComponentSnapshots.remove(child)
        deliveredComponentSnapshots.remove(child)
        if (childrenResolved) {
            childrenResolved = false
            requestLayout()
        }
        notifyAccessibilityStateChanged()
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
        super.onLayout(changed, left, top, right, bottom)
        for (index: Int in 0 until childCount) {
            val child: View = getChildAt(index)
            if (child.isGone) continue

            val layoutParams: LayoutParams = child.layoutParams as LayoutParams
            when (layoutParams.role) {
                ChildRole.Header -> layoutHeader(child, layoutParams)
                ChildRole.Footer -> layoutFooter(child, layoutParams)
                ChildRole.Content -> Unit
            }
            applyChildTranslation(child)
        }
        if (isInLayout) {
            // ViewRoot 会延后或过滤布局回调内的 requestLayout, 在布局结束后通知才能可靠处理组件改尺寸/替换.
            if (pendingComponentSnapshots.isNotEmpty() || pendingActionPosition != null || haveComponentDistancesChanged()) {
                removeCallbacks(synchronizeComponentsAfterLayout)
                post(synchronizeComponentsAfterLayout)
            }
        } else {
            synchronizeComponentDistances()
            synchronizePendingComponents()
            positionPendingActionIfReady(fromLayout = true)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(synchronizeComponentsAfterLayout)
        nestedTouchTarget = null
        nestedNonTouchTarget = null
        nestedScrollingParentHelper.onStopNestedScroll(this, ViewCompat.TYPE_TOUCH)
        nestedScrollingParentHelper.onStopNestedScroll(this, ViewCompat.TYPE_NON_TOUCH)
        cancelTouchInteraction(animate = false)
        super.onDetachedFromWindow()
    }

    override fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        super.setEnabled(enabled)
        if (initialized && !enabled) {
            cancelTouchInteraction(animate = false)
        }
        notifyAccessibilityStateChanged()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val compat: AccessibilityNodeInfoCompat = AccessibilityNodeInfoCompat.wrap(info)
        if (canPerformAccessibilityAction(RefreshLoadAction.Refresh)) {
            compat.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    R.id.kit_accessibility_action_refresh,
                    context.getString(R.string.kit_refresh_load_action_refresh),
                ),
            )
        }
        if (canPerformAccessibilityAction(RefreshLoadAction.LoadMore)) {
            compat.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    R.id.kit_accessibility_action_load_more,
                    context.getString(R.string.kit_refresh_load_action_load_more),
                ),
            )
        }
        if (compat.stateDescription == null && ViewCompat.getStateDescription(this) == null) {
            compat.stateDescription = when (state) {
                RefreshLoadState.Refreshing -> context.getString(R.string.kit_refresh_load_state_refreshing)
                RefreshLoadState.LoadingMore -> context.getString(R.string.kit_refresh_load_state_loading_more)
                else -> null
            }
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val refreshLoadAction: RefreshLoadAction = when (action) {
            R.id.kit_accessibility_action_refresh -> RefreshLoadAction.Refresh
            R.id.kit_accessibility_action_load_more -> RefreshLoadAction.LoadMore
            else -> return super.performAccessibilityAction(action, arguments)
        }
        if (!canPerformAccessibilityAction(refreshLoadAction)) return false
        blockCurrentInteractions()
        beginAction(refreshLoadAction)
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val wasDispatchingTouchCancel: Boolean = dispatchingTouchCancel
        val revision: Long = stateRevision
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrollRevision += 1
                touchInteractionBlocked = !isEnabled
                touchSequenceActive = true
                nestedTouchStopPending = false
                stopIndicatorFling()
            }
            // 子 View 会在处理 CANCEL 时先调用 onStopNestedScroll, 必须提前标记取消.
            MotionEvent.ACTION_CANCEL -> {
                scrollRevision += 1
                touchInteractionBlocked = true
                dispatchingTouchCancel = true
            }
        }
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    touchSequenceActive = false
                    val shouldSettle: Boolean = nestedTouchStopPending
                    nestedTouchStopPending = false
                    if (shouldSettle) {
                        settleByCurrentOffset()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    dispatchingTouchCancel = wasDispatchingTouchCancel
                    // 直接与嵌套分发共用一次清理; 子回调新建的任务不属于旧取消事件.
                    if (!wasDispatchingTouchCancel && (stateRevision == revision || !isBusyState())) {
                        cancelTouchInteraction(animate = true)
                    }
                }
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || isBusyState() || nestedTouchTarget != null) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrollRevision += 1
                activePointerId = ev.getPointerId(0)
                lastMotionY = ev.y
                initialMotionY = ev.y
                dragging = false
                dragRemainder = 0f
                touchInteractionBlocked = false
                stopIndicatorFling()
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex: Int = ev.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val y: Float = ev.getY(pointerIndex)
                val dy: Float = y - initialMotionY
                if (!touchInteractionBlocked && abs(dy) > touchSlop && canDragBy(dy)) {
                    beginTouchDrag()
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
                dragRemainder = 0f
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
                scrollRevision += 1
                activePointerId = event.getPointerId(0)
                lastMotionY = event.y
                initialMotionY = event.y
                dragging = false
                dragRemainder = 0f
                touchInteractionBlocked = false
                stopIndicatorFling()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (touchInteractionBlocked || isBusyState()) return false
                val pointerIndex: Int = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val y: Float = event.getY(pointerIndex)
                val dy: Float = y - lastMotionY
                if (!dragging && abs(y - initialMotionY) > touchSlop && canDragBy(y - initialMotionY)) {
                    beginTouchDrag()
                }
                if (dragging) {
                    if (moveOffsetBy(dy)) {
                        if (dragging) lastMotionY = y
                        return true
                    }
                    return false
                }
            }

            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)

            MotionEvent.ACTION_CANCEL -> {
                if (!dispatchingTouchCancel) {
                    cancelTouchInteraction(animate = true)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging || contentOffset != 0) {
                    dragging = false
                    activePointerId = INVALID_POINTER_ID
                    dragRemainder = 0f
                    settleByCurrentOffset()
                    return true
                }
                activePointerId = INVALID_POINTER_ID
                dragRemainder = 0f
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

    override fun getNestedScrollAxes(): Int = nestedScrollingParentHelper.nestedScrollAxes

    // Framework 和旧版 AndroidX 没有 type 的入口统一按 TOUCH 桥接, 不从速度推测滚动类型.
    override fun onStartNestedScroll(child: View, target: View, axes: Int): Boolean =
        onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH)

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
    ) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH)
    }

    override fun onStopNestedScroll(target: View) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH)
        // 旧版协议没有独立的 NON_TOUCH stop, 未交给新协议的速度随旧会话结束.
        if (nestedNonTouchTarget == null) {
            pendingIndicatorFlingVelocityY = 0f
        }
    }

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
    ) {
        scrollRevision += 1
        nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes, type)
        if (type == ViewCompat.TYPE_TOUCH) {
            stopIndicatorFling()
            nestedTouchTarget = target
            touchInteractionBlocked = !isEnabled
            nestedTouchStopPending = false
            dragRemainder = 0f
        } else if (type == ViewCompat.TYPE_NON_TOUCH) {
            nestedNonTouchTarget = target
            nonTouchInteractionBlocked = !isEnabled
        }
    }

    override fun onNestedPreScroll(
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        if (isNestedInteractionBlocked(type)) return
        if (isBusyState()) {
            consumeBusyIndicatorPreScroll(target, dy, consumed)
            return
        }
        if (type != ViewCompat.TYPE_TOUCH) return

        if (dy > 0 && contentOffset > 0) {
            val consumeY: Int = min(dy, contentOffset)
            cancelOffsetAnimations()
            dragRemainder = 0f
            setContentOffset(contentOffset - consumeY, fromDrag = true)
            consumed[1] += consumeY
        } else if (dy < 0 && contentOffset < 0) {
            val consumeY: Int = max(dy, contentOffset)
            cancelOffsetAnimations()
            dragRemainder = 0f
            setContentOffset(contentOffset - consumeY, fromDrag = true)
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
        if (isNestedInteractionBlocked(type)) return
        if (isBusyState()) {
            followIndicatorWithContentScroll(
                target = target,
                dyUnconsumed = dyUnconsumed,
                consumed = consumed,
            )
            return
        }
        val revision: Long = scrollRevision
        if (dispatchAutoLoadMoreIfNeeded(target, dyConsumed, dyUnconsumed)) return
        if (scrollRevision != revision || isNestedInteractionBlocked(type)) return
        if (type != ViewCompat.TYPE_TOUCH) return

        if (dyUnconsumed < 0 && canStartRefreshDrag(target)) {
            if (moveOffsetBy(-dyUnconsumed.toFloat(), target)) {
                consumed[1] += dyUnconsumed
            }
        } else if (dyUnconsumed > 0 && canStartLoadMoreDrag(target)) {
            if (moveOffsetBy(-dyUnconsumed.toFloat(), target)) {
                consumed[1] += dyUnconsumed
            }
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
        scrollRevision += 1
        nestedScrollingParentHelper.onStopNestedScroll(target, type)
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            nestedNonTouchTarget = null
            pendingIndicatorFlingVelocityY = 0f
            return
        }
        if (type != ViewCompat.TYPE_TOUCH) return

        nestedTouchTarget = null
        dragRemainder = 0f
        if (!isEnabled || touchInteractionBlocked) return
        // ViewGroup 内部清理子触摸目标时, CANCEL 会绕过本容器的 dispatchTouchEvent.
        // 真实触摸序列只在收到 UP 后结算, 避免把这类嵌套停止当成释放.
        if (touchSequenceActive) {
            nestedTouchStopPending = true
            return
        }
        settleByCurrentOffset()
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        if (!isEnabled || touchInteractionBlocked) return false
        scrollRevision += 1
        if (isBusyState() && !isBusyIndicatorAvailable()) {
            stopIndicatorFling()
            return false
        }
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
     * @param listener 拖拽释放、自动加载或无障碍动作触发的回调; null 表示移除监听.
     */
    fun setOnRefreshLoadListener(listener: OnRefreshLoadListener?) {
        refreshLoadListener = listener
    }

    /**
     * 设置内容是否还能继续向指定方向滚动的外部判定.
     *
     * 嵌套滚动层级复杂时, 直接 content child 的 [View.canScrollVertically] 可能无法代表真实列表边界.
     * 自定义判定同时用于拖拽、自动加载和忙碌期间的头尾滚动交接, 不替代启用状态或触发距离检查.
     *
     * @param callback 内容滚动能力判定, 传入 null 时恢复当前滚动目标的默认 View 判定.
     */
    fun setScrollBoundaryCallback(callback: ScrollBoundaryCallback?) {
        scrollBoundaryCallback = callback
    }

    /**
     * 结束刷新显示并收起 header, 也可取消该方向尚未提交的拖拽.
     *
     * 当前为加载更多任务时不处理; 调用方的业务请求仍由调用方结束或取消.
     *
     * @param animate 是否使用动画切换位置.
     */
    fun finishRefresh(animate: Boolean = true) {
        resetRefreshState(animate)
    }

    /**
     * 结束加载更多显示并收起 footer, 也可取消该方向尚未提交的拖拽.
     *
     * 当前为刷新任务时不处理; 调用方的业务请求仍由调用方结束或取消.
     *
     * @param animate 是否使用动画切换位置.
     */
    fun finishLoadMore(animate: Boolean = true) {
        resetLoadMoreState(animate)
    }

    /**
     * 同步业务当前任务的显示状态, 不触发 [OnRefreshLoadListener].
     *
     * 不受交互启用开关限制. 相同忙碌状态保留当前位置和动画; null 在空闲或收起期间重复同步也不重启动画.
     * 同步其它状态会结束未提交手势, null 会收起已有拖拽. 组件尚未布局、缺失或为 GONE 时等待尺寸可用后再定位.
     * 不保存或恢复业务请求, 页面重建后应由调用方再次同步真实任务状态.
     *
     * @param action 当前任务, null 表示清除忙碌显示并收起指示器.
     * @param animate 是否播放位置动画, 恢复或首次同步默认直接定位.
     */
    @JvmOverloads
    fun setActiveAction(action: RefreshLoadAction?, animate: Boolean = false) {
        if (action == null) {
            if (state == RefreshLoadState.Idle || state == RefreshLoadState.Settling) return
            startingAction = false
            settleToRest(animate)
            return
        }
        val busyState: RefreshLoadState = busyStateFor(action)
        if (state == busyState) return

        blockCurrentInteractions()
        cancelOffsetAnimations()
        stopIndicatorFling()
        startingAction = false
        pendingActionPosition = action
        animatePendingActionPosition = animate
        if (!changeState(busyState)) return
        positionPendingActionIfReady()
        if (pendingActionPosition != null) requestLayout()
    }

    private fun resetRefreshState(animate: Boolean = true) {
        if (!isHeaderState()) return

        settleToRest(animate)
    }

    private fun resetLoadMoreState(animate: Boolean = true) {
        if (!isFooterState()) return

        settleToRest(animate)
    }

    private fun beginTouchDrag() {
        dragRemainder = 0f
        dragging = true
        takeOverOffsetAnimation()
    }

    private fun takeOverOffsetAnimation() {
        cancelOffsetAnimations()
        // 接管后的首帧可能没有有效位移, 仍需离开回弹状态以便松手时正常收尾.
        if (state == RefreshLoadState.Settling) {
            updatePullState()
        }
    }

    private fun isNestedInteractionBlocked(type: Int): Boolean =
        !isEnabled || if (type == ViewCompat.TYPE_NON_TOUCH) {
            nonTouchInteractionBlocked
        } else {
            touchInteractionBlocked
        }

    private fun blockCurrentInteractions() {
        scrollRevision += 1
        touchInteractionBlocked = true
        nonTouchInteractionBlocked = true
        touchSequenceActive = false
        nestedTouchStopPending = false
        dragging = false
        activePointerId = INVALID_POINTER_ID
        dragRemainder = 0f
    }

    private fun cancelTouchInteraction(animate: Boolean) {
        blockCurrentInteractions()
        stopIndicatorFling()
        if ((isBusyState() && !startingAction) || (state == RefreshLoadState.Idle && contentOffset == 0)) {
            cancelOffsetAnimator()
        } else {
            settleToRest(animate)
        }
    }

    private fun settleToRest(animate: Boolean) {
        pendingActionPosition = null
        blockCurrentInteractions()
        stopIndicatorFling()
        if (animate && state == RefreshLoadState.Settling && activeAnimator != null) return
        if (changeState(RefreshLoadState.Settling)) {
            animateToOffset(0, animate)
        }
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
        scrollRevision += 1
        if (!value && isHeaderState() && (!refreshEnabledRequested || pendingActionPosition != RefreshLoadAction.Refresh)) {
            resetRefreshState()
        }
        notifyAccessibilityStateChanged()
    }

    private fun fieldSetLoadMoreEnabled(value: Boolean) {
        loadMoreEnabledValue = value
        scrollRevision += 1
        if (!value && isFooterState() && (!loadMoreEnabledRequested || pendingActionPosition != RefreshLoadAction.LoadMore)) {
            resetLoadMoreState()
        }
        notifyAccessibilityStateChanged()
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
        dragRemainder = 0f
    }

    private fun layoutHeader(child: View, layoutParams: LayoutParams) {
        val childLeft: Int = child.left
        val childTop: Int = paddingTop - child.measuredHeight - layoutParams.bottomMargin
        child.layout(
            childLeft,
            childTop,
            childLeft + child.measuredWidth,
            childTop + child.measuredHeight,
        )
    }

    private fun layoutFooter(child: View, layoutParams: LayoutParams) {
        val childLeft: Int = child.left
        val childTop: Int = measuredHeight - paddingBottom + layoutParams.topMargin
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
        isAtScrollBoundary(target, -1) {
            isActionInteractionEnabled(RefreshLoadAction.Refresh) && !isBusyState() && !touchInteractionBlocked
        }

    private fun canStartLoadMoreDrag(target: View?): Boolean =
        isAtScrollBoundary(target, 1) {
            isActionInteractionEnabled(RefreshLoadAction.LoadMore) && !isBusyState() && !touchInteractionBlocked
        }

    private fun canAutoLoadMore(target: View, dyConsumed: Int, dyUnconsumed: Int): Boolean =
        isAtScrollBoundary(target, 1) {
            autoLoadMoreEnabled && isActionInteractionEnabled(RefreshLoadAction.LoadMore) &&
                state == RefreshLoadState.Idle && contentOffset == 0 && (dyConsumed > 0 || dyUnconsumed > 0)
        }

    private inline fun isAtScrollBoundary(target: View?, direction: Int, isAvailable: () -> Boolean): Boolean {
        if (!isAvailable()) return false
        val revision: Long = scrollRevision
        val canScroll: Boolean = canChildScroll(target, direction)
        // 边界查询允许同步结束任务或重建会话; 旧查询不能继续移动内容、消费滚动或启动惯性.
        return scrollRevision == revision && !canScroll && isAvailable()
    }

    private fun canChildScroll(target: View?, direction: Int): Boolean {
        return scrollBoundaryCallback?.canScrollVertically(this, target, direction)
            ?: (target?.canScrollVertically(direction) == true)
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
        if (isBusyState()) {
            state == RefreshLoadState.Refreshing
        } else {
            state == RefreshLoadState.PullingDown || state == RefreshLoadState.ReleaseToRefresh || contentOffset > 0
        }

    private fun isFooterState(): Boolean =
        if (isBusyState()) {
            state == RefreshLoadState.LoadingMore
        } else {
            state == RefreshLoadState.PullingUp || state == RefreshLoadState.ReleaseToLoadMore || contentOffset < 0
        }

    private fun moveOffsetBy(deltaY: Float, target: View? = contentView): Boolean {
        if (deltaY == 0f || !isEnabled || touchInteractionBlocked || isBusyState()) return false

        takeOverOffsetAnimation()
        if (!isEnabled || touchInteractionBlocked || isBusyState()) return false
        val revision: Long = scrollRevision
        val accumulatedDelta: Float = dragRemainder + deltaY / resolveDragResistance(deltaY)
        val adjustedDelta: Int = accumulatedDelta.roundToInt()
        if (adjustedDelta == 0) {
            dragRemainder = accumulatedDelta
            return true
        }

        val canLoadMore: Boolean = canStartLoadMoreDrag(target)
        if (scrollRevision != revision) return false
        val canRefresh: Boolean = canStartRefreshDrag(target)
        if (scrollRevision != revision) return false

        // 后一次边界查询可能隐藏前一方向, 最终按当前可用性限制展开, 但仍允许收回已有位移.
        val loadMoreAvailable: Boolean = isActionInteractionEnabled(RefreshLoadAction.LoadMore)
        val refreshAvailable: Boolean = isActionInteractionEnabled(RefreshLoadAction.Refresh)
        val minOffset: Int = if (canLoadMore && loadMoreAvailable) {
            -resolveMaxLoadMoreDistance()
        } else {
            min(0, contentOffset)
        }
        val maxOffset: Int = if (canRefresh && refreshAvailable) {
            resolveMaxRefreshDistance()
        } else {
            max(0, contentOffset)
        }
        val requestedOffset: Long = contentOffset.toLong() + adjustedDelta
        val newOffset: Int = requestedOffset.coerceIn(minOffset.toLong(), maxOffset.toLong()).toInt()
        if (newOffset == contentOffset && ((deltaY < 0f && !loadMoreAvailable) || (deltaY > 0f && !refreshAvailable))) {
            dragRemainder = 0f
            return false
        }
        dragRemainder = if (
            requestedOffset != newOffset.toLong() ||
            (deltaY < 0f && newOffset == minOffset) || (deltaY > 0f && newOffset == maxOffset)
        ) {
            0f
        } else {
            accumulatedDelta - adjustedDelta
        }
        setContentOffset(newOffset, fromDrag = true)
        return true
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

    private fun setContentOffset(offset: Int, fromDrag: Boolean = false) {
        if (contentOffset == offset) return

        val previousOffset: Int = contentOffset
        contentOffset = offset
        scrollRevision += 1
        val revision: Long = ++offsetRevision
        applyChildTranslations()
        if (fromDrag) {
            updatePullState()
        }
        if (offsetRevision == revision) {
            notifyComponentsOffsetChanged(previousOffset)
        }
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
        consumed: IntArray,
    ) {
        if (!isBusyIndicatorAvailable()) {
            stopIndicatorFling()
            return
        }
        when (state) {
            RefreshLoadState.Refreshing -> followHeaderWithContentScroll(target, dyUnconsumed, consumed)
            RefreshLoadState.LoadingMore -> followFooterWithContentScroll(target, dyUnconsumed, consumed)
            else -> Unit
        }
    }

    private fun consumeBusyIndicatorPreScroll(target: View, dy: Int, consumed: IntArray) {
        if (!isBusyIndicatorAvailable()) {
            stopIndicatorFling()
            return
        }
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
                canExpandBusyIndicator(target, RefreshLoadAction.Refresh) -> {
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
                canExpandBusyIndicator(target, RefreshLoadAction.LoadMore) -> {
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
        consumed: IntArray,
    ) {
        if (dyUnconsumed >= 0 || !canExpandBusyIndicator(target, RefreshLoadAction.Refresh)) return
        val holdDistance: Int = resolveRefreshHoldDistance()
        if (contentOffset >= holdDistance) return

        val consumeY: Int = max(dyUnconsumed, contentOffset - holdDistance)
        cancelOffsetAnimations()
        consumed[1] += consumeY
        setContentOffset(contentOffset - consumeY)
        startPendingIndicatorFlingIfNeeded(target)
    }

    private fun followFooterWithContentScroll(
        target: View,
        dyUnconsumed: Int,
        consumed: IntArray,
    ) {
        if (dyUnconsumed <= 0 || !canExpandBusyIndicator(target, RefreshLoadAction.LoadMore)) return
        val holdDistance: Int = resolveLoadMoreHoldDistance()
        if (contentOffset <= -holdDistance) return

        val consumeY: Int = min(dyUnconsumed, contentOffset + holdDistance)
        cancelOffsetAnimations()
        consumed[1] += consumeY
        setContentOffset(contentOffset - consumeY)
        startPendingIndicatorFlingIfNeeded(target)
    }

    /**
     * 内容惯性滚动到边界后, 用之前记录的 fling 速度继续拉出正在加载中的头尾.
     */
    private fun startPendingIndicatorFlingIfNeeded(target: View) {
        if (!isBusyIndicatorAvailable()) {
            stopIndicatorFling()
            return
        }
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
                canExpandBusyIndicator(target, RefreshLoadAction.Refresh)
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
                canExpandBusyIndicator(target, RefreshLoadAction.LoadMore)
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

        cancelOffsetAnimator()
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
        if (!isBusyIndicatorAvailable()) {
            stopIndicatorFling()
            return
        }
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
        cancelOffsetAnimator()
        stopIndicatorFling(clearPendingVelocity = false)
    }

    private fun cancelOffsetAnimator() {
        val animator: ValueAnimator = activeAnimator ?: return
        activeAnimator = null
        animator.cancel()
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

    private fun onEffectiveDistancesChanged() {
        synchronizedRefreshTriggerDistance = resolveRefreshTriggerDistance()
        synchronizedLoadMoreTriggerDistance = resolveLoadMoreTriggerDistance()
        synchronizedRefreshHoldDistance = resolveRefreshHoldDistance()
        synchronizedLoadMoreHoldDistance = resolveLoadMoreHoldDistance()
        scrollRevision += 1
        val revision: Long = ++distanceRevision
        if (!isBusyState() && state != RefreshLoadState.Settling) {
            val updatedState: RefreshLoadState = resolvePullState()
            if (state != updatedState && !changeState(updatedState)) return
        }
        if (distanceRevision == revision) {
            notifyComponentsOffsetChanged(contentOffset, notifyBoth = true)
        }
    }

    private fun updatePullState() {
        if (isBusyState()) return

        changeState(resolvePullState())
    }

    private fun resolvePullState(): RefreshLoadState = when {
        contentOffset > 0 && contentOffset >= resolveRefreshTriggerDistance() -> RefreshLoadState.ReleaseToRefresh
        contentOffset > 0 -> RefreshLoadState.PullingDown
        contentOffset < 0 && abs(contentOffset) >= resolveLoadMoreTriggerDistance() -> RefreshLoadState.ReleaseToLoadMore
        contentOffset < 0 -> RefreshLoadState.PullingUp
        else -> RefreshLoadState.Idle
    }

    private fun settleByCurrentOffset() {
        if (!isEnabled || touchInteractionBlocked || isBusyState() || state == RefreshLoadState.Settling) return

        when {
            contentOffset >= resolveRefreshTriggerDistance() &&
                isActionInteractionEnabled(RefreshLoadAction.Refresh) -> beginRefresh()
            contentOffset <= -resolveLoadMoreTriggerDistance() &&
                isActionInteractionEnabled(RefreshLoadAction.LoadMore) -> beginLoadMore()
            contentOffset != 0 -> settleToRest(animate = true)
        }
    }

    private fun beginRefresh() {
        beginAction(RefreshLoadAction.Refresh)
    }

    private fun beginLoadMore() {
        beginAction(RefreshLoadAction.LoadMore)
    }

    private fun busyStateFor(action: RefreshLoadAction): RefreshLoadState = when (action) {
        RefreshLoadAction.Refresh -> RefreshLoadState.Refreshing
        RefreshLoadAction.LoadMore -> RefreshLoadState.LoadingMore
    }

    private fun beginAction(action: RefreshLoadAction) {
        if (!isActionInteractionEnabled(action) || isBusyState()) return
        val busyState: RefreshLoadState = busyStateFor(action)
        startingAction = true
        dragRemainder = 0f
        pendingActionPosition = action
        animatePendingActionPosition = true
        try {
            if (!changeState(busyState)) return
            if (!isActionInteractionEnabled(action)) {
                settleToRest(animate = true)
                return
            }
            val revision: Long = stateRevision
            positionPendingActionIfReady()
            if (pendingActionPosition != null) requestLayout()
            if (stateRevision != revision || state != busyState) return
            // 状态和定位回调都可能隐藏组件, 尚未通知业务的动作必须收起, 避免留下空转的忙碌状态.
            if (!isActionInteractionEnabled(action)) {
                settleToRest(animate = true)
                return
            }
            // 从业务回调开始算已提交任务; 之前的组件回调仍可同步取消此次操作.
            startingAction = false
            refreshLoadListener?.onRefreshLoad(action)
        } finally {
            startingAction = false
        }
    }

    private fun positionPendingActionIfReady(fromLayout: Boolean = false) {
        val action: RefreshLoadAction = pendingActionPosition ?: return
        if (!fromLayout && (!isLaidOut || isLayoutRequested)) return
        val indicator: View = when (action) {
            RefreshLoadAction.Refresh -> refreshHeaderView
            RefreshLoadAction.LoadMore -> loadFooterView
        } ?: return
        if (!isComponentLayoutReady(indicator) || indicator in pendingComponentStates) return

        val targetOffset: Int = when (action) {
            RefreshLoadAction.Refresh -> resolveRefreshHoldDistance()
            RefreshLoadAction.LoadMore -> -resolveLoadMoreHoldDistance()
        }
        val animate: Boolean = animatePendingActionPosition
        pendingActionPosition = null
        animateToOffset(targetOffset, animate)
    }

    private fun canPerformAccessibilityAction(action: RefreshLoadAction): Boolean =
        state == RefreshLoadState.Idle && isActionInteractionEnabled(action)

    private fun isActionInteractionEnabled(action: RefreshLoadAction): Boolean =
        isEnabled && when (action) {
            RefreshLoadAction.Refresh -> refreshEnabled && refreshHeaderView?.isGone == false
            RefreshLoadAction.LoadMore -> loadMoreEnabled && loadFooterView?.isGone == false
        }

    private fun isBusyIndicatorAvailable(): Boolean = when (state) {
        RefreshLoadState.Refreshing -> refreshHeaderView?.isGone == false
        RefreshLoadState.LoadingMore -> loadFooterView?.isGone == false
        else -> false
    }

    private fun canExpandBusyIndicator(target: View, action: RefreshLoadAction): Boolean =
        isAtScrollBoundary(target, if (action == RefreshLoadAction.Refresh) -1 else 1) {
            isEnabled && state == busyStateFor(action) && isBusyIndicatorAvailable()
        }

    private fun notifyAccessibilityStateChanged() {
        if (initialized) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    private fun animateToOffset(targetOffset: Int, animate: Boolean) {
        stopIndicatorFling()
        cancelOffsetAnimator()
        dragRemainder = 0f
        val revision: Long = stateRevision
        if (!animate || animationDurationMillis <= 0L || contentOffset == targetOffset) {
            setContentOffset(targetOffset)
            if (stateRevision == revision && targetOffset == 0 && state == RefreshLoadState.Settling) {
                changeState(RefreshLoadState.Idle)
            }
            return
        }

        val startOffset: Int = contentOffset
        val animator: ValueAnimator = ValueAnimator.ofInt(startOffset, targetOffset).apply {
            duration = animationDurationMillis
            interpolator = settleInterpolator
            addUpdateListener { animator: ValueAnimator ->
                if (activeAnimator === animator) {
                    setContentOffset(animator.animatedValue as Int)
                }
            }
            addListener(
                object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(animation: Animator) {
                        if (activeAnimator !== animation) return
                        activeAnimator = null
                        if (stateRevision == revision && targetOffset == 0 && state == RefreshLoadState.Settling) {
                            changeState(RefreshLoadState.Idle)
                        }
                    }
                },
            )
        }
        activeAnimator = animator
        animator.start()
    }

    private fun resolveRefreshTriggerDistance(): Int =
        triggerDistanceOrViewHeight(refreshTriggerDistance, refreshHeaderView?.measuredHeight)

    private fun resolveLoadMoreTriggerDistance(): Int =
        triggerDistanceOrViewHeight(loadMoreTriggerDistance, loadFooterView?.measuredHeight)

    private fun resolveRefreshHoldDistance(): Int =
        resolveIndicatorHoldDistance(refreshHeaderView, resolveRefreshTriggerDistance())

    private fun resolveLoadMoreHoldDistance(): Int =
        resolveIndicatorHoldDistance(loadFooterView, resolveLoadMoreTriggerDistance())

    private fun resolveIndicatorHoldDistance(indicator: View?, fallback: Int): Int {
        val params: LayoutParams? = indicator?.layoutParams as? LayoutParams
        val height: Long = (indicator?.measuredHeight ?: 0).toLong() +
            (params?.topMargin ?: 0) + (params?.bottomMargin ?: 0)
        return if (height > 0L) height.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else fallback
    }

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

    private fun changeState(newState: RefreshLoadState): Boolean {
        scrollRevision += 1
        val revision: Long = ++stateRevision
        if (state == newState) return true

        val previousState: RefreshLoadState = state
        state = newState
        if (pendingActionPosition?.let(::busyStateFor) != newState) {
            pendingActionPosition = null
        }
        applyChildTranslations()
        if (previousState == RefreshLoadState.Idle || newState == RefreshLoadState.Idle ||
            previousState == RefreshLoadState.Refreshing || previousState == RefreshLoadState.LoadingMore || isBusyState()
        ) {
            notifyAccessibilityStateChanged()
        }
        notifyComponentsStateChanged(newState)
        return stateRevision == revision
    }

    private fun notifyComponentsStateChanged(state: RefreshLoadState) {
        val revision: Long = stateRevision
        val header: View? = refreshHeaderView
        val footer: View? = loadFooterView
        if (header != null && header !in pendingComponentStates) {
            notifyComponentStateChanged(header, state)
        }
        if (stateRevision != revision) return
        if (footer != null && footer.parent === this && footer !in pendingComponentStates) {
            notifyComponentStateChanged(footer, state)
        }
    }

    private fun notifyComponentStateChanged(child: View, state: RefreshLoadState) {
        val component: KitRefreshLoadComponent = child.asRefreshLoadComponent() ?: return
        component.onRefreshLoadStateChanged(this, state)
        if (child.parent === this && !isComponentLayoutReady(child)) {
            pendingComponentSnapshots.add(child)
        }
    }

    private fun notifyComponentsOffsetChanged(previousOffset: Int, notifyBoth: Boolean = false) {
        val currentStateRevision: Long = stateRevision
        val currentOffsetRevision: Long = offsetRevision
        val currentDistanceRevision: Long = distanceRevision
        val header: View? = refreshHeaderView
        val footer: View? = loadFooterView
        // 跳过零点切换方向时, 退出方向也必须收到归零通知.
        if (
            (notifyBoth || contentOffset >= 0 || previousOffset > 0) &&
            header != null && header !in pendingComponentSnapshots
        ) {
            notifyComponentOffsetChanged(header, ChildRole.Header)
        }
        if (
            stateRevision != currentStateRevision || offsetRevision != currentOffsetRevision ||
            distanceRevision != currentDistanceRevision
        ) return
        if (
            (notifyBoth || contentOffset <= 0 || previousOffset < 0) &&
            footer != null && footer.parent === this && footer !in pendingComponentSnapshots
        ) {
            notifyComponentOffsetChanged(footer, ChildRole.Footer)
        }
    }

    private fun notifyComponentOffsetChanged(child: View, role: ChildRole, onlyIfChanged: Boolean = false) {
        val component: KitRefreshLoadComponent = child.asRefreshLoadComponent() ?: return
        if (!isComponentLayoutReady(child)) {
            if (child.parent === this) {
                pendingComponentSnapshots.add(child)
            }
            return
        }
        val snapshot: ComponentOffsetSnapshot = when (role) {
            ChildRole.Header -> ComponentOffsetSnapshot(
                role, child.width, child.height,
                max(0, contentOffset), resolveRefreshTriggerDistance(), resolveRefreshHoldDistance(),
            )
            ChildRole.Footer -> ComponentOffsetSnapshot(
                role, child.width, child.height,
                min(0, contentOffset), resolveLoadMoreTriggerDistance(), resolveLoadMoreHoldDistance(),
            )
            ChildRole.Content -> return
        }
        // 相同文案也可能请求布局; 补发只在实际尺寸或数据变化时通知, 避免循环阻塞初始定位.
        if (onlyIfChanged && deliveredComponentSnapshots[child] == snapshot) return
        // 回调可重入或移除组件, 必须先记录, 返回后不能覆盖重入产生的新快照或重新保留已移除的实例.
        deliveredComponentSnapshots[child] = snapshot
        component.onRefreshLoadOffsetChanged(this, snapshot.offset, snapshot.triggerDistance, snapshot.holdDistance)
        if (child.parent === this && !isComponentLayoutReady(child)) {
            pendingComponentSnapshots.add(child)
        }
    }

    private fun isComponentLayoutReady(child: View): Boolean =
        child.parent === this && !child.isGone && child.isLaidOut && !child.isLayoutRequested

    private fun haveComponentDistancesChanged(): Boolean =
        synchronizedRefreshTriggerDistance != resolveRefreshTriggerDistance() ||
            synchronizedLoadMoreTriggerDistance != resolveLoadMoreTriggerDistance() ||
            synchronizedRefreshHoldDistance != resolveRefreshHoldDistance() ||
            synchronizedLoadMoreHoldDistance != resolveLoadMoreHoldDistance()

    private fun synchronizeComponentDistances() {
        if (haveComponentDistancesChanged()) {
            // 在通知前更新快照; 回调再次请求布局时, 下一次只同步最终改变的距离.
            onEffectiveDistancesChanged()
        }
    }

    private fun synchronizePendingComponents() {
        if (pendingComponentSnapshots.isEmpty()) return
        // 回调可能移除或替换组件; 当前快照只覆盖已经完成此次布局的实例.
        val pending: List<View> = pendingComponentSnapshots.toList()
        for (child: View in pending) {
            if (!isComponentLayoutReady(child)) continue
            val role: ChildRole = (child.layoutParams as LayoutParams).role
            if (role == ChildRole.Content) {
                pendingComponentStates.remove(child)
                pendingComponentSnapshots.remove(child)
                deliveredComponentSnapshots.remove(child)
                continue
            }
            if (pendingComponentStates.remove(child)) {
                notifyComponentStateChanged(child, state)
            }
            if (isComponentLayoutReady(child) && (child.layoutParams as LayoutParams).role == role) {
                // 状态回调可以同步完成或切换任务, 此处取回调后的最新位移和距离.
                pendingComponentSnapshots.remove(child)
                notifyComponentOffsetChanged(child, role, onlyIfChanged = true)
            }
        }
    }

    private fun View?.asRefreshLoadComponent(): KitRefreshLoadComponent? =
        this as? KitRefreshLoadComponent

    /** 组件实际接收的布局与位移数据, 用于判定重布局后的补发是否必要. */
    private data class ComponentOffsetSnapshot(
        val role: ChildRole,
        val width: Int,
        val height: Int,
        val offset: Int,
        val triggerDistance: Int,
        val holdDistance: Int,
    )

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
         * 拖拽释放、自动加载或无障碍动作提交刷新或加载更多时回调.
         *
         * 回调时已进入对应忙碌状态; 指示器可能仍在等待布局或执行展开动画.
         * [KitRefreshLoadLayout.setActiveAction] 只同步显示, 不触发此回调.
         *
         * @param action 触发动作.
         */
        fun onRefreshLoad(action: RefreshLoadAction)
    }

    /**
     * 内容是否还能继续向指定方向滚动的外部判定.
     */
    fun interface ScrollBoundaryCallback {

        /**
         * 返回 true 表示内容仍可向指定方向滚动, 此时不应从该侧开始拖拽或展开忙碌中的指示器.
         * 返回 false 只表示已到滚动边界, 不会立即发起刷新或加载.
         * 回调中同步结束或切换任务后, 当前旧滚动处理不会继续移动指示器或提交动作.
         *
         * @param layout 当前刷新加载容器.
         * @param target 当前嵌套滚动目标; 直接触摸拖拽时为 content child, 没有内容时为 null.
         * @param direction 与 [View.canScrollVertically] 一致, -1 表示向顶部, 1 表示向底部.
         */
        fun canScrollVertically(layout: KitRefreshLoadLayout, target: View?, direction: Int): Boolean
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

        constructor(source: FrameLayout.LayoutParams) : super(source)

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
