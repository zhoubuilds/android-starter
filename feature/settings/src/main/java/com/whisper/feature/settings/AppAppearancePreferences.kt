package com.whisper.feature.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/**
 * 应用支持的颜色模式.
 *
 * @property preferenceValue 独立于枚举名称的持久化值.
 * @property nightMode 对应的 AppCompat 日夜模式常量.
 */
internal enum class AppThemeMode(
    val preferenceValue: String,
    val nightMode: Int,
) {
    SYSTEM(
        preferenceValue = "system",
        nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    ),
    LIGHT(
        preferenceValue = "light",
        nightMode = AppCompatDelegate.MODE_NIGHT_NO,
    ),
    DARK(
        preferenceValue = "dark",
        nightMode = AppCompatDelegate.MODE_NIGHT_YES,
    );

    companion object {

        /**
         * 未保存或无法识别的偏好回退为跟随系统, 不因异常值阻断应用启动.
         */
        fun fromPreferenceValue(value: String?): AppThemeMode =
            entries.firstOrNull { mode: AppThemeMode -> mode.preferenceValue == value } ?: SYSTEM
    }
}

/**
 * 保存并应用应用级颜色模式.
 *
 * App 组合根应在首个 Activity 创建前调用 [initialize], 页面读写入口只在 Settings 模块内部使用.
 *
 * @author whisper
 * @since 2026/09/14
 */
object AppAppearancePreferences {

    private const val PREFERENCES_NAME: String = "starter_appearance"
    private const val KEY_THEME_MODE: String = "theme_mode"

    /**
     * 在首个 Activity 创建前恢复已保存的颜色模式, 避免先绘制默认主题再切换.
     */
    fun initialize(context: Context) {
        AppCompatDelegate.setDefaultNightMode(currentThemeMode(context).nightMode)
    }

    internal fun currentThemeMode(context: Context): AppThemeMode {
        val value: String? = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).getString(KEY_THEME_MODE, null)
        return AppThemeMode.fromPreferenceValue(value)
    }

    /**
     * 先更新偏好再切换 AppCompat 模式, 使可能发生的 Activity 重建读取到最新选择.
     */
    internal fun setThemeMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_THEME_MODE, mode.preferenceValue)
        }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }
}
