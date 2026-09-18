package com.whisper.starter.network

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whisper.kit.utils.ClientInfoUtils
import com.whisper.starter.ui.activity.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * 验证使用 Application 的公共请求头随应用语言切换, 并支持恢复为系统语言.
 *
 * @author whisper
 * @since 2026/09/17
 */
@RunWith(AndroidJUnit4::class)
class StarterRequestHeadersProviderInstrumentedTest {

    @Test
    fun currentHeaders_whenApplicationLanguageChanges_updatesExistingProviderAndSupportsSystemDefault() {
        val application: Application = ApplicationProvider.getApplicationContext()
        val provider = StarterRequestHeadersProvider(application)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val systemLocale: Locale = checkNotNull(LocaleManagerCompat.getSystemLocales(application)[0])

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var previousLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()
            scenario.onActivity {
                previousLocales = AppCompatDelegate.getApplicationLocales()
            }
            try {
                for (languageTag in listOf("en", "zh-CN")) {
                    scenario.onActivity {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
                        assertEquals(languageTag, provider.currentHeaders()["Locale"])
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertEquals(
                            Locale.forLanguageTag(languageTag).language,
                            ClientInfoUtils.getPrimaryAppLocale(activity).language,
                        )
                    }
                    if (Build.VERSION.SDK_INT <= 32) {
                        assertEquals(systemLocale, ClientInfoUtils.getPrimaryAppLocale(application))
                    }
                }

                scenario.onActivity {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(systemLocale.language, ClientInfoUtils.getPrimaryAppLocale(activity).language)
                    assertEquals(systemLocale.toLanguageTag(), provider.currentHeaders()["Locale"])
                }
            } finally {
                instrumentation.runOnMainSync {
                    AppCompatDelegate.setApplicationLocales(previousLocales)
                }
                instrumentation.waitForIdleSync()
            }
        }
    }
}
