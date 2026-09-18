package com.whisper.starter.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import com.whisper.kit.extension.applyWindowInsetsPadding
import com.whisper.kit.extension.viewBinding
import com.whisper.kit.utils.ClientInfoUtils
import com.whisper.starter.R
import com.whisper.starter.databinding.AboutStarterActivityBinding

/**
 * 展示 Starter 应用身份与版本.
 *
 * Activity 仅由应用内部设置页打开, 不作为跨模块路由契约.
 *
 * @author whisper
 * @since 2026/09/14
 */
class AboutStarterActivity : AppCompatActivity() {

    @get:MainThread
    private val viewBinding by viewBinding(AboutStarterActivityBinding::inflate)

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
        viewBinding.aboutStarterRoot.applyWindowInsetsPadding()
    }

    private fun configureToolbar() {
        viewBinding.aboutStarterToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun bindApplicationInfo() {
        val applicationInfo = applicationInfo
        viewBinding.aboutStarterAppIcon.setImageDrawable(
            applicationInfo.loadIcon(packageManager),
        )
        viewBinding.aboutStarterAppName.text = applicationInfo.loadLabel(packageManager)

        val versionName: String? = ClientInfoUtils.getAppVersionName(this)
        val versionCode: Long? = ClientInfoUtils.getAppVersionCode(this)
        viewBinding.aboutStarterAppVersion.text = if (versionName != null && versionCode != null) {
            getString(R.string.about_starter_version, versionName, versionCode)
        } else {
            getString(R.string.about_starter_version_unknown)
        }
    }
}
