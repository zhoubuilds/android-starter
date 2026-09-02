package com.whisper.kit.extension

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

/**
 * 验证尺寸扩展使用当前 Context 的显示和字体缩放配置.
 *
 * @author whisper
 * @since 2026/09/02
 */
@RunWith(RobolectricTestRunner::class)
class DimensionExtensionsTest {

    @Test
    fun dp_whenContextsHaveDifferentDensity_usesCurrentContextDensity() {
        val mdpiContext: Context = createContext(
            densityDpi = DisplayMetrics.DENSITY_DEFAULT,
            fontScale = 1f,
        )
        val xhdpiContext: Context = createContext(
            densityDpi = DisplayMetrics.DENSITY_XHIGH,
            fontScale = 1f,
        )

        val mdpiValue: Float = with(mdpiContext) { 10.dp }
        val xhdpiValue: Float = with(xhdpiContext) { 10.dp }

        assertEquals(10f, mdpiValue, DELTA)
        assertEquals(20f, xhdpiValue, DELTA)
    }

    @Test
    fun sp_whenContextsHaveDifferentFontScale_usesCurrentContextFontScale() {
        val normalScaleContext: Context = createContext(
            densityDpi = DisplayMetrics.DENSITY_DEFAULT,
            fontScale = 1f,
        )
        val largeScaleContext: Context = createContext(
            densityDpi = DisplayMetrics.DENSITY_DEFAULT,
            fontScale = 1.5f,
        )

        val normalScaleValue: Float = with(normalScaleContext) { 10.sp }
        val largeScaleValue: Float = with(largeScaleContext) { 10.sp }

        assertEquals(10f, normalScaleValue, DELTA)
        assertEquals(15f, largeScaleValue, DELTA)
    }

    @Test
    fun dimensions_whenAccessedFromFragmentMembers_useAttachedContext() {
        val controller: ActivityController<FragmentActivity> = Robolectric.buildActivity(
            FragmentActivity::class.java,
        ).setup()
        val fragment = TestFragment()
        controller.get().supportFragmentManager.beginTransaction()
            .add(fragment, "dimension-test")
            .commitNow()
        val context: Context = fragment.requireContext()

        assertEquals(expectedDp(context), fragment.dpValue, DELTA)
        assertEquals(expectedSp(context), fragment.spValue, DELTA)

        controller.pause().stop().destroy()
    }

    @Test
    fun dimensions_whenAccessedFromViewMembers_useViewContext() {
        val context: Context = createContext(
            densityDpi = DisplayMetrics.DENSITY_XHIGH,
            fontScale = 1.5f,
        )
        val view = TestView(context)

        assertEquals(20f, view.dpValue, DELTA)
        assertEquals(30f, view.spValue, DELTA)
    }

    @Test
    fun dimensions_whenAccessedFromDialogMembers_useDialogContext() {
        val context: Context = createContext(
            densityDpi = DisplayMetrics.DENSITY_XHIGH,
            fontScale = 1.5f,
        )
        val dialog = TestDialog(context)

        assertEquals(expectedDp(dialog.context), dialog.dpValue, DELTA)
        assertEquals(expectedSp(dialog.context), dialog.spValue, DELTA)
    }

    @Test
    fun dimensions_whenFragmentAndViewScopesAreNested_explicitContextUsesViewContext() {
        val viewContext: Context = createContext(
            densityDpi = DisplayMetrics.DENSITY_XHIGH,
            fontScale = 1.5f,
        )
        val values: Pair<Float, Float> = TestFragment().dimensionsFrom(View(viewContext))

        assertEquals(20f, values.first, DELTA)
        assertEquals(30f, values.second, DELTA)
    }

    private fun createContext(
        densityDpi: Int,
        fontScale: Float,
    ): Context {
        val baseContext: Context = RuntimeEnvironment.getApplication()
        val configuration = Configuration(baseContext.resources.configuration).apply {
            this.densityDpi = densityDpi
            this.fontScale = fontScale
        }
        return baseContext.createConfigurationContext(configuration)
    }

    private fun expectedDp(context: Context): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DIMENSION_VALUE,
            context.resources.displayMetrics,
        )
    }

    private fun expectedSp(context: Context): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            DIMENSION_VALUE,
            context.resources.displayMetrics,
        )
    }

    class TestFragment : Fragment() {

        val dpValue: Float
            get() = DIMENSION_VALUE.dp

        val spValue: Float
            get() = DIMENSION_VALUE.sp

        fun dimensionsFrom(view: View): Pair<Float, Float> = with(view) {
            DIMENSION_VALUE.dp(context) to DIMENSION_VALUE.sp(context)
        }
    }

    private class TestView(context: Context) : View(context) {

        val dpValue: Float
            get() = DIMENSION_VALUE.dp

        val spValue: Float
            get() = DIMENSION_VALUE.sp
    }

    private class TestDialog(context: Context) : Dialog(context) {

        val dpValue: Float
            get() = DIMENSION_VALUE.dp

        val spValue: Float
            get() = DIMENSION_VALUE.sp
    }

    private companion object {
        const val DELTA: Float = 0.001f
        const val DIMENSION_VALUE: Float = 10f
    }
}
