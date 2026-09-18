package com.whisper.feature.project.data

import com.whisper.architecture.extension.withLoading
import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.network.ApiFactory
import com.whisper.feature.project.data.database.GithubProjectDao
import com.whisper.feature.project.data.database.GithubProjectEntity
import com.whisper.feature.project.data.network.GithubProjectApi
import com.whisper.feature.project.data.network.GithubProjectResp
import com.whisper.feature.project.model.GithubProject
import com.whisper.habitat.runtime.HabitatFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 协调 GitHub 项目网络请求与 Room 缓存.
 *
 * 创建 Flow 不执行操作, 每次收集独立查询或刷新. 不在此处选择页面错误文案,
 * 也不将旧缓存伪装成网络成功结果.
 *
 * @property currentTimeMillis 提供 Unix 毫秒时间, 用于记录刷新时间和判断缓存新鲜期.
 */
internal class GithubProjectRepository(
    private val api: GithubProjectApi = ApiFactory.create(GithubProjectApi::class),
    private val dao: GithubProjectDao = requireGithubProjectDao(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {

    /**
     * 每次收集读取当前缓存; 没有记录时发送载荷为 null 的成功结果, 读取失败则发送失败结果.
     */
    fun getCachedProject(): Flow<Business.Outcome<Unit, GithubProject?>> = projectOutcomeFlow {
        dao.findByFullName(PROJECT_FULL_NAME)?.toProject()
    }

    /**
     * 每次收集发送 Loading, 请求 GitHub 并写入缓存, 两者均成功后才发布新数据.
     *
     * 缓存时间在请求开始前取值, 网络耗时计入新鲜期. 映射或写入失败同样发送失败结果,
     * 不提前向页面发布未持久化的网络响应.
     */
    fun refreshProject(): Flow<Business<Unit, GithubProject?>> = projectOutcomeFlow {
        val cachedAt: Long = currentTimeMillis()
        val project: GithubProject = api.getProject().toProject(cachedAt)
        dao.upsert(project.toEntity())
        project
    }.withLoading()

    /**
     * 按本地时钟判断缓存年龄; 达到 15 分钟边界即视为过期.
     */
    fun isFresh(project: GithubProject): Boolean =
        currentTimeMillis() - project.cachedAt < CACHE_FRESHNESS_MILLIS

    /**
     * 将 Project 数据操作转换为冷的业务结果流, 当前没有额外元信息, 使用 Unit.
     *
     * 只捕获数据操作中的异常并保留原异常, 取消继续传播. emit 位于转换边界外,
     * 避免把下游页面处理抛出的异常再次包装成业务失败.
     */
    private fun projectOutcomeFlow(
        block: suspend () -> GithubProject?,
    ): Flow<Business.Outcome<Unit, GithubProject?>> = flow {
        val outcome: Business.Outcome<Unit, GithubProject?> = try {
            Business.Success(meta = Unit, data = block())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Business.Failure(exception = exception, meta = Unit, data = null)
        }
        emit(outcome)
    }

    private companion object {

        const val PROJECT_FULL_NAME: String = "zhoubuilds/android-starter"
        const val CACHE_FRESHNESS_MILLIS: Long = 15L * 60L * 1_000L

        /**
         * 要求 app 已完成 Room 与 Habitat 装配; 缺少 Dao 属于初始化错误, 不创建备用数据库.
         */
        fun requireGithubProjectDao(): GithubProjectDao =
            checkNotNull(HabitatFactory.get<GithubProjectDao>()) {
                "GithubProjectDao is not registered in Habitat."
            }
    }
}

/**
 * 校验仓库身份后转换为领域模型, 其余缺失字段保留为 null.
 *
 * 许可证优先使用 SPDX 标识; GitHub 返回 NOASSERTION 时改用名称, 不把该占位值展示为许可证.
 *
 * @param cachedAt 本次刷新开始时取得的 Unix 毫秒时间.
 * @throws IllegalStateException 响应缺少仓库 id, 完整名称或网页地址.
 */
private fun GithubProjectResp.toProject(cachedAt: Long): GithubProject {
    val repositoryId: Long = checkNotNull(id) {
        "GitHub response did not contain a repository id."
    }
    val fullName: String = checkNotNull(fullName) {
        "GitHub response did not contain a repository full name."
    }
    val htmlUrl: String = checkNotNull(htmlUrl) {
        "GitHub response did not contain a repository URL."
    }
    return GithubProject(
        repositoryId = repositoryId,
        fullName = fullName,
        description = description,
        htmlUrl = htmlUrl,
        starCount = starCount,
        forkCount = forkCount,
        openItemCount = openItemCount,
        language = language,
        licenseName = license?.spdxId?.takeUnless { value: String -> value == "NOASSERTION" }
            ?: license?.name,
        defaultBranch = defaultBranch,
        pushedAt = pushedAt,
        privateRepository = privateRepository,
        cachedAt = cachedAt,
    )
}

private fun GithubProjectEntity.toProject(): GithubProject =
    GithubProject(
        repositoryId = repositoryId,
        fullName = fullName,
        description = description,
        htmlUrl = htmlUrl,
        starCount = starCount,
        forkCount = forkCount,
        openItemCount = openItemCount,
        language = language,
        licenseName = licenseName,
        defaultBranch = defaultBranch,
        pushedAt = pushedAt,
        privateRepository = privateRepository,
        cachedAt = cachedAt,
    )

private fun GithubProject.toEntity(): GithubProjectEntity =
    GithubProjectEntity(
        repositoryId = repositoryId,
        fullName = fullName,
        description = description,
        htmlUrl = htmlUrl,
        starCount = starCount,
        forkCount = forkCount,
        openItemCount = openItemCount,
        language = language,
        licenseName = licenseName,
        defaultBranch = defaultBranch,
        pushedAt = pushedAt,
        privateRepository = privateRepository,
        cachedAt = cachedAt,
    )
