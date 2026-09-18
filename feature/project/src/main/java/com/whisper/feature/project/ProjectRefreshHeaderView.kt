package com.whisper.feature.project

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.whisper.feature.project.databinding.ProjectRefreshHeaderBinding
import com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadComponent
import com.whisper.kit.widget.refreshloadlayout.KitRefreshLoadLayout

/**
 * 呈现项目页下拉刷新状态的头部组件.
 *
 * @author whisper
 * @since 2026/09/14
 */
class ProjectRefreshHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), KitRefreshLoadComponent {

    private val viewBinding: ProjectRefreshHeaderBinding =
        ProjectRefreshHeaderBinding.inflate(LayoutInflater.from(context), this, true)

    override fun onRefreshLoadStateChanged(
        layout: KitRefreshLoadLayout,
        state: KitRefreshLoadLayout.RefreshLoadState,
    ) {
        val refreshing: Boolean = state == KitRefreshLoadLayout.RefreshLoadState.Refreshing
        viewBinding.projectRefreshProgress.isVisible = refreshing
        viewBinding.projectRefreshIcon.isVisible = !refreshing
        viewBinding.projectRefreshText.setText(
            when (state) {
                KitRefreshLoadLayout.RefreshLoadState.ReleaseToRefresh ->
                    R.string.project_refresh_release

                KitRefreshLoadLayout.RefreshLoadState.Refreshing ->
                    R.string.project_refreshing

                else -> R.string.project_refresh_pull
            }
        )
    }

    override fun onRefreshLoadOffsetChanged(
        layout: KitRefreshLoadLayout,
        offset: Int,
        triggerDistance: Int,
        holdDistance: Int,
    ) {
        val progress: Float = if (triggerDistance > 0) {
            (offset.toFloat() / triggerDistance).coerceIn(0f, 1f)
        } else {
            0f
        }
        viewBinding.projectRefreshIcon.rotation = progress * 180f
    }
}
