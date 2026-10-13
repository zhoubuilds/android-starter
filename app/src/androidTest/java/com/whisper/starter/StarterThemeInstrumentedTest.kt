package com.whisper.starter

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.whisper.starter.ui.activity.MainActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR
import com.whisper.feature.function.R as FunctionR
import com.whisper.feature.settings.R as SettingsR
import com.whisper.foundation.R as FoundationR

/**
 * 验证资源合并后的应用日夜主题, 包含文字状态和真实 Activity 接入.
 *
 * @author whisper
 * @since 2026/09/11
 */
@RunWith(AndroidJUnit4::class)
class StarterThemeInstrumentedTest {
    @Test
    fun semanticColors_inBothModes_keepContentReadable() {
        val pairs = listOf(
            AppCompatR.attr.colorPrimary to MaterialR.attr.colorOnPrimary,
            MaterialR.attr.colorPrimaryContainer to MaterialR.attr.colorOnPrimaryContainer,
            MaterialR.attr.colorSecondary to MaterialR.attr.colorOnSecondary,
            MaterialR.attr.colorSecondaryContainer to MaterialR.attr.colorOnSecondaryContainer,
            MaterialR.attr.colorTertiary to MaterialR.attr.colorOnTertiary,
            MaterialR.attr.colorTertiaryContainer to MaterialR.attr.colorOnTertiaryContainer,
            AppCompatR.attr.colorError to MaterialR.attr.colorOnError,
            MaterialR.attr.colorErrorContainer to MaterialR.attr.colorOnErrorContainer,
            MaterialR.attr.colorSurface to MaterialR.attr.colorOnSurface,
            MaterialR.attr.colorSurfaceContainerHighest to MaterialR.attr.colorOnSurface,
            MaterialR.attr.colorSurface to MaterialR.attr.colorOnSurfaceVariant,
            MaterialR.attr.colorSurfaceInverse to MaterialR.attr.colorOnSurfaceInverse,
        )
        for (dark in listOf(false, true)) {
            val context = themedContext(dark)
            for ((background, foreground) in pairs) {
                val contrast = ColorUtils.calculateContrast(color(context, foreground), color(context, background))
                assertTrue("Insufficient contrast for attribute $foreground in dark=$dark: $contrast", contrast >= 4.5)
            }
        }
        assertNotEquals(color(themedContext(false), AppCompatR.attr.colorPrimary), color(themedContext(true), AppCompatR.attr.colorPrimary))
        assertEquals(color(themedContext(false), MaterialR.attr.colorPrimaryFixed), color(themedContext(true), MaterialR.attr.colorPrimaryFixed))
    }

    @Test
    fun platformText_inBothModes_usesSemanticColorsAndDisabledOpacity() {
        val enabled = intArrayOf(android.R.attr.state_enabled)
        val disabled = intArrayOf(-android.R.attr.state_enabled)
        for (dark in listOf(false, true)) {
            val context = themedContext(dark)
            val primary = colors(context, android.R.attr.textColorPrimary)
            val secondary = colors(context, android.R.attr.textColorSecondary)
            assertEquals(color(context, MaterialR.attr.colorOnSurface), primary.getColorForState(enabled, 0))
            assertEquals(color(context, MaterialR.attr.colorOnSurfaceVariant), secondary.getColorForState(enabled, 0))
            assertEquals(97, Color.alpha(primary.getColorForState(disabled, 0)))
            assertEquals(97, Color.alpha(secondary.getColorForState(disabled, 0)))
            assertEquals(
                color(themedContext(!dark), MaterialR.attr.colorOnSurfaceVariant),
                color(context, android.R.attr.textColorSecondaryInverse),
            )
        }
    }

    /**
     * 验证主题使用打包字体及上游原始字宽: 拉丁字符为 600 units, 中文为 1000 units.
     *
     * 五个拉丁字符与三个中文等宽, 用于防止再次把中文强行拉伸到两个英文字符的宽度.
     */
    @Test
    fun materialTypography_usesBundledStarterMono() {
        val context = themedContext(dark = false)
        val typography = listOf(
            MaterialR.attr.textAppearanceDisplayLarge to FoundationR.style.Foundation_TextAppearance_DisplayLarge,
            MaterialR.attr.textAppearanceDisplayMedium to FoundationR.style.Foundation_TextAppearance_DisplayMedium,
            MaterialR.attr.textAppearanceDisplaySmall to FoundationR.style.Foundation_TextAppearance_DisplaySmall,
            MaterialR.attr.textAppearanceHeadlineLarge to FoundationR.style.Foundation_TextAppearance_HeadlineLarge,
            MaterialR.attr.textAppearanceHeadlineMedium to FoundationR.style.Foundation_TextAppearance_HeadlineMedium,
            MaterialR.attr.textAppearanceHeadlineSmall to FoundationR.style.Foundation_TextAppearance_HeadlineSmall,
            MaterialR.attr.textAppearanceTitleLarge to FoundationR.style.Foundation_TextAppearance_TitleLarge,
            MaterialR.attr.textAppearanceTitleMedium to FoundationR.style.Foundation_TextAppearance_TitleMedium,
            MaterialR.attr.textAppearanceTitleSmall to FoundationR.style.Foundation_TextAppearance_TitleSmall,
            MaterialR.attr.textAppearanceBodyLarge to FoundationR.style.Foundation_TextAppearance_BodyLarge,
            MaterialR.attr.textAppearanceBodyMedium to FoundationR.style.Foundation_TextAppearance_BodyMedium,
            MaterialR.attr.textAppearanceBodySmall to FoundationR.style.Foundation_TextAppearance_BodySmall,
            MaterialR.attr.textAppearanceLabelLarge to FoundationR.style.Foundation_TextAppearance_LabelLarge,
            MaterialR.attr.textAppearanceLabelMedium to FoundationR.style.Foundation_TextAppearance_LabelMedium,
            MaterialR.attr.textAppearanceLabelSmall to FoundationR.style.Foundation_TextAppearance_LabelSmall,
            MaterialR.attr.textAppearanceDisplayLargeEmphasized to
                FoundationR.style.Foundation_TextAppearance_DisplayLarge_Emphasized,
            MaterialR.attr.textAppearanceDisplayMediumEmphasized to
                FoundationR.style.Foundation_TextAppearance_DisplayMedium_Emphasized,
            MaterialR.attr.textAppearanceDisplaySmallEmphasized to
                FoundationR.style.Foundation_TextAppearance_DisplaySmall_Emphasized,
            MaterialR.attr.textAppearanceHeadlineLargeEmphasized to
                FoundationR.style.Foundation_TextAppearance_HeadlineLarge_Emphasized,
            MaterialR.attr.textAppearanceHeadlineMediumEmphasized to
                FoundationR.style.Foundation_TextAppearance_HeadlineMedium_Emphasized,
            MaterialR.attr.textAppearanceHeadlineSmallEmphasized to
                FoundationR.style.Foundation_TextAppearance_HeadlineSmall_Emphasized,
            MaterialR.attr.textAppearanceTitleLargeEmphasized to
                FoundationR.style.Foundation_TextAppearance_TitleLarge_Emphasized,
            MaterialR.attr.textAppearanceTitleMediumEmphasized to
                FoundationR.style.Foundation_TextAppearance_TitleMedium_Emphasized,
            MaterialR.attr.textAppearanceTitleSmallEmphasized to
                FoundationR.style.Foundation_TextAppearance_TitleSmall_Emphasized,
            MaterialR.attr.textAppearanceBodyLargeEmphasized to
                FoundationR.style.Foundation_TextAppearance_BodyLarge_Emphasized,
            MaterialR.attr.textAppearanceBodyMediumEmphasized to
                FoundationR.style.Foundation_TextAppearance_BodyMedium_Emphasized,
            MaterialR.attr.textAppearanceBodySmallEmphasized to
                FoundationR.style.Foundation_TextAppearance_BodySmall_Emphasized,
            MaterialR.attr.textAppearanceLabelLargeEmphasized to
                FoundationR.style.Foundation_TextAppearance_LabelLarge_Emphasized,
            MaterialR.attr.textAppearanceLabelMediumEmphasized to
                FoundationR.style.Foundation_TextAppearance_LabelMedium_Emphasized,
            MaterialR.attr.textAppearanceLabelSmallEmphasized to
                FoundationR.style.Foundation_TextAppearance_LabelSmall_Emphasized,
        )

        context.obtainStyledAttributes(
            intArrayOf(android.R.attr.fontFamily, AppCompatR.attr.fontFamily),
        ).use { attributes ->
            assertEquals(FoundationR.font.foundation_starter_mono, attributes.getResourceId(0, 0))
            assertEquals(FoundationR.font.foundation_starter_mono, attributes.getResourceId(1, 0))
        }
        val typeface = requireNotNull(ResourcesCompat.getFont(context, FoundationR.font.foundation_starter_mono)) {
            "Starter Mono font could not be loaded"
        }
        val semiBoldTypeface = requireNotNull(
            ResourcesCompat.getFont(context, FoundationR.font.foundation_starter_mono_semibold),
        ) {
            "Starter Mono SemiBold font could not be loaded"
        }
        val paint = Paint().apply {
            this.typeface = typeface
            textSize = 100f
        }
        assertTrue("Starter Mono does not contain the expected CJK glyph", paint.hasGlyph("中"))
        assertEquals(paint.measureText("AAAAA"), paint.measureText("中中中"), 0.01f)
        paint.typeface = semiBoldTypeface
        assertTrue("Starter Mono SemiBold does not contain the expected CJK glyph", paint.hasGlyph("中"))
        assertEquals(paint.measureText("AAAAA"), paint.measureText("中中中"), 0.01f)

        context.obtainStyledAttributes(
            FoundationR.style.Foundation_TextAppearance_TopLevelTitle,
            intArrayOf(android.R.attr.fontFamily, AppCompatR.attr.fontFamily),
        ).use { attributes ->
            assertEquals(FoundationR.font.foundation_starter_mono_semibold, attributes.getResourceId(0, 0))
            assertEquals(FoundationR.font.foundation_starter_mono_semibold, attributes.getResourceId(1, 0))
        }

        for ((attribute, expectedStyle) in typography) {
            val value = TypedValue()
            assertTrue(
                "Missing typography attribute: $attribute",
                context.theme.resolveAttribute(attribute, value, true),
            )
            assertEquals("Unexpected style for typography attribute: $attribute", expectedStyle, value.resourceId)
            context.obtainStyledAttributes(
                expectedStyle,
                intArrayOf(android.R.attr.fontFamily, AppCompatR.attr.fontFamily, android.R.attr.letterSpacing),
            ).use { attributes ->
                assertEquals(FoundationR.font.foundation_starter_mono, attributes.getResourceId(0, 0))
                assertEquals(FoundationR.font.foundation_starter_mono, attributes.getResourceId(1, 0))
                assertEquals(0f, attributes.getFloat(2, Float.NaN), 0f)
            }
        }
    }

    @Test
    fun segmentedButtons_inBothModes_keepContinuousCornersWhenSelectionChanges() {
        val layouts = listOf(
            SettingsR.layout.settings_fragment to listOf(
                SettingsR.id.settings_theme_group,
                SettingsR.id.settings_language_group,
            ),
            FunctionR.layout.function_recycler_activity to listOf(
                FunctionR.id.function_recycler_layout_group,
                FunctionR.id.function_recycler_orientation_group,
            ),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (dark in listOf(false, true)) {
                val context = themedContext(dark)
                for ((layout, groupIds) in layouts) {
                    val root = LayoutInflater.from(context).inflate(layout, null)
                    for (groupId in groupIds) {
                        val group = root.findViewById<MaterialButtonToggleGroup>(groupId)
                        group.layoutDirection = View.LAYOUT_DIRECTION_LTR
                        val buttons = List(group.childCount) { group.getChildAt(it) as MaterialButton }
                        group.check(buttons.first().id)
                        layoutSegmentedGroup(group)
                        val initialCorners = buttons.map(::cornerSizes)
                        for ((index, corners) in initialCorners.withIndex()) {
                            assertEquals(index == 0, corners[0] > 0f)
                            assertEquals(index == buttons.lastIndex, corners[1] > 0f)
                            assertEquals(index == buttons.lastIndex, corners[2] > 0f)
                            assertEquals(index == 0, corners[3] > 0f)
                        }
                        val firstButton = buttons.first()
                        val tint = requireNotNull(firstButton.backgroundTintList)
                        val checkedColor = tint.getColorForState(firstButton.drawableState, tint.defaultColor)
                        for (button in buttons) {
                            button.performClick()
                            layoutSegmentedGroup(group)
                            assertEquals(button.id, group.checkedButtonId)
                            assertEquals(initialCorners, buttons.map(::cornerSizes))
                        }
                        val uncheckedColor = tint.getColorForState(firstButton.drawableState, tint.defaultColor)
                        assertNotEquals("Selection must change segmented button color", checkedColor, uncheckedColor)
                    }
                }
            }
        }
    }

    @Test
    fun mainActivity_inBothModes_keepsTheFixedBrandTheme() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val previousMode = AppCompatDelegate.getDefaultNightMode()
        try {
            for (dark in listOf(false, true)) {
                instrumentation.runOnMainSync {
                    AppCompatDelegate.setDefaultNightMode(
                        if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    )
                }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertEquals(
                            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
                            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
                        )
                        assertEquals(color(themedContext(dark), AppCompatR.attr.colorPrimary), color(activity, AppCompatR.attr.colorPrimary))
                        assertEquals(color(themedContext(dark), MaterialR.attr.colorSurface), color(activity, MaterialR.attr.colorSurface))
                        if (InstrumentationRegistry.getArguments().getString("captureThemePreviews") == "true") {
                            val decor = activity.window.decorView
                            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                            decor.draw(Canvas(bitmap))
                            val directory = File(activity.getExternalFilesDir(null), "theme-previews").apply { mkdirs() }
                            File(directory, if (dark) "dark.png" else "light.png").outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(previousMode) }
        }
    }

    /**
     * 对离屏布局执行测量和布局, 使按钮组完成角形状更新后再读取几何结果.
     */
    private fun layoutSegmentedGroup(group: MaterialButtonToggleGroup) {
        val width = (360 * group.resources.displayMetrics.density).toInt()
        group.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        group.layout(0, 0, group.measuredWidth, group.measuredHeight)
    }

    /**
     * 按左上、右上、右下、左下顺序返回实际边界上的圆角尺寸, 单位为 px.
     */
    private fun cornerSizes(button: MaterialButton): List<Float> {
        val bounds = RectF(0f, 0f, button.width.toFloat(), button.height.toFloat())
        val shape = button.shapeAppearanceModel
        return listOf(
            shape.topLeftCornerSize.getCornerSize(bounds),
            shape.topRightCornerSize.getCornerSize(bounds),
            shape.bottomRightCornerSize.getCornerSize(bounds),
            shape.bottomLeftCornerSize.getCornerSize(bounds),
        )
    }

    private fun themedContext(dark: Boolean): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_Androidstarter)
    }

    private fun color(context: Context, attribute: Int): Int = colors(context, attribute).defaultColor

    private fun colors(context: Context, attribute: Int): ColorStateList {
        val value = TypedValue()
        assertTrue("Missing theme attribute: $attribute", context.theme.resolveAttribute(attribute, value, true))
        return if (value.resourceId != 0) {
            context.resources.getColorStateList(value.resourceId, context.theme)
        } else {
            ColorStateList.valueOf(value.data)
        }
    }
}
