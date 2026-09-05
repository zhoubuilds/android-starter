package com.whisper.kit.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import androidx.window.layout.WindowMetricsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证完整窗口边界和资源配置方向的查询契约.
 *
 * @author whisper
 * @since 2026/09/05
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class WindowUtilsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun getCurrentWindowBounds_whenCalled_returnsCompleteCalculatorBoundsCopy() {
        val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val calculatorBounds: Rect = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(activity)
            .bounds

        val actualBounds: Rect = WindowUtils.getCurrentWindowBounds(activity)

        assertEquals(calculatorBounds, actualBounds)
        actualBounds.setEmpty()
        assertEquals(calculatorBounds, WindowUtils.getCurrentWindowBounds(activity))
    }

    @Test
    fun orientationPredicates_whenConfigurationIsLandscape_returnLandscapeOnly() {
        val landscapeContext: Context = context.withOrientation(
            Configuration.ORIENTATION_LANDSCAPE,
        )

        assertTrue(WindowUtils.isLandscape(landscapeContext))
        assertFalse(WindowUtils.isPortrait(landscapeContext))
    }

    @Test
    fun orientationPredicates_whenConfigurationIsPortrait_returnPortraitOnly() {
        val portraitContext: Context = context.withOrientation(
            Configuration.ORIENTATION_PORTRAIT,
        )

        assertFalse(WindowUtils.isLandscape(portraitContext))
        assertTrue(WindowUtils.isPortrait(portraitContext))
    }

    @Test
    @Suppress("DEPRECATION")
    fun orientationPredicates_whenConfigurationIsSquare_returnFalse() {
        val squareContext: Context = context.withOrientation(
            Configuration.ORIENTATION_SQUARE,
        )

        assertFalse(WindowUtils.isLandscape(squareContext))
        assertFalse(WindowUtils.isPortrait(squareContext))
    }

    private fun Context.withOrientation(orientation: Int): Context {
        val configuration: Configuration = Configuration(resources.configuration).apply {
            this.orientation = orientation
        }
        return createConfigurationContext(configuration)
    }
}
