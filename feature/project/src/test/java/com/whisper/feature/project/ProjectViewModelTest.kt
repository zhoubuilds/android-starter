package com.whisper.feature.project

import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.whisper.feature.project.data.GithubProjectRepository
import com.whisper.feature.project.data.database.GithubProjectDao
import com.whisper.feature.project.data.database.GithubProjectEntity
import com.whisper.feature.project.data.network.GithubProjectApi
import com.whisper.feature.project.data.network.GithubProjectResp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 验证业务管线接入后页面的缓存、刷新与就地错误处理行为.
 *
 * @author whisper
 * @since 2026/09/16
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ProjectViewModelTest {

    private val viewModels: MutableList<ProjectViewModel> = mutableListOf()
    private var currentTime: Long = CURRENT_TIME

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        idleMainLooper()
    }

    @Test
    fun initialLoad_whenCacheIsFresh_displaysCacheWithoutNetworkRequest() {
        val api = FakeGithubProjectApi()
        val viewModel = createViewModel(api, FakeGithubProjectDao(cachedEntity()))

        val state = viewModel.uiState.value

        assertEquals(ProjectDataSource.CACHE, state.source)
        assertEquals(10, state.project?.starCount)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.failure)
        assertEquals(0, api.requestCount)
    }

    @Test
    fun initialization_beforePageBecomesVisible_doesNotReadCacheOrRequestNetwork() {
        val api = FakeGithubProjectApi()
        val dao = FakeGithubProjectDao(cachedEntity())
        val viewModel = createViewModel(api, dao, visible = false)

        assertEquals(0, dao.readCount)
        assertEquals(0, api.requestCount)
        assertNull(viewModel.uiState.value.project)

        viewModel.refreshIfStale()
        idleMainLooper()

        assertEquals(1, dao.readCount)
        assertEquals(ProjectDataSource.CACHE, viewModel.uiState.value.source)
    }

    @Test
    fun refreshIfStale_whenPageReturnsWithFreshData_doesNotReload() {
        val api = FakeGithubProjectApi()
        val dao = FakeGithubProjectDao(cachedEntity())
        val viewModel = createViewModel(api, dao)
        currentTime += CACHE_FRESHNESS_MILLIS - 1L

        viewModel.refreshIfStale()
        idleMainLooper()

        assertEquals(1, dao.readCount)
        assertEquals(0, api.requestCount)
        assertEquals(ProjectDataSource.CACHE, viewModel.uiState.value.source)
    }

    @Test
    fun refreshIfStale_whenPageReturnsWithExpiredData_refreshesAndGuardsDuplicateRequests() {
        val response = CompletableDeferred<GithubProjectResp>()
        val api = FakeGithubProjectApi { response.await() }
        val dao = FakeGithubProjectDao(cachedEntity())
        val viewModel = createViewModel(api, dao)
        currentTime += CACHE_FRESHNESS_MILLIS

        viewModel.refreshIfStale()
        viewModel.refreshIfStale()
        idleMainLooper()

        assertEquals(1, api.requestCount)
        assertEquals(1, dao.readCount)
        assertEquals(ProjectDataSource.CACHE, viewModel.uiState.value.source)
        assertEquals(10, viewModel.uiState.value.project?.starCount)
        assertTrue(viewModel.uiState.value.refreshing)

        response.complete(networkResponse())
        idleMainLooper()

        assertEquals(ProjectDataSource.NETWORK, viewModel.uiState.value.source)
        assertEquals(currentTime, viewModel.uiState.value.project?.cachedAt)
        assertFalse(viewModel.uiState.value.refreshing)
    }

    @Test
    fun refreshIfStale_afterInitialNetworkFailure_keepsFailureUntilManualRetry() {
        val api = FakeGithubProjectApi { throw IOException("Network unavailable") }
        val viewModel = createViewModel(api)

        viewModel.refreshIfStale()
        idleMainLooper()

        assertEquals(1, api.requestCount)
        assertEquals(ProjectLoadFailure.NETWORK, viewModel.uiState.value.failure)

        viewModel.refresh()
        idleMainLooper()

        assertEquals(2, api.requestCount)
    }

    @Test
    fun refreshIfStale_afterCacheReadFailure_doesNotRetryOrBypassCache() {
        val api = FakeGithubProjectApi()
        val dao = FakeGithubProjectDao(readFailure = IllegalStateException("Cache unavailable"))
        val viewModel = createViewModel(api, dao)

        viewModel.refreshIfStale()
        idleMainLooper()

        assertEquals(1, dao.readCount)
        assertEquals(0, api.requestCount)
        assertEquals(ProjectLoadFailure.UNEXPECTED, viewModel.uiState.value.failure)
    }

    @Test
    fun initialLoad_whenCacheIsExpired_displaysCacheUntilNetworkCompletes() {
        val response = CompletableDeferred<GithubProjectResp>()
        val api = FakeGithubProjectApi { response.await() }
        val dao = FakeGithubProjectDao(cachedEntity(cachedAt = EXPIRED_AT))
        val viewModel = createViewModel(api, dao)

        val refreshingState = viewModel.uiState.value
        assertEquals(ProjectDataSource.CACHE, refreshingState.source)
        assertEquals(10, refreshingState.project?.starCount)
        assertFalse(refreshingState.loading)
        assertTrue(refreshingState.refreshing)
        assertEquals(1, api.requestCount)

        response.complete(networkResponse())
        idleMainLooper()

        val completedState = viewModel.uiState.value
        assertEquals(ProjectDataSource.NETWORK, completedState.source)
        assertEquals(11, completedState.project?.starCount)
        assertEquals(CURRENT_TIME, dao.entity?.cachedAt)
        assertFalse(completedState.loading)
        assertFalse(completedState.refreshing)
        assertNull(completedState.failure)
    }

    @Test
    fun refresh_whenNetworkFails_keepsCachedProjectAndDisplaysPageFailure() {
        val api = FakeGithubProjectApi { throw IOException("Network unavailable") }
        val viewModel = createViewModel(api, FakeGithubProjectDao(cachedEntity()))
        val cachedProject = viewModel.uiState.value.project

        viewModel.refresh()
        idleMainLooper()

        val state = viewModel.uiState.value
        assertEquals(cachedProject, state.project)
        assertEquals(ProjectDataSource.CACHE, state.source)
        assertEquals(ProjectLoadFailure.NETWORK, state.failure)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
    }

    @Test
    fun initialLoad_whenNoCacheAndNetworkFails_displaysEmptyPageFailure() {
        val api = FakeGithubProjectApi { throw IOException("Network unavailable") }
        val viewModel = createViewModel(api)

        val state = viewModel.uiState.value

        assertNull(state.project)
        assertNull(state.source)
        assertEquals(ProjectLoadFailure.NETWORK, state.failure)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertEquals(1, api.requestCount)
    }

    @Test
    fun initialLoad_whenCacheReadFails_displaysPageFailureWithoutRequestingNetwork() {
        val api = FakeGithubProjectApi()
        val dao = FakeGithubProjectDao(readFailure = IllegalStateException("Cache unavailable"))
        val viewModel = createViewModel(api, dao)

        val state = viewModel.uiState.value

        assertEquals(ProjectLoadFailure.UNEXPECTED, state.failure)
        assertNull(state.project)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertEquals(0, api.requestCount)
    }

    @Test
    fun refresh_whenAlreadyRunning_ignoresDuplicatesAndAllowsNextRefresh() {
        val response = CompletableDeferred<GithubProjectResp>()
        val api = FakeGithubProjectApi { response.await() }
        val viewModel = createViewModel(api, FakeGithubProjectDao(cachedEntity()))

        viewModel.refresh()
        viewModel.refresh()
        idleMainLooper()
        viewModel.refresh()

        assertEquals(1, api.requestCount)
        assertTrue(viewModel.uiState.value.refreshing)
        assertFalse(viewModel.uiState.value.loading)

        response.complete(networkResponse())
        idleMainLooper()
        assertFalse(viewModel.uiState.value.refreshing)

        viewModel.refresh()
        idleMainLooper()

        assertEquals(2, api.requestCount)
        assertEquals(ProjectDataSource.NETWORK, viewModel.uiState.value.source)
        assertFalse(viewModel.uiState.value.refreshing)
    }

    @Test
    fun refresh_whenScopeIsCancelled_finishesProgressWithoutPageFailure() {
        val response = CompletableDeferred<GithubProjectResp>()
        val api = FakeGithubProjectApi { response.await() }
        val viewModel = createViewModel(api, FakeGithubProjectDao(cachedEntity()))
        val cachedProject = viewModel.uiState.value.project

        viewModel.refresh()
        idleMainLooper()
        assertTrue(viewModel.uiState.value.refreshing)

        viewModel.viewModelScope.cancel()
        idleMainLooper()

        val state = viewModel.uiState.value
        assertEquals(cachedProject, state.project)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.failure)
    }

    @Test
    fun initialLoad_whenNetworkIsCancelled_finishesInitialLoadingWithoutPageFailure() {
        val response = CompletableDeferred<GithubProjectResp>()
        val api = FakeGithubProjectApi { response.await() }
        val viewModel = createViewModel(api)

        assertTrue(viewModel.uiState.value.loading)
        assertTrue(viewModel.uiState.value.refreshing)

        viewModel.viewModelScope.cancel()
        idleMainLooper()

        val state = viewModel.uiState.value
        assertNull(state.project)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.failure)
    }

    @Test
    fun refresh_whenHttpRequestFails_retainsPageSpecificErrorClassification() {
        val expectedFailures = mapOf(
            403 to ProjectLoadFailure.UNEXPECTED,
            429 to ProjectLoadFailure.RATE_LIMITED,
            500 to ProjectLoadFailure.SERVER,
            503 to ProjectLoadFailure.SERVER,
            404 to ProjectLoadFailure.UNEXPECTED,
        )

        expectedFailures.forEach { (statusCode, expectedFailure) ->
            val api = FakeGithubProjectApi {
                throw HttpException(Response.error<GithubProjectResp>(statusCode, "{}".toResponseBody()))
            }
            val viewModel = createViewModel(api, FakeGithubProjectDao(cachedEntity()))

            viewModel.refresh()
            idleMainLooper()

            val state = viewModel.uiState.value
            assertEquals("Unexpected mapping for HTTP $statusCode", expectedFailure, state.failure)
            assertEquals(ProjectDataSource.CACHE, state.source)
            assertEquals(10, state.project?.starCount)
            assertFalse(state.loading)
            assertFalse(state.refreshing)
        }
    }

    @Test
    fun refresh_whenForbiddenHasPrimaryRateLimitHeaders_displaysRateLimitFailure() {
        assertForbiddenFailure(
            Headers.headersOf("X-RateLimit-Remaining", "0"),
            ProjectLoadFailure.RATE_LIMITED,
        )
    }

    @Test
    fun refresh_whenForbiddenHasSecondaryRateLimitHeaders_displaysRateLimitFailure() {
        assertForbiddenFailure(
            Headers.headersOf("X-RateLimit-Remaining", "10", "Retry-After", "60"),
            ProjectLoadFailure.RATE_LIMITED,
        )
    }

    @Test
    fun refresh_whenForbiddenHasNoValidRateLimitEvidence_displaysGeneralFailure() {
        listOf("invalid", "-1", "").forEach { retryAfter: String ->
            assertForbiddenFailure(
                Headers.headersOf("X-RateLimit-Remaining", "10", "Retry-After", retryAfter),
                ProjectLoadFailure.UNEXPECTED,
            )
        }
    }

    @Test
    fun refresh_whenCacheWriteFails_retainsDisplayedDataAndReportsFailure() {
        val dao = FakeGithubProjectDao(
            entity = cachedEntity(),
            writeFailure = IllegalStateException("Cache write failed"),
        )
        val viewModel = createViewModel(dao = dao)

        viewModel.refresh()
        idleMainLooper()

        val state = viewModel.uiState.value
        assertEquals(ProjectDataSource.CACHE, state.source)
        assertEquals(10, state.project?.starCount)
        assertEquals(10, dao.entity?.starCount)
        assertEquals(ProjectLoadFailure.UNEXPECTED, state.failure)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
    }

    private fun createViewModel(
        api: FakeGithubProjectApi = FakeGithubProjectApi(),
        dao: FakeGithubProjectDao = FakeGithubProjectDao(),
        visible: Boolean = true,
    ): ProjectViewModel {
        val repository = GithubProjectRepository(api, dao, currentTimeMillis = { currentTime })
        val viewModel = ProjectViewModel(repository)
        viewModels += viewModel
        if (visible) {
            viewModel.refreshIfStale()
        }
        idleMainLooper()
        return viewModel
    }

    private fun assertForbiddenFailure(headers: Headers, expectedFailure: ProjectLoadFailure) {
        val response = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://api.github.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .headers(headers)
            .build()
        val api = FakeGithubProjectApi {
            throw HttpException(Response.error<GithubProjectResp>("{}".toResponseBody(), response))
        }
        val viewModel = createViewModel(api, FakeGithubProjectDao(cachedEntity()))

        viewModel.refresh()
        idleMainLooper()

        assertEquals(expectedFailure, viewModel.uiState.value.failure)
        assertEquals(ProjectDataSource.CACHE, viewModel.uiState.value.source)
        assertFalse(viewModel.uiState.value.refreshing)
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * 通过可挂起响应控制完成顺序, 不依赖真实线程延时.
     */
    private class FakeGithubProjectApi(
        private val response: suspend () -> GithubProjectResp = { networkResponse() },
    ) : GithubProjectApi {

        var requestCount: Int = 0
            private set

        override suspend fun getProject(): GithubProjectResp {
            requestCount += 1
            return response()
        }
    }

    /**
     * 保存当前缓存, 并可在读写边界抛出指定异常.
     */
    private class FakeGithubProjectDao(
        var entity: GithubProjectEntity? = null,
        private val readFailure: Exception? = null,
        private val writeFailure: Exception? = null,
    ) : GithubProjectDao {

        var readCount: Int = 0
            private set

        override suspend fun findByFullName(fullName: String): GithubProjectEntity? {
            readCount += 1
            readFailure?.let { throw it }
            return entity?.takeIf { it.fullName == fullName }
        }

        override suspend fun upsert(entity: GithubProjectEntity) {
            writeFailure?.let { throw it }
            this.entity = entity
        }

        override suspend fun deleteByFullName(fullName: String) {
            if (entity?.fullName == fullName) {
                entity = null
            }
        }
    }

    private companion object {

        const val CURRENT_TIME: Long = 2_000_000L
        const val CACHE_FRESHNESS_MILLIS: Long = 15L * 60L * 1_000L
        const val EXPIRED_AT: Long = CURRENT_TIME - CACHE_FRESHNESS_MILLIS

        fun cachedEntity(cachedAt: Long = CURRENT_TIME): GithubProjectEntity = GithubProjectEntity(
            repositoryId = 1L,
            fullName = "zhoubuilds/android-starter",
            description = "Starter",
            htmlUrl = "https://github.com/zhoubuilds/android-starter",
            starCount = 10,
            forkCount = 2,
            openItemCount = 1,
            language = "Kotlin",
            licenseName = null,
            defaultBranch = "main",
            pushedAt = null,
            privateRepository = false,
            cachedAt = cachedAt,
        )

        fun networkResponse(): GithubProjectResp = GithubProjectResp(
            id = 1L,
            fullName = "zhoubuilds/android-starter",
            description = "Starter",
            htmlUrl = "https://github.com/zhoubuilds/android-starter",
            starCount = 11,
            forkCount = 3,
            openItemCount = 2,
            language = "Kotlin",
            license = null,
            defaultBranch = "main",
            pushedAt = null,
            privateRepository = false,
        )
    }
}
