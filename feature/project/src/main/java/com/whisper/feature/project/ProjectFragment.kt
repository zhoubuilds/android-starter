package com.whisper.feature.project

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.whisper.feature.project.databinding.ProjectFragmentBinding
import com.whisper.feature.project.model.GithubProject
import com.whisper.kit.extension.viewBinding
import com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadLayout
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 展示 GitHub 公开仓库信息、缓存来源和刷新状态.
 *
 * 当前类型由 app 组合根直接创建, 不作为跨模块路由或能力契约.
 *
 * @author whisper
 * @since 2026/09/14
 */
class ProjectFragment : Fragment(R.layout.project_fragment) {

    @get:MainThread
    private val viewBinding by viewBinding(ProjectFragmentBinding::bind)

    private val viewModel: ProjectViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this)[ProjectViewModel::class.java]
    }

    /**
     * 上一次判定的可见状态, 用于合并 onResume 与 show/hide 回调中的重复进入通知.
     */
    private var projectVisible: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureProjectIdentity()
        configureMetadataLabels()
        configureActions()
        observeProject()
    }

    override fun onResume() {
        super.onResume()
        refreshOnVisibilityChange()
    }

    override fun onPause() {
        projectVisible = false
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        refreshOnVisibilityChange()
    }

    /**
     * show/hide 不改变 Fragment 生命周期, 只在真正进入可见状态时检查一次新鲜期.
     */
    private fun refreshOnVisibilityChange() {
        val visible: Boolean = isResumed && !isHidden
        if (visible && !projectVisible) {
            projectVisible = true
            viewModel.refreshIfStale()
        } else if (!visible) {
            projectVisible = false
        }
    }

    private fun configureProjectIdentity() {
        val context = requireContext()
        viewBinding.projectAppIcon.setImageDrawable(
            context.applicationInfo.loadIcon(context.packageManager),
        )
    }

    private fun configureMetadataLabels() {
        viewBinding.projectLanguageRow.projectMetadataLabel.setText(R.string.project_language)
        viewBinding.projectLicenseRow.projectMetadataLabel.setText(R.string.project_license)
        viewBinding.projectBranchRow.projectMetadataLabel.setText(R.string.project_branch)
        viewBinding.projectPushedRow.projectMetadataLabel.setText(R.string.project_pushed)
    }

    private fun configureActions() {
        viewBinding.projectRefresh.apply {
            loadMoreEnabled = false
            setScrollBoundaryCallback { _, _, direction: Int ->
                viewBinding.projectScroll.canScrollVertically(direction)
            }
            setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction ->
                when (action) {
                    KitRefreshLoadLayout.RefreshLoadAction.Refresh -> viewModel.refresh()
                    KitRefreshLoadLayout.RefreshLoadAction.LoadMore -> Unit
                }
            }
        }
        viewBinding.projectRetry.setOnClickListener {
            viewModel.refresh()
        }
        viewBinding.projectOpenGithub.setOnClickListener {
            viewModel.uiState.value.project?.htmlUrl?.let(::openRepository)
        }
    }

    private fun observeProject() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderProject)
            }
        }
    }

    /**
     * 将持续状态还原到页面; 有数据时刷新失败只展示告警, 不遮挡已有项目内容.
     */
    private fun renderProject(state: ProjectUiState) {
        val project: GithubProject? = state.project
        viewBinding.projectLoading.isVisible = state.loading && project == null
        viewBinding.projectError.isVisible = !state.loading && project == null
        viewBinding.projectContent.isVisible = project != null
        viewBinding.projectRefresh.setActiveAction(
            action = if (state.refreshing && project != null) {
                KitRefreshLoadLayout.RefreshLoadAction.Refresh
            } else {
                null
            },
            animate = project != null,
        )

        val failureMessage: String? = state.failure?.let(::failureMessage)
        viewBinding.projectErrorMessage.text = failureMessage
        viewBinding.projectWarning.isVisible = project != null && failureMessage != null
        viewBinding.projectWarning.text = failureMessage
        if (project != null) {
            bindProject(project, state.source)
        }
    }

    private fun bindProject(project: GithubProject, source: ProjectDataSource?) {
        viewBinding.projectFullName.text = project.fullName
        viewBinding.projectVisibility.setText(
            when (project.privateRepository) {
                true -> R.string.project_private
                false -> R.string.project_public
                null -> R.string.project_visibility_unknown
            }
        )
        viewBinding.projectDescription.text = project.description
            ?.takeIf(String::isNotBlank)
            ?: getString(R.string.project_description_unavailable)
        viewBinding.projectStars.text = formatCount(project.starCount)
        viewBinding.projectForks.text = formatCount(project.forkCount)
        viewBinding.projectOpenItems.text = formatCount(project.openItemCount)
        viewBinding.projectLanguageRow.projectMetadataValue.text = project.language.orUnknown()
        viewBinding.projectLicenseRow.projectMetadataValue.text = project.licenseName.orUnknown()
        viewBinding.projectBranchRow.projectMetadataValue.text = project.defaultBranch.orUnknown()
        viewBinding.projectPushedRow.projectMetadataValue.text = formatGithubTime(project.pushedAt)
        viewBinding.projectSource.text = getString(
            when (source) {
                ProjectDataSource.NETWORK -> R.string.project_source_network
                ProjectDataSource.CACHE, null -> R.string.project_source_cache
            },
            formatTime(project.cachedAt),
        )
    }

    private fun String?.orUnknown(): String =
        this?.takeIf(String::isNotBlank) ?: getString(R.string.project_value_unknown)

    private fun formatCount(value: Int?): String = value?.let { count: Int ->
        NumberFormat.getIntegerInstance().format(count)
    } ?: getString(R.string.project_value_unknown)

    /**
     * 将 GitHub 的 UTC 时间转为设备当前时区; 缺失或无法解析时显示统一的未知占位.
     */
    private fun formatGithubTime(value: String?): String {
        if (value.isNullOrBlank()) {
            return getString(R.string.project_value_unknown)
        }
        return try {
            val githubTimeFormat = SimpleDateFormat(GITHUB_TIME_PATTERN, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone(GITHUB_TIME_ZONE)
            }
            val timestamp: Long = githubTimeFormat.parse(value)?.time
                ?: return getString(R.string.project_value_unknown)
            formatTime(timestamp)
        } catch (_: ParseException) {
            getString(R.string.project_value_unknown)
        }
    }

    /**
     * 按设备当前语言和时区显示 Unix 时间戳, [timestamp] 的单位为毫秒.
     */
    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(timestamp))

    private fun failureMessage(failure: ProjectLoadFailure): String =
        getString(
            when (failure) {
                ProjectLoadFailure.NETWORK -> R.string.project_error_network
                ProjectLoadFailure.RATE_LIMITED -> R.string.project_error_rate_limited
                ProjectLoadFailure.SERVER -> R.string.project_error_server
                ProjectLoadFailure.UNEXPECTED -> R.string.project_error_unexpected
            }
        )

    private fun openRepository(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                R.string.project_browser_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private companion object {

        const val GITHUB_TIME_PATTERN: String = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        const val GITHUB_TIME_ZONE: String = "UTC"
        const val TIME_PATTERN: String = "yyyy-MM-dd HH:mm"
    }
}
