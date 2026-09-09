package com.whisper.kit.widget

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 验证线条默认展示、状态色和运行时属性在真实硬件加速窗口中的绘制结果.
 *
 * @author whisper
 * @since 2026/09/09
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class KitLineViewInstrumentedTest {

    private lateinit var activity: KitLineViewTestActivity

    @Before
    fun launchActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, KitLineViewTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as KitLineViewTestActivity
        var ready: Boolean = false
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            onMainThread {
                ready = activity.line.hasWindowFocus() && activity.line.isLaidOut
            }
            if (!ready) {
                SystemClock.sleep(50L)
            }
        }
        assertTrue("The test window must be focused and laid out.", ready)
        onMainThread {
            assertTrue("The line must use hardware accelerated rendering.", activity.line.isHardwareAccelerated)
            assertEquals(120, activity.line.width)
            assertEquals(120, activity.line.height)
        }
    }

    @After
    fun finishActivity() {
        if (::activity.isInitialized) {
            onMainThread { activity.finish() }
        }
    }

    @Test
    fun defaultLine_whenMounted_drawsVisibleSolidLine() {
        var expectedColor: Int = Color.WHITE
        onMainThread {
            val colors: ColorStateList = activity.line.lineColors
            expectedColor = ColorUtils.compositeColors(
                colors.getColorForState(activity.line.drawableState, colors.defaultColor),
                Color.WHITE,
            )
        }
        assertTrue("The default color must be visible on the light test background.", expectedColor != Color.WHITE)
        awaitSolidColor(expectedColor)
    }

    @Test
    fun lineColors_whenReplacedAndViewStateChanges_repaintsWindow() {
        onMainThread {
            activity.line.lineColors = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_selected),
                    intArrayOf(),
                ),
                intArrayOf(Color.RED, Color.BLUE, Color.GREEN),
            )
        }
        awaitSolidColor(Color.GREEN)

        onMainThread { activity.line.isEnabled = false }
        awaitSolidColor(Color.RED)

        onMainThread { activity.line.isEnabled = true }
        awaitSolidColor(Color.GREEN)

        onMainThread { activity.line.isSelected = true }
        awaitSolidColor(Color.BLUE)

        onMainThread { activity.line.setLineColor(Color.RED) }
        awaitSolidColor(Color.RED)
    }

    @Test
    fun linePattern_whenLengthsAndDirectionChange_repaintsWindow() {
        onMainThread { activity.line.setLineColor(Color.BLUE) }
        awaitSolidColor(Color.BLUE)

        onMainThread {
            activity.line.dashWidth = 4f
            activity.line.gapWidth = 4f
        }
        awaitWindowPixels("A gap must separate the horizontal dashes.") { bitmap ->
            bitmap.getPixel(2, 60) == Color.BLUE && bitmap.getPixel(6, 60) == Color.WHITE
        }

        onMainThread { activity.line.dashWidth = 8f }
        awaitWindowPixels("Changing dash length must move the gap.") { bitmap ->
            bitmap.getPixel(6, 60) == Color.BLUE && bitmap.getPixel(10, 60) == Color.WHITE
        }

        onMainThread { activity.line.dotWidth = 4f }
        awaitWindowPixels("Setting dot length must produce the alternating pattern.") { bitmap ->
            bitmap.getPixel(6, 60) == Color.WHITE && bitmap.getPixel(10, 60) == Color.BLUE &&
                bitmap.getPixel(18, 60) == Color.WHITE
        }

        onMainThread { activity.line.orientation = KitLineView.VERTICAL }
        awaitWindowPixels("Changing orientation must rotate the pattern within the square view.") { bitmap ->
            bitmap.getPixel(6, 2) == Color.BLUE && bitmap.getPixel(2, 6) == Color.WHITE &&
                bitmap.getPixel(60, 10) == Color.BLUE && bitmap.getPixel(60, 18) == Color.WHITE
        }

        onMainThread { activity.line.gapWidth = 0f }
        awaitSolidColor(Color.BLUE)
    }

    @Test
    fun linePattern_whenPaddingChanges_repositionsDashesAndClearsOldBounds() {
        onMainThread {
            activity.line.setLineColor(Color.BLUE)
            activity.line.dashWidth = 4f
            activity.line.gapWidth = 4f
        }
        awaitWindowPixels("The initial horizontal dashes must be visible.") { bitmap ->
            bitmap.getPixel(2, 60) == Color.BLUE && bitmap.getPixel(6, 60) == Color.WHITE
        }

        onMainThread { activity.line.setPadding(4, 8, 12, 16) }
        awaitWindowPixels("Padding must move the dashes and clear the previous content bounds.") { bitmap ->
            bitmap.getPixel(2, 60) == Color.WHITE && bitmap.getPixel(6, 60) == Color.BLUE &&
                bitmap.getPixel(10, 60) == Color.WHITE && bitmap.getPixel(6, 4) == Color.WHITE &&
                bitmap.getPixel(6, 110) == Color.WHITE && bitmap.getPixel(118, 60) == Color.WHITE
        }

        onMainThread { activity.line.orientation = KitLineView.VERTICAL }
        awaitWindowPixels("The padded vertical dashes must replace the horizontal pattern.") { bitmap ->
            bitmap.getPixel(60, 10) == Color.BLUE && bitmap.getPixel(60, 14) == Color.WHITE &&
                bitmap.getPixel(2, 10) == Color.WHITE && bitmap.getPixel(110, 10) == Color.WHITE
        }
    }

    private fun awaitSolidColor(color: Int) {
        awaitWindowPixels("The line must repaint with color $color.") { bitmap ->
            bitmap.getPixel(2, 60) == color && bitmap.getPixel(60, 60) == color &&
                bitmap.getPixel(110, 60) == color
        }
    }

    private fun awaitWindowPixels(message: String, matches: (Bitmap) -> Boolean) {
        val deadline: Long = SystemClock.uptimeMillis() + 5_000L
        val bitmap: Bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        try {
            do {
                val copied: CountDownLatch = CountDownLatch(1)
                var result: Int = PixelCopy.ERROR_UNKNOWN
                onMainThread {
                    val location: IntArray = IntArray(2)
                    activity.line.getLocationInWindow(location)
                    val bounds: Rect = Rect(location[0], location[1], location[0] + 120, location[1] + 120)
                    PixelCopy.request(activity.window, bounds, bitmap, { copyResult ->
                        result = copyResult
                        copied.countDown()
                    }, Handler(Looper.getMainLooper()))
                }
                assertTrue("The window pixel copy did not complete in time.", copied.await(2, TimeUnit.SECONDS))
                assertTrue(
                    "The window pixel copy failed with result $result.",
                    result == PixelCopy.SUCCESS || result == PixelCopy.ERROR_SOURCE_NO_DATA,
                )
                if (result == PixelCopy.SUCCESS && matches(bitmap)) {
                    return
                }
                // PixelCopy 可能取得上一个已提交帧; 只等待自然重绘, 不代替属性 setter 主动失效 View.
                SystemClock.sleep(32L)
            } while (SystemClock.uptimeMillis() < deadline)
            throw AssertionError(message)
        } finally {
            bitmap.recycle()
        }
    }

    private fun onMainThread(block: () -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}
