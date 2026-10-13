package com.whisper.feature.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.whisper.feature.settings.databinding.SettingsFragmentBinding
import com.whisper.kit.extension.viewBinding
import com.whisper.kit.utils.ClientInfoUtils

/**
 * 提供应用颜色模式、语言和应用信息入口.
 *
 * 当前类型由 app 组合根直接创建, 不作为跨模块路由或能力契约.
 *
 * @author whisper
 * @since 2026/09/14
 */
class SettingsFragment : Fragment(R.layout.settings_fragment) {

    @get:MainThread
    private val viewBinding by viewBinding(SettingsFragmentBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureThemeSelection()
        configureLanguageSelection()
        configureAboutEntry()
    }

    /**
     * 先回显当前偏好再注册监听, 避免 View 重建时把状态恢复当作用户修改写回配置.
     */
    private fun configureThemeSelection() {
        viewBinding.settingsThemeGroup.check(
            AppAppearancePreferences.currentThemeMode(requireContext()).buttonId,
        )
        viewBinding.settingsThemeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                AppAppearancePreferences.setThemeMode(
                    requireContext(),
                    AppThemeMode.fromButtonId(checkedId),
                )
            }
        }
    }

    /**
     * 先回显应用语言再注册监听, 后续切换由 AppCompat 负责持久化及必要的 Activity 重建.
     */
    private fun configureLanguageSelection() {
        viewBinding.settingsLanguageGroup.check(currentLanguage().buttonId)
        viewBinding.settingsLanguageGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setApplicationLocales(
                    AppLanguage.fromButtonId(checkedId).locales,
                )
            }
        }
    }

    private fun configureAboutEntry() {
        val versionName: String? = ClientInfoUtils.getAppVersionName(requireContext())
        viewBinding.settingsAboutSummary.text = versionName?.let { version: String ->
            getString(R.string.settings_about_version, version)
        } ?: getString(R.string.settings_about_version_unknown)
        viewBinding.settingsAbout.setOnClickListener {
            startActivity(Intent(requireContext(), AboutStarterActivity::class.java))
        }
    }

    /**
     * 从显式应用语言恢复选中项; 空语言列表表示跟随系统, 不能根据当前资源语言推断该选择.
     */
    private fun currentLanguage(): AppLanguage {
        val languageTags: String = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            languageTags.isBlank() -> AppLanguage.SYSTEM
            languageTags.startsWith("zh", ignoreCase = true) -> AppLanguage.CHINESE
            else -> AppLanguage.ENGLISH
        }
    }
}

private val AppThemeMode.buttonId: Int
    @IdRes get() = when (this) {
        AppThemeMode.SYSTEM -> R.id.settings_theme_system
        AppThemeMode.LIGHT -> R.id.settings_theme_light
        AppThemeMode.DARK -> R.id.settings_theme_dark
    }

private fun AppThemeMode.Companion.fromButtonId(@IdRes buttonId: Int): AppThemeMode =
    when (buttonId) {
        R.id.settings_theme_light -> AppThemeMode.LIGHT
        R.id.settings_theme_dark -> AppThemeMode.DARK
        else -> AppThemeMode.SYSTEM
    }

/**
 * 应用支持的界面语言.
 *
 * @property buttonId 设置页对应的单选按钮资源 ID.
 * @property locales 交给 AppCompat 的语言列表, 空列表表示恢复跟随系统.
 */
private enum class AppLanguage(
    @param:IdRes val buttonId: Int,
    val locales: LocaleListCompat,
) {
    SYSTEM(
        buttonId = R.id.settings_language_system,
        locales = LocaleListCompat.getEmptyLocaleList(),
    ),
    CHINESE(
        buttonId = R.id.settings_language_chinese,
        locales = LocaleListCompat.forLanguageTags("zh-CN"),
    ),
    ENGLISH(
        buttonId = R.id.settings_language_english,
        locales = LocaleListCompat.forLanguageTags("en"),
    );

    companion object {

        fun fromButtonId(@IdRes buttonId: Int): AppLanguage =
            entries.firstOrNull { language: AppLanguage -> language.buttonId == buttonId } ?: SYSTEM
    }
}
