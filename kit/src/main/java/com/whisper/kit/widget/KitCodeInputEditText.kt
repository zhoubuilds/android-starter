package com.whisper.kit.widget

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.util.AttributeSet
import android.view.ActionMode
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.use
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import com.whisper.kit.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 使用单个原生输入连接绘制数字或 ASCII 字母短码的 EditText.
 *
 * 控件保留 EditText 的文本、粘贴、删除、TextWatcher、Autofill 和状态恢复能力,
 * 使用原生转换后的文本和 Paint 绘制分格, 未输入位置重复绘制 [getHint] 返回的提示文本.
 * 分格数量和 UTF-16 长度上限由原生 `android:maxLength` 或 [setFilters] 配置, 未配置时默认为 6.
 * 允许输入的字符由调用方通过原生 digits 或过滤器限制, 分格在 RTL 布局中仍从左向右排列.
 * 选区固定在文本末尾, 当前不提供自由选区、无障碍逐字导航或分格字符几何适配.
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
     * 单个输入格背景.
     *
     * Drawable 状态跟随控件状态, 同一个实例会依次绘制到每个输入格.
     */
    var itemBackground: Drawable? = null
        set(value) {
            if (field === value) {
                return
            }
            field?.let { previous: Drawable ->
                previous.setVisible(false, false)
                unscheduleDrawable(previous)
                previous.callback = null
            }
            field = value?.mutate()
            field?.let { background: Drawable ->
                background.callback = this
                background.setVisible(itemBackgroundVisible && isAttachedToWindow, false)
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
     * 父布局或控件自身上限限制宽度时, 实际间距由可用宽度、格宽和分格数量重新计算.
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

    private val textActionModeCallback: ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.removeItem(android.R.id.selectAll)
            return menu.hasVisibleItems()
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val hadSelectAll: Boolean = menu.findItem(android.R.id.selectAll) != null
            menu.removeItem(android.R.id.selectAll)
            return hadSelectAll
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }

    private var changingSelection: Boolean = false
    private var widthConstrained: Boolean = false
    private var initialized: Boolean = false
    private var itemBackgroundVisible: Boolean = false
    private var pasteActionMode: ActionMode? = null
    private var pasteBackHandler: PasteBackHandler? = null
    private var drawItemWidth: Float = 0f
    private var drawItemSpacing: Float = 0f
    private var drawItemLeft: Float = 0f
    private var drawItemTop: Float = 0f
    private var drawItemHeight: Float = 0f

    init {
        // 单行 TextView 可能自动安装 5000 位限制, 分格默认值只取显式样式或 XML 的 maxLength.
        val configuredMaxLength: Int = context.obtainStyledAttributes(
            attrs,
            intArrayOf(android.R.attr.maxLength),
            defStyleAttr,
            0,
        ).use { it.getInt(0, DEFAULT_MAX_LENGTH) }
        require(configuredMaxLength > 0) { "android:maxLength must be greater than 0." }
        filters = (filters.filterNot { it is InputFilter.LengthFilter } +
            InputFilter.LengthFilter(configuredMaxLength)).toTypedArray()
        trimTextToMaxLength(configuredMaxLength)

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
        // 自定义样式为空时不会继承 EditText 的触摸焦点默认值, 但仍尊重调用方显式关闭焦点.
        if (isFocusable) {
            isFocusableInTouchMode = context.obtainStyledAttributes(
                attrs,
                intArrayOf(android.R.attr.focusableInTouchMode),
                defStyleAttr,
                0,
            ).use { it.getBoolean(0, true) }
        }
        // 末尾编辑没有可用的全选操作, 保留菜单中的粘贴和其它原生命令.
        customInsertionActionModeCallback = textActionModeCallback
        customSelectionActionModeCallback = textActionModeCallback
        setOnLongClickListener { showPasteActionMode() }
        initialized = true
    }

    override fun setFilters(filters: Array<InputFilter>) {
        // TextView 构造期间也会调用此方法, 此时子类属性尚未初始化.
        if (!initialized) {
            super.setFilters(filters)
            return
        }
        val length: Int = resolveMaxLength(filters)
        val effectiveFilters: Array<InputFilter> = if (filters.any { it is InputFilter.LengthFilter }) {
            filters
        } else {
            arrayOf<InputFilter>(*filters, InputFilter.LengthFilter(length))
        }
        super.setFilters(effectiveFilters)
        trimTextToMaxLength(length)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 先建立原生 Layout, 使用其转换后的文本, 保留密码等 TransformationMethod 的生命周期.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val length: Int = resolveMaxLength()
        val horizontalPadding: Int = compoundPaddingLeft + compoundPaddingRight
        val verticalPadding: Int = compoundPaddingTop + compoundPaddingBottom
        val desiredItemWidth: Float = resolveDesiredItemWidth(layout.text)
        val desiredItemHeight: Float = resolveDesiredItemHeight()
        val drawables: Array<Drawable?> = compoundDrawables
        val rowWidth: Float = desiredItemWidth * length + itemSpacing * (length - 1)
        val drawableWidth: Int = max(drawables[1]?.bounds?.width() ?: 0, drawables[3]?.bounds?.width() ?: 0)
        val drawableHeight: Int = max(drawables[0]?.bounds?.height() ?: 0, drawables[2]?.bounds?.height() ?: 0)
        val desiredWidth: Int = ceil(horizontalPadding + max(rowWidth, drawableWidth.toFloat())).toInt()
        val desiredHeight: Int = ceil(verticalPadding + max(desiredItemHeight, drawableHeight.toFloat())).toInt()
        val minimumWidth: Int = if (minEms >= 0) {
            (minEms.toLong() * lineHeight).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            minWidth
        }
        val maximumWidth: Int = if (maxEms >= 0) {
            (maxEms.toLong() * lineHeight).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            maxWidth
        }
        val resolvedWidth: Int = resolveSizeAndState(
            desiredWidth.coerceAtMost(maximumWidth).coerceAtLeast(minimumWidth)
                .coerceAtLeast(suggestedMinimumWidth),
            widthMeasureSpec,
            0,
        )
        val resolvedHeight: Int = resolveSizeAndState(
            desiredHeight.coerceAtMost(maxHeight.takeIf { it >= 0 } ?: Int.MAX_VALUE)
                .coerceAtLeast(minHeight).coerceAtLeast(suggestedMinimumHeight),
            heightMeasureSpec,
            0,
        )
        val measuredWidth: Int = resolvedWidth and View.MEASURED_SIZE_MASK
        val measuredHeight: Int = resolvedHeight and View.MEASURED_SIZE_MASK
        val widthMode: Int = MeasureSpec.getMode(widthMeasureSpec)
        widthConstrained = widthMode == MeasureSpec.EXACTLY || measuredWidth < desiredWidth
        if (this.measuredWidth != measuredWidth || this.measuredHeight != measuredHeight) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        drawCompoundDrawables(canvas)
        // 保留原生 Layout 和 Editor 的 IME 同步, 只裁掉连续正文的实际绘制.
        canvas.withSave {
            clipRect(0, 0, 0, 0)
            super.onDraw(canvas)
        }
        // 原生文本可能自行滚动, 分格展示始终固定在控件可视区域.
        canvas.withTranslation(scrollX.toFloat(), scrollY.toFloat()) {
            drawItems(this)
        }
    }

    private fun drawItems(canvas: Canvas) {
        val length: Int = resolveMaxLength()
        val value: CharSequence = layout.text
        updateItemGeometry(value)
        val drawItemWidth: Float = this.drawItemWidth
        val drawItemSpacing: Float = this.drawItemSpacing
        val contentLeft: Float = drawItemLeft
        val itemTop: Float = drawItemTop
        val drawItemHeight: Float = this.drawItemHeight
        val itemBottom: Float = itemTop + drawItemHeight
        val emptyItemHint: CharSequence = hint ?: ""

        val background: Drawable? = itemBackground
        background?.setBounds(0, 0, drawItemWidth.roundToInt(), drawItemHeight.roundToInt())
        for (index: Int in 0 until length) {
            val itemLeft: Float = contentLeft + index * (drawItemWidth + drawItemSpacing)
            if (background != null) {
                canvas.withTranslation(itemLeft, itemTop) {
                    background.draw(this)
                }
            }
        }

        var index: Int = 0
        while (index < length) {
            val occupiedSlots: Int = if (index < value.length) {
                Character.charCount(Character.codePointAt(value, index))
            } else {
                1
            }
            val character: CharSequence = if (index < value.length && index + occupiedSlots <= length) {
                value.subSequence(index, index + occupiedSlots)
            } else {
                emptyItemHint
            }
            val itemLeft: Float = contentLeft + index * (drawItemWidth + drawItemSpacing)
            val itemRight: Float = itemLeft + drawItemWidth * occupiedSlots +
                drawItemSpacing * (occupiedSlots - 1)
            if (character.isEmpty()) {
                index += occupiedSlots
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
            index += occupiedSlots
        }
    }

    private fun updateItemGeometry(value: CharSequence) {
        val length: Int = resolveMaxLength()
        val contentLeft: Float = compoundPaddingLeft.toFloat()
        val contentTop: Float = compoundPaddingTop.toFloat()
        val contentRight: Float = (width - compoundPaddingRight).toFloat()
        val contentBottom: Float = (height - compoundPaddingBottom).toFloat()
        val contentWidth: Float = max(0f, contentRight - contentLeft)
        val contentHeight: Float = max(0f, contentBottom - contentTop)
        val fixedItemWidth: Float? = resolveFixedItemWidth()
        val drawItemWidth: Float
        val drawItemSpacing: Float

        if (widthConstrained) {
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
            drawItemWidth = fixedItemWidth ?: resolveDesiredItemWidth(value)
            drawItemSpacing = itemSpacing
        }

        val drawItemHeight: Float = resolveFixedItemHeight() ?: contentHeight
        val itemTop: Float = contentTop + (contentHeight - drawItemHeight) / 2f
        this.drawItemWidth = drawItemWidth
        this.drawItemSpacing = drawItemSpacing
        this.drawItemLeft = contentLeft
        this.drawItemTop = itemTop
        this.drawItemHeight = drawItemHeight
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

    override fun onCreateContextMenu(menu: ContextMenu) {
        super.onCreateContextMenu(menu)
        menu.removeItem(android.R.id.selectAll)
    }

    override fun onTextContextMenuItem(id: Int): Boolean =
        id != android.R.id.selectAll && super.onTextContextMenuItem(id)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pasteActionMode?.finish()
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && pasteActionMode != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                keyDispatcherState?.startTracking(event, this)
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                keyDispatcherState?.handleUpEvent(event)
                if (event.isTracking && !event.isCanceled) {
                    pasteActionMode?.finish()
                    return true
                }
            }
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // 原生节点默认提供自由选区和文本导航, 与固定末尾的编辑模式不符.
        info.removeAction(AccessibilityAction.ACTION_SET_SELECTION)
        info.removeAction(AccessibilityAction.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
        info.removeAction(AccessibilityAction.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
        info.movementGranularities = 0
        info.maxTextLength = resolveMaxLength()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info.isTextSelectable = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            info.availableExtraData = info.availableExtraData.filterNot {
                it == AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
            }
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SET_SELECTION,
        AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
        AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
        -> false
        else -> super.performAccessibilityAction(action, arguments)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun addExtraDataToAccessibilityNodeInfo(
        info: AccessibilityNodeInfo,
        extraDataKey: String,
        arguments: Bundle?,
    ) {
        // 连续文本 Layout 的字符坐标与实际分格位置不同, 不向服务返回错误的几何信息.
        if (extraDataKey == AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY) {
            info.extras.remove(extraDataKey)
            return
        }
        super.addExtraDataToAccessibilityNodeInfo(info, extraDataKey, arguments)
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        who === itemBackground || super.verifyDrawable(who)

    override fun invalidateDrawable(drawable: Drawable) {
        if (drawable === itemBackground) {
            // 同一个 Drawable 绘制在多个平移后的区域, 失效范围必须覆盖全部格子.
            invalidate()
        } else {
            super.invalidateDrawable(drawable)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        itemBackground?.let { background: Drawable ->
            val previousWidth: Int = background.intrinsicWidth
            val previousHeight: Int = background.intrinsicHeight
            if (background.isStateful && background.setState(drawableState)) {
                if (previousWidth != background.intrinsicWidth || previousHeight != background.intrinsicHeight) {
                    requestLayout()
                }
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
        val background: Drawable = itemBackground ?: return
        val length: Int = resolveMaxLength()
        if (!initialized || width <= 0 || height <= 0) {
            return
        }
        updateItemGeometry(layout?.text ?: text ?: "")
        if (drawItemWidth <= 0f || drawItemHeight <= 0f || length <= 0) {
            return
        }
        val step: Float = drawItemWidth + drawItemSpacing
        var index: Int = 0
        var distance: Float = Float.POSITIVE_INFINITY
        for (candidate: Int in 0 until length) {
            val candidateDistance: Float = abs(x - (drawItemLeft + candidate * step + drawItemWidth / 2f))
            if (candidateDistance < distance) {
                index = candidate
                distance = candidateDistance
            }
        }
        val itemLeft: Float = drawItemLeft + index * step
        background.setHotspot(
            (x - itemLeft).coerceIn(0f, drawItemWidth),
            (y - drawItemTop).coerceIn(0f, drawItemHeight),
        )
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        itemBackground?.let { background: Drawable ->
            val previousWidth: Int = background.intrinsicWidth
            val previousHeight: Int = background.intrinsicHeight
            background.layoutDirection = layoutDirection
            if (previousWidth != background.intrinsicWidth || previousHeight != background.intrinsicHeight) {
                requestLayout()
            }
            invalidate()
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        itemBackgroundVisible = isVisible
        itemBackground?.setVisible(isVisible && isAttachedToWindow, false)
        if (!isVisible) {
            pasteActionMode?.finish()
        }
    }

    override fun onDetachedFromWindow() {
        pasteActionMode?.finish()
        itemBackgroundVisible = false
        itemBackground?.let { background: Drawable ->
            background.setVisible(false, false)
            unscheduleDrawable(background)
        }
        super.onDetachedFromWindow()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused) {
            pasteActionMode?.finish()
        }
    }

    private fun showPasteActionMode(): Boolean {
        if (!isEnabled || keyListener == null || !isAttachedToWindow || !requestFocus()) {
            return false
        }
        val clipboard: ClipboardManager = context.getSystemService(ClipboardManager::class.java)
        if (!clipboard.hasPrimaryClip()) {
            return false
        }
        pasteActionMode?.let { mode: ActionMode ->
            mode.invalidate()
            return true
        }
        val callback: ActionMode.Callback? = customInsertionActionModeCallback
        var destroyedDuringCreate: Boolean = false
        val createdMode: ActionMode? = startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, android.R.id.paste, Menu.NONE, android.R.string.paste)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                return callback?.onCreateActionMode(mode, menu) ?: true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.findItem(android.R.id.paste)?.isEnabled = isEnabled && keyListener != null &&
                    clipboard.hasPrimaryClip()
                return callback?.onPrepareActionMode(mode, menu) ?: false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (callback?.onActionItemClicked(mode, item) == true) {
                    return true
                }
                if (item.itemId != android.R.id.paste || !isEnabled || keyListener == null) {
                    return false
                }
                val handled: Boolean = onTextContextMenuItem(item.itemId)
                if (handled) {
                    mode.finish()
                }
                return handled
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                if (pasteActionMode === mode) {
                    pasteActionMode = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pasteBackHandler?.unregister()
                        pasteBackHandler = null
                    }
                } else {
                    // 调用方可能在创建回调中同步结束菜单, 此时外层尚未接收返回值.
                    destroyedDuringCreate = true
                }
                callback?.onDestroyActionMode(mode)
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                outRect.set(
                    compoundPaddingLeft,
                    compoundPaddingTop,
                    width - compoundPaddingRight,
                    height - compoundPaddingBottom,
                )
            }
        }, ActionMode.TYPE_FLOATING)
        if (destroyedDuringCreate || createdMode == null) {
            return false
        }
        pasteActionMode = createdMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pasteBackHandler = PasteBackHandler(this) { pasteActionMode?.finish() }
        }
        return pasteActionMode != null
    }

    private fun drawCompoundDrawables(canvas: Canvas) {
        val drawables: Array<Drawable?> = compoundDrawables
        val contentWidth: Int = width - compoundPaddingLeft - compoundPaddingRight
        val contentHeight: Int = height - compoundPaddingTop - compoundPaddingBottom
        val left: Drawable? = drawables[0]
        val right: Drawable? = drawables[2]
        val top: Drawable? = drawables[1]
        val bottom: Drawable? = drawables[3]
        left?.let { drawable: Drawable ->
            canvas.withTranslation(
                scrollX + paddingLeft.toFloat(),
                (scrollY + compoundPaddingTop + (contentHeight - drawable.bounds.height()) / 2).toFloat(),
            ) { drawable.draw(this) }
        }
        right?.let { drawable: Drawable ->
            canvas.withTranslation(
                scrollX + width - paddingRight - drawable.bounds.width().toFloat(),
                (scrollY + compoundPaddingTop + (contentHeight - drawable.bounds.height()) / 2).toFloat(),
            ) { drawable.draw(this) }
        }
        top?.let { drawable: Drawable ->
            canvas.withTranslation(
                (scrollX + compoundPaddingLeft + (contentWidth - drawable.bounds.width()) / 2).toFloat(),
                scrollY + paddingTop.toFloat(),
            ) { drawable.draw(this) }
        }
        bottom?.let { drawable: Drawable ->
            canvas.withTranslation(
                (scrollX + compoundPaddingLeft + (contentWidth - drawable.bounds.width()) / 2).toFloat(),
                scrollY + height - paddingBottom - drawable.bounds.height().toFloat(),
            ) { drawable.draw(this) }
        }
    }

    private fun resolveDesiredItemWidth(value: CharSequence): Float {
        resolveFixedItemWidth()?.let { return it }
        syncCharacterPaint()
        characterPaint.typeface = hintTypeface
        var measuredTextWidth: Float = characterPaint.measureText(hint?.toString().orEmpty())
        syncCharacterPaint()
        var index: Int = 0
        while (index < value.length) {
            val occupiedSlots: Int = Character.charCount(Character.codePointAt(value, index))
            measuredTextWidth = max(
                measuredTextWidth,
                characterPaint.measureText(value, index, index + occupiedSlots) / occupiedSlots,
            )
            index += occupiedSlots
        }
        return max(MINIMUM_DRAW_SIZE_PX, max(textSize, measuredTextWidth))
    }

    private fun resolveDesiredItemHeight(): Float {
        resolveFixedItemHeight()?.let { return it }
        syncCharacterPaint()
        val textMetrics: Paint.FontMetrics = characterPaint.fontMetrics
        var desiredHeight: Float = textMetrics.descent - textMetrics.ascent
        if (!hint.isNullOrEmpty()) {
            characterPaint.typeface = hintTypeface
            val hintMetrics: Paint.FontMetrics = characterPaint.fontMetrics
            desiredHeight = max(desiredHeight, hintMetrics.descent - hintMetrics.ascent)
        }
        return max(MINIMUM_DRAW_SIZE_PX, desiredHeight)
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
        characterPaint.set(paint)
        characterPaint.textAlign = Paint.Align.CENTER
    }

    private fun resolveMaxLength(filters: Array<InputFilter> = getFilters()): Int {
        val length: Int = filters.filterIsInstance<InputFilter.LengthFilter>()
            .minOfOrNull { filter: InputFilter.LengthFilter -> filter.max } ?: DEFAULT_MAX_LENGTH
        require(length > 0) { "android:maxLength must be greater than 0." }
        return length
    }

    private fun trimTextToMaxLength(length: Int) {
        val currentText = text ?: return
        if (currentText.length > length) {
            val end: Int = if (Character.isHighSurrogate(currentText[length - 1]) &&
                Character.isLowSurrogate(currentText[length])) {
                length - 1
            } else {
                length
            }
            currentText.delete(end, currentText.length)
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

    /**
     * 自建浮动菜单不由 TextView.Editor 管理, 需要单独接入新版本系统返回调度.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private class PasteBackHandler(view: View, onBack: () -> Unit) {

        private val dispatcher: OnBackInvokedDispatcher? = view.findOnBackInvokedDispatcher()
        private val callback: OnBackInvokedCallback = OnBackInvokedCallback(onBack)

        init {
            dispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        }

        fun unregister() {
            dispatcher?.unregisterOnBackInvokedCallback(callback)
        }
    }

    private companion object {

        private const val DEFAULT_MAX_LENGTH: Int = 6
        private const val MINIMUM_DRAW_SIZE_PX: Float = 1f
    }
}
