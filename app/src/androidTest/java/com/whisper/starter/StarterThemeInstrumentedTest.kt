package com.whisper.starter

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whisper.starter.ui.activity.MainActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

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
