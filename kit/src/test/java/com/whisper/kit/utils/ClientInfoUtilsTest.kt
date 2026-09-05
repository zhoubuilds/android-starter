package com.whisper.kit.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 验证客户端与宿主应用基础信息的查询边界.
 *
 * @author whisper
 * @since 2026/09/05
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class ClientInfoUtilsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun platformProperties_whenRead_returnRawBuildValues() {
        assertEquals(Build.VERSION.SDK_INT, ClientInfoUtils.sdkInt)
        assertEquals(Build.VERSION.RELEASE, ClientInfoUtils.osRelease)
        assertEquals(Build.MANUFACTURER, ClientInfoUtils.manufacturer)
        assertEquals(Build.BRAND, ClientInfoUtils.brand)
        assertEquals(Build.MODEL, ClientInfoUtils.model)
    }

    @Test
    fun getPackageName_whenCalled_returnsContextPackageName() {
        assertEquals(context.packageName, ClientInfoUtils.getPackageName(context))
    }

    @Test
    fun getAppVersion_whenCalled_returnsRawPackageValues() {
        val packageInfo: PackageInfo = getPackageInfo(context)
        val expectedVersionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        assertEquals(packageInfo.versionName, ClientInfoUtils.getAppVersionName(context))
        assertEquals(expectedVersionCode, ClientInfoUtils.getAppVersionCode(context))
    }

    @Test
    fun getAppLocales_whenCalled_returnsConfigurationLocales() {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.JAPAN, Locale.US))
        }
        val localizedContext: Context = context.createConfigurationContext(configuration)

        assertEquals(configuration.locales, ClientInfoUtils.getAppLocales(localizedContext))
    }

    @Test
    fun getPrimaryAppLocale_whenContextHasMultipleLocales_returnsFirstLocale() {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.JAPAN, Locale.US))
        }
        val localizedContext: Context = context.createConfigurationContext(configuration)

        assertEquals(Locale.JAPAN, ClientInfoUtils.getPrimaryAppLocale(localizedContext))
    }

    @Test
    fun getPrimaryAppLocale_whenLocaleListIsEmpty_returnsDefaultLocale() {
        context.resources.configuration.setLocales(LocaleList.getEmptyLocaleList())

        assertEquals(Locale.getDefault(), ClientInfoUtils.getPrimaryAppLocale(context))
    }

    @Test
    fun getAppVersion_whenPackageDoesNotExist_returnsNull() {
        val missingPackageContext: Context = object : ContextWrapper(context) {
            override fun getPackageName(): String = "com.whisper.kit.missing"
        }

        assertNull(ClientInfoUtils.getAppVersionName(missingPackageContext))
        assertNull(ClientInfoUtils.getAppVersionCode(missingPackageContext))
    }

    @Test
    fun getDefaultUserAgent_whenCalled_returnsDirectlyUsableProductTokens() {
        val version: String = ClientInfoUtils.getAppVersionName(context)
            ?.takeIf { it.isNotBlank() }
            ?: ClientInfoUtils.getAppVersionCode(context)?.toString()
            ?: "unknown"

        assertEquals(
            "${context.packageName}/$version " +
                "Android/${Build.VERSION.RELEASE} API/${Build.VERSION.SDK_INT}",
            ClientInfoUtils.getDefaultUserAgent(context),
        )
    }

    @Test
    fun getDefaultUserAgent_whenPackageIsMissing_sanitizesPackageAndUsesFallbackVersion() {
        val missingPackageContext: Context = object : ContextWrapper(context) {
            override fun getPackageName(): String = "com.whisper invalid/\u5ba2\u6237\u7aef"
        }

        assertEquals(
            "com.whisper-invalid-/unknown " +
                "Android/${Build.VERSION.RELEASE} API/${Build.VERSION.SDK_INT}",
            ClientInfoUtils.getDefaultUserAgent(missingPackageContext),
        )
    }

    @Test
    fun getDefaultUserAgent_whenPackageTokenIsLong_limitsTokenLength() {
        val missingPackageContext: Context = object : ContextWrapper(context) {
            override fun getPackageName(): String = "a".repeat(200)
        }

        val packageToken: String = ClientInfoUtils.getDefaultUserAgent(missingPackageContext)
            .substringBefore('/')

        assertEquals(128, packageToken.length)
        assertTrue(packageToken.all { it == 'a' })
    }

    private fun getPackageInfo(targetContext: Context): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            targetContext.packageManager.getPackageInfo(
                targetContext.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            targetContext.packageManager.getPackageInfo(targetContext.packageName, 0)
        }
    }
}
