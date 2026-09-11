package com.whisper.kit.widget

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.SeekBar
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.whisper.kit.R
import kotlin.math.abs

/** 范围选择器单个端点的无障碍快照, 数值和可用范围保持整数精度. */
internal data class KitRangeAccessibilityNode(
    val label: CharSequence,
    val value: Int,
    val min: Int,
    val max: Int,
    val bounds: Rect,
    val enabled: Boolean,
)

/** 将范围选择器的两个端点映射为可独立聚焦和按步长调整的虚拟节点. */
internal class KitRangeAccessibilityHelper(
    private val host: View,
    private val nodeFor: (Int) -> KitRangeAccessibilityNode,
    private val adjust: (Int, Boolean) -> Boolean,
    private val onKeyboardFocusChanged: () -> Unit,
) : ExploreByTouchHelper(host) {

    companion object {
        const val START_ID: Int = 1
        const val END_ID: Int = 2
    }

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val startBounds = nodeFor(START_ID).bounds
        val endBounds = nodeFor(END_ID).bounds
        val hitsStart = contains(startBounds, x, y)
        val hitsEnd = contains(endBounds, x, y)
        if (!hitsStart) {
            return if (hitsEnd) END_ID else INVALID_ID
        }
        if (!hitsEnd) {
            return START_ID
        }

        val startDistance = abs(startBounds.exactCenterX() - x)
        val endDistance = abs(endBounds.exactCenterX() - x)
        return when {
            startDistance < endDistance -> START_ID
            endDistance < startDistance -> END_ID
            accessibilityFocusedVirtualViewId == END_ID -> END_ID
            accessibilityFocusedVirtualViewId == START_ID -> START_ID
            keyboardFocusedVirtualViewId == END_ID -> END_ID
            else -> START_ID
        }
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        virtualViewIds.add(START_ID)
        virtualViewIds.add(END_ID)
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat,
    ) {
        val model = nodeFor(virtualViewId)
        node.className = SeekBar::class.java.name
        node.contentDescription = host.context.getString(
            R.string.kit_range_accessibility_description,
            model.label,
            model.value,
            model.min,
            model.max,
        )
        // CustomView 1.1.0 强制要求父坐标, 并据此计算屏幕边界和可见性.
        @Suppress("DEPRECATION")
        node.setBoundsInParent(model.bounds)
        node.isEnabled = model.enabled
        node.isFocusable = true
        node.isScrollable = model.enabled && model.min < model.max
        // RangeInfo 和 SET_PROGRESS 以 Float 传值, 这里用整数文本和离散动作避免大数值失真.
        if (model.enabled && model.value < model.max) {
            node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
        }
        if (model.enabled && model.value > model.min) {
            node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?,
    ): Boolean {
        if (virtualViewId != START_ID && virtualViewId != END_ID) {
            return false
        }
        val model = nodeFor(virtualViewId)
        if (!model.enabled) {
            return false
        }
        return when (action) {
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> {
                model.value < model.max && adjust(virtualViewId, true)
            }

            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> {
                model.value > model.min && adjust(virtualViewId, false)
            }

            else -> false
        }
    }

    override fun onVirtualViewKeyboardFocusChanged(virtualViewId: Int, hasFocus: Boolean) {
        onKeyboardFocusChanged()
    }

    /** 选区变化也会改变另一个端点的合法范围, 因此统一刷新两个节点. */
    fun invalidateNodes() {
        invalidateVirtualView(START_ID, AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION)
        invalidateVirtualView(END_ID, AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION)
    }

    private fun contains(bounds: Rect, x: Float, y: Float): Boolean {
        return x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom
    }
}
