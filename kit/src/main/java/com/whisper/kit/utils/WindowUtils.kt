package com.whisper.kit.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import androidx.window.layout.WindowMetricsCalculator

/**
 * 提供当前应用窗口的边界和方向信息.
 *
 * 窗口边界使用 px, 包含状态栏和导航栏所在区域, 不扣除任何 WindowInsets.
 * 横竖屏状态读取当前资源配置, 不根据设备自然方向或 Display rotation 推断.
 *
 * @aegis 保护公开 API、完整窗口 bounds 的快照与兼容语义、Insets 职责边界及资源方向判定规则.
 *
 * @author whisper
 * @since 2026/09/05
 */
object WindowUtils {

    /**
     * 返回当前 Activity 包含系统栏区域的完整窗口边界.
     *
     * 返回值是 [WindowMetricsCalculator.computeCurrentWindowMetrics] 所得 bounds
     * 的独立副本, 单位为 px. 状态栏、导航栏、显示缺口和 IME 等 Insets
     * 不会从边界中扣除; 需要安全内容区域时, 调用方应基于目标 View 的实时
     * WindowInsets 单独处理.
     *
     * 该结果是调用时基于系统最近一次上报窗口状态生成的快照. 旋转、分屏比例调整、
     * 自由窗口缩放或其它窗口配置变化后, 调用方必须重新查询, 不得把返回的 [Rect]
     * 作为进程级固定窗口状态长期缓存.
     *
     * API 29 及以上由 AndroidX Window 保证结果正确. API 24 至 28 采用
     * AndroidX 的 best-effort 兼容计算, 多窗口或无法可靠补偿导航栏时可能不精确.
     */
    fun getCurrentWindowBounds(activity: Activity): Rect =
        WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(activity)
            .bounds

    /**
     * 当前 Context 的资源配置明确为横屏时返回 `true`.
     *
     * 未定义或方形方向不会被视为横屏.
     */
    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * 当前 Context 的资源配置明确为竖屏时返回 `true`.
     *
     * 未定义或方形方向不会被视为竖屏.
     */
    fun isPortrait(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
}
