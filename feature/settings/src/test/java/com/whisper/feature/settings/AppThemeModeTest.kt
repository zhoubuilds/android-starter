package com.whisper.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证颜色模式持久化值的解析规则.
 *
 * @author whisper
 * @since 2026/10/13
 */
class AppThemeModeTest {

    @Test
    fun fromPreferenceValue_whenValueIsKnown_returnsMatchingMode() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromPreferenceValue("system"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromPreferenceValue("light"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromPreferenceValue("dark"))
    }

    @Test
    fun fromPreferenceValue_whenValueIsMissingOrUnknown_returnsSystem() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromPreferenceValue(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromPreferenceValue("unknown"))
    }
}
