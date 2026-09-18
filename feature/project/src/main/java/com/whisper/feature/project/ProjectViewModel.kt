package com.whisper.feature.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisper.architecture.extension.consumeError
import com.whisper.architecture.extension.consumeLoading
import com.whisper.architecture.extension.consumeSuccessMeta
import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.processor.BusinessErrorProcessor
import com.whisper.architecture.processor.BusinessProgressProcessor
import com.whisper.feature.project.data.GithubProjectRepository
import com.whisper.feature.project.model.GithubProject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * 管理 GitHub 项目概览的缓存优先加载与刷新状态.
 *
 * 构造时不读取数据, 由页面可见回调或用户刷新触发加载. Business 管线只描述本次操作,
 * 持续页面状态由 [uiState] 保留, 失败在页面内处理而不转发到公共通知处理器.
 */
internal class ProjectViewModel(
    private val repository: GithubProjectRepository = GithubProjectRepository(),
) : ViewModel() {

    private val _uiState: MutableStateFlow<ProjectUiState> = MutableStateFlow(ProjectUiState())

    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    /**
     * 同时覆盖首次缓存读取和网络刷新; 加载进行时忽略重复触发, 不排队.
     */
    private var loadJob: Job? = null

    /**
     * 记录是否已触发首次可见加载; 即使读取失败也不复位, 避免生命周期回调自动重试.
     */
    private var initialLoadStarted: Boolean = false

    /**
     * 将本次刷新进度映射为页面状态, 异常或取消后也由管线配对结束进度.
     */
    private val refreshProgressProcessor: BusinessProgressProcessor = object : BusinessProgressProcessor {
        override fun onBusinessStart() {
            val currentState: ProjectUiState = _uiState.value
            _uiState.value = currentState.copy(
                loading = currentState.project == null,
                refreshing = true,
                failure = null,
            )
        }

        override fun onBusinessCompletion() {
            _uiState.value = _uiState.value.copy(loading = false, refreshing = false)
        }
    }

    /**
     * 失败由项目页面展示, 保留已有数据, 不转发到公共通知处理器.
     */
    private val projectErrorProcessor: BusinessErrorProcessor<Unit> = object : BusinessErrorProcessor<Unit> {
        override fun onBusinessError(error: Business.Failure<Unit, *>) {
            _uiState.value = _uiState.value.copy(
                loading = false,
                refreshing = false,
                failure = error.exception.toProjectLoadFailure(),
            )
        }
    }

    /**
     * 首次可见时读取缓存, 再次可见时仅更新过期数据.
     *
     * 无数据失败保留页面重试入口, 不因 Activity 重建或生命周期回调自动重试.
     */
    fun refreshIfStale() {
        if (loadJob?.isActive == true) {
            return
        }
        if (!initialLoadStarted) {
            initialLoadStarted = true
            loadInitialProject()
            return
        }
        val project: GithubProject = _uiState.value.project ?: return
        if (!repository.isFresh(project)) {
            refresh()
        }
    }

    /**
     * 强制请求网络而不检查缓存新鲜期; 已有加载进行时忽略此次调用, 不排队追加请求.
     */
    fun refresh() {
        if (loadJob?.isActive == true) {
            return
        }
        loadJob = viewModelScope.launch {
            refreshProject()
        }
    }

    /**
     * 先展示缓存, 仅在读取成功且缓存缺失或过期时继续刷新; 缓存读取失败由页面接管.
     */
    private fun loadInitialProject() {
        loadJob = viewModelScope.launch {
            repository.getCachedProject()
                .consumeError(projectErrorProcessor)
                .consumeSuccessMeta()
                .collect { cachedProject: GithubProject? ->
                    if (cachedProject != null) {
                        _uiState.value = ProjectUiState(
                            project = cachedProject,
                            source = ProjectDataSource.CACHE,
                            loading = false,
                        )
                    }
                    if (cachedProject == null || !repository.isFresh(cachedProject)) {
                        refreshProject()
                    }
                }
        }
    }

    private suspend fun refreshProject() {
        repository.refreshProject()
            .consumeLoading(refreshProgressProcessor)
            .consumeError(projectErrorProcessor)
            .consumeSuccessMeta()
            .filterNotNull()
            .collect { project: GithubProject ->
                _uiState.value = ProjectUiState(
                    project = project,
                    source = ProjectDataSource.NETWORK,
                    loading = false,
                    refreshing = false,
                )
            }
    }
}

/**
 * 将底层异常归类为页面可展示的失败, 不向界面暴露服务器或数据库的原始异常文案.
 */
private fun Exception.toProjectLoadFailure(): ProjectLoadFailure =
    when (this) {
        is IOException -> ProjectLoadFailure.NETWORK
        is HttpException -> when (code()) {
            429 -> ProjectLoadFailure.RATE_LIMITED
            403 -> if (hasRateLimitHeaders()) {
                ProjectLoadFailure.RATE_LIMITED
            } else {
                ProjectLoadFailure.UNEXPECTED
            }

            in 500..599 -> ProjectLoadFailure.SERVER
            else -> ProjectLoadFailure.UNEXPECTED
        }

        else -> ProjectLoadFailure.UNEXPECTED
    }

/**
 * GitHub 的主限流返回剩余配额 0, 次级限流可返回以秒为单位的 Retry-After.
 */
private fun HttpException.hasRateLimitHeaders(): Boolean {
    val headers = response()?.headers() ?: return false
    val remainingRequests: Long? = headers["X-RateLimit-Remaining"]?.toLongOrNull()
    val retryAfterSeconds: Long? = headers["Retry-After"]?.toLongOrNull()
    return remainingRequests == 0L || (retryAfterSeconds != null && retryAfterSeconds >= 0L)
}
