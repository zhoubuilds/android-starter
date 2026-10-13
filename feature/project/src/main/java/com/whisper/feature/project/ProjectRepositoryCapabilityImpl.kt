package com.whisper.feature.project

import android.app.Application
import com.whisper.aster.runtime.annotation.Capable
import com.whisper.feature.project.api.ProjectCapabilities
import com.whisper.feature.project.api.ProjectRepositoryCapability

/**
 * 向其它 feature 提供 Project 的稳定源码仓库入口.
 *
 * 能力不读取页面状态、网络或缓存, 因此 About 页面在离线和首次启动时也能展示仓库地址.
 *
 * @author whisper
 * @since 2026/10/13
 */
@Capable(name = ProjectCapabilities.REPOSITORY)
class ProjectRepositoryCapabilityImpl : ProjectRepositoryCapability {

    override val repositoryUrl: String = GithubProjectIdentity.WEB_URL

    override fun initialize(application: Application) = Unit
}
