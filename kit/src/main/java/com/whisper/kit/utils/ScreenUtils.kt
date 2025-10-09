package com.whisper.kit.utils

import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics


/**
 *
 * @author whisper
 * @since 2025/10/9
 */
object ScreenUtils {

    /**
     * 屏幕尺寸
     */
    fun getScreenSize(context: Context): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * 应用窗口尺寸
     */
    fun getAppWindowSize(context: Context): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm: WindowManager = context.getSystemService(WindowManager::class.java)
            val metrics: WindowMetrics = wm.currentWindowMetrics
            val insets: Insets = metrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val bounds: Rect = metrics.bounds
            Pair(
                bounds.width() - insets.left - insets.right,
                bounds.height() - insets.top - insets.bottom
            )
        } else {
            val wm: WindowManager =
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                    ?: return 0 to 0
            val outMetrics: DisplayMetrics = DisplayMetrics()
            @Suppress("Deprecation")
            wm.defaultDisplay.getMetrics(outMetrics)
            Pair(outMetrics.widthPixels, outMetrics.heightPixels)
        }
    }

    /**
     * 是否是横屏
     */
    fun isLandscape(context: Context): Boolean {
        val rotation: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.rotation
        } else {
            @Suppress("Deprecation")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    /**
     * 是否是竖屏
     */
    fun isPortrait(context: Context): Boolean = !isLandscape(context)

}