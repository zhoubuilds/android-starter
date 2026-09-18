package com.whisper.feature.project

import com.whisper.feature.project.model.GithubProject

/**
 * GitHub 项目概览的持续页面状态.
 *
 * @property project 当前已展示的数据; null 表示尚无可用项目, 不等同于加载失败.
 * @property source 当前数据由缓存读取还是网络刷新取得; 刷新失败不改变已有来源.
 * @property loading 是否显示无数据时的首屏进度, 初始为 true 以避免进入页面时闪现错误区.
 * @property refreshing 是否正在执行网络刷新; 有项目数据时由下拉刷新头部呈现.
 * @property failure 最近一次加载失败; 可与已有项目同时存在, 由页面展示非阻断告警.
 */
internal data class ProjectUiState(
    val project: GithubProject? = null,
    val source: ProjectDataSource? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val failure: ProjectLoadFailure? = null,
)

/**
 * 当前项目数据来源.
 */
internal enum class ProjectDataSource {
    NETWORK,
    CACHE,
}

/**
 * 项目概览加载失败类型.
 */
internal enum class ProjectLoadFailure {
    NETWORK,
    RATE_LIMITED,
    SERVER,
    UNEXPECTED,
}
