package com.whisper.feature.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import com.whisper.feature.settings.databinding.SettingsAboutActivityBinding
import com.whisper.kit.extension.applyWindowInsetsPadding
import com.whisper.kit.extension.viewBinding
import com.whisper.kit.utils.ClientInfoUtils

/**
 * 展示 Starter 应用身份与版本.
 *
 * Activity 仅由设置页打开, 不作为跨模块路由契约.
 *
 * @author whisper
 * @since 2026/09/14
 */
class AboutStarterActivity : AppCompatActivity() {

    @get:MainThread
    private val viewBinding by viewBinding(SettingsAboutActivityBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(viewBinding.root)
        configureWindowInsets()
        configureToolbar()
        bindApplicationInfo()
    }

    /**
     * 详情根布局统一处理四边系统栏与刘海避让, 子 View 接收扣除后的剩余 Insets.
     */
    private fun configureWindowInsets() {
        viewBinding.settingsAboutRoot.applyWindowInsetsPadding()
    }

    private fun configureToolbar() {
        viewBinding.settingsAboutToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun bindApplicationInfo() {
        val applicationInfo = applicationInfo
        viewBinding.settingsAboutAppIcon.setImageDrawable(
            applicationInfo.loadIcon(packageManager),
        )
        viewBinding.settingsAboutAppName.text = applicationInfo.loadLabel(packageManager)

        val versionName: String? = ClientInfoUtils.getAppVersionName(this)
        val versionCode: Long? = ClientInfoUtils.getAppVersionCode(this)
        viewBinding.settingsAboutAppVersion.text = if (versionName != null && versionCode != null) {
            getString(R.string.settings_about_version_with_code, versionName, versionCode)
        } else {
            getString(R.string.settings_about_version_unknown)
        }
    }
}
