package com.whisper.feature.project.data

import com.whisper.architecture.model.domain.Business
import com.whisper.feature.project.data.database.GithubProjectDao
import com.whisper.feature.project.data.database.GithubProjectEntity
import com.whisper.feature.project.data.network.GithubLicenseResp
import com.whisper.feature.project.data.network.GithubProjectApi
import com.whisper.feature.project.data.network.GithubProjectResp
import com.whisper.feature.project.model.GithubProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 验证 GitHub 项目缓存、业务管线与网络刷新边界.
 */
class GithubProjectRepositoryTest {

    @Test
    fun getCachedProject_whenEntityExists_mapsStoredProject() = runBlocking {
        val dao = FakeGithubProjectDao(entity = cachedEntity())
        val repository = GithubProjectRepository(
            api = FakeGithubProjectApi(networkResponse()),
            dao = dao,
            currentTimeMillis = { CURRENT_TIME },
        )

        val outcome: Business.Outcome<Unit, GithubProject?> = repository.getCachedProject().single()
        val project: GithubProject? = (outcome as Business.Success).data

        assertEquals(Unit, outcome.meta)
        assertEquals(PROJECT_ID, project?.repositoryId)
        assertEquals(PROJECT_FULL_NAME, project?.fullName)
        assertEquals(CACHED_AT, project?.cachedAt)
        assertTrue(project?.let(repository::isFresh) == true)
    }

    @Test
    fun getCachedProject_whenCacheIsEmpty_emitsSuccessfulNull() = runBlocking {
        val repository = repository()

        assertEquals(Business.Success(Unit, null), repository.getCachedProject().single())
    }

    @Test
    fun getCachedProject_whenCollectedAgain_readsCurrentCacheEachTime() = runBlocking {
        val dao = FakeGithubProjectDao()
        val repository = repository(dao = dao)
        val projectFlow: Flow<Business.Outcome<Unit, GithubProject?>> = repository.getCachedProject()

        assertEquals(0, dao.readCount)
        assertEquals(Business.Success(Unit, null), projectFlow.single())

        dao.entity = cachedEntity()
        val outcome = projectFlow.single() as Business.Success

        assertEquals(2, dao.readCount)
        assertEquals(CACHED_AT, outcome.data?.cachedAt)
    }

    @Test
    fun getCachedProject_whenDaoFails_preservesOriginalException() = runBlocking {
        val exception = IOException("Cache read failed")
        val repository = repository(dao = FakeGithubProjectDao(readFailure = exception))

        val outcome = repository.getCachedProject().single() as Business.Failure

        assertSame(exception, outcome.exception)
        assertEquals(Unit, outcome.meta)
        assertNull(outcome.data)
    }

    @Test
    fun getCachedProject_whenDaoIsCancelled_propagatesCancellation() {
        val exception = CancellationException("Cache read cancelled")
        val repository = repository(dao = FakeGithubProjectDao(readFailure = exception))

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { repository.getCachedProject().toList() }
        }

        assertSame(exception, thrown)
    }

    @Test
    fun getCachedProject_whenCollectorThrows_doesNotConvertDownstreamFailure() {
        val exception = IllegalArgumentException("Collector failed")
        val repository = repository()
        var emissionCount = 0

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.getCachedProject().collect {
                    emissionCount += 1
                    throw exception
                }
            }
        }

        assertSame(exception, thrown)
        assertEquals(1, emissionCount)
    }

    @Test
    fun refreshProject_whenNetworkSucceeds_persistsMappedProject() = runBlocking {
        val dao = FakeGithubProjectDao()
        val repository = GithubProjectRepository(
            api = FakeGithubProjectApi(networkResponse()),
            dao = dao,
            currentTimeMillis = { CURRENT_TIME },
        )

        val emissions: List<Business<Unit, GithubProject?>> = repository.refreshProject().toList()
        val outcome = emissions[1] as Business.Success
        val project = checkNotNull(outcome.data)

        assertEquals(2, emissions.size)
        assertSame(Business.Loading, emissions[0])
        assertEquals(Unit, outcome.meta)
        assertEquals("Apache-2.0", project.licenseName)
        assertEquals(CURRENT_TIME, project.cachedAt)
        assertEquals(project.repositoryId, dao.entity?.repositoryId)
        assertEquals(project.fullName, dao.entity?.fullName)
    }

    @Test
    fun refreshProject_whenCollectedAgain_repeatsNetworkAndCacheWrite() = runBlocking {
        val api = FakeGithubProjectApi(networkResponse())
        val dao = FakeGithubProjectDao()
        var now = CURRENT_TIME
        val repository = GithubProjectRepository(api, dao, currentTimeMillis = { now })
        val projectFlow: Flow<Business<Unit, GithubProject?>> = repository.refreshProject()

        assertEquals(0, api.requestCount)
        assertEquals(0, dao.writeCount)

        projectFlow.toList()
        now += 1_000L
        val emissions = projectFlow.toList()

        assertEquals(2, api.requestCount)
        assertEquals(2, dao.writeCount)
        assertSame(Business.Loading, emissions.first())
        assertEquals(now, (emissions.last() as Business.Success).data?.cachedAt)
        assertEquals(now, dao.entity?.cachedAt)
    }

    @Test
    fun refreshProject_whenNetworkFails_emitsFailureAndPreservesCache() = runBlocking {
        val exception = IOException("Network unavailable")
        val cachedProject = cachedEntity()
        val dao = FakeGithubProjectDao(entity = cachedProject)
        val repository = repository(
            api = FakeGithubProjectApi(networkResponse(), failure = exception),
            dao = dao,
        )

        val emissions = repository.refreshProject().toList()

        assertEquals(listOf(Business.Loading, Business.Failure(exception, Unit, null)), emissions)
        assertSame(exception, (emissions.last() as Business.Failure).exception)
        assertEquals(cachedProject, dao.entity)
        assertEquals(0, dao.writeCount)
    }

    @Test
    fun refreshProject_whenResponseCannotBeMapped_emitsFailureWithoutWritingCache() = runBlocking {
        val dao = FakeGithubProjectDao(entity = cachedEntity())
        val repository = repository(
            api = FakeGithubProjectApi(networkResponse().copy(id = null)),
            dao = dao,
        )

        val emissions = repository.refreshProject().toList()
        val outcome = emissions.last() as Business.Failure

        assertEquals(2, emissions.size)
        assertSame(Business.Loading, emissions.first())
        assertTrue(outcome.exception is IllegalStateException)
        assertEquals(Unit, outcome.meta)
        assertNull(outcome.data)
        assertEquals(0, dao.writeCount)
        assertEquals(CACHED_AT, dao.entity?.cachedAt)
    }

    @Test
    fun refreshProject_whenCacheWriteFails_doesNotPublishUnsavedNetworkData() = runBlocking {
        val exception = IOException("Cache write failed")
        val cachedProject = cachedEntity()
        val dao = FakeGithubProjectDao(entity = cachedProject, writeFailure = exception)
        val repository = repository(dao = dao)

        val emissions = repository.refreshProject().toList()

        assertEquals(listOf(Business.Loading, Business.Failure(exception, Unit, null)), emissions)
        assertSame(exception, (emissions.last() as Business.Failure).exception)
        assertEquals(cachedProject, dao.entity)
        assertEquals(1, dao.writeCount)
    }

    @Test
    fun refreshProject_whenNetworkIsCancelled_propagatesWithoutFailureEmission() {
        val exception = CancellationException("Network request cancelled")
        val repository = repository(api = FakeGithubProjectApi(networkResponse(), failure = exception))
        val emissions: MutableList<Business<Unit, GithubProject?>> = mutableListOf()

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { repository.refreshProject().toList(emissions) }
        }

        assertSame(exception, thrown)
        assertEquals(listOf(Business.Loading), emissions)
    }

    @Test
    fun refreshProject_whenCacheWriteIsCancelled_propagatesWithoutSuccessEmission() {
        val exception = CancellationException("Cache write cancelled")
        val dao = FakeGithubProjectDao(entity = cachedEntity(), writeFailure = exception)
        val repository = repository(dao = dao)
        val emissions: MutableList<Business<Unit, GithubProject?>> = mutableListOf()

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { repository.refreshProject().toList(emissions) }
        }

        assertSame(exception, thrown)
        assertEquals(listOf(Business.Loading), emissions)
        assertEquals(CACHED_AT, dao.entity?.cachedAt)
    }

    @Test
    fun refreshProject_whenCollectorThrows_doesNotConvertDownstreamFailure() {
        val exception = IllegalArgumentException("Collector failed")
        val repository = repository()
        val emissions: MutableList<Business<Unit, GithubProject?>> = mutableListOf()

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.refreshProject().collect { result ->
                    emissions += result
                    if (result is Business.Success) {
                        throw exception
                    }
                }
            }
        }

        assertSame(exception, thrown)
        assertEquals(2, emissions.size)
        assertSame(Business.Loading, emissions.first())
        assertTrue(emissions.last() is Business.Success)
    }

    @Test
    fun isFresh_whenCacheAgeReachesThreshold_returnsFalse() {
        val repository = GithubProjectRepository(
            api = FakeGithubProjectApi(networkResponse()),
            dao = FakeGithubProjectDao(),
            currentTimeMillis = { CACHED_AT + FIFTEEN_MINUTES_MILLIS },
        )

        assertFalse(repository.isFresh(cachedEntity().toTestProject()))
    }

    private fun repository(
        api: FakeGithubProjectApi = FakeGithubProjectApi(networkResponse()),
        dao: FakeGithubProjectDao = FakeGithubProjectDao(),
    ): GithubProjectRepository = GithubProjectRepository(api, dao, currentTimeMillis = { CURRENT_TIME })

    /**
     * 提供可重复调用的响应或异常, 并记录实际请求次数.
     */
    private class FakeGithubProjectApi(
        private val response: GithubProjectResp,
        private val failure: Exception? = null,
    ) : GithubProjectApi {

        var requestCount: Int = 0
            private set

        override suspend fun getProject(): GithubProjectResp {
            requestCount += 1
            failure?.let { throw it }
            return response
        }
    }

    /**
     * 模拟缓存记录与读写失败, 保持失败写入不修改原记录.
     */
    private class FakeGithubProjectDao(
        var entity: GithubProjectEntity? = null,
        private val readFailure: Exception? = null,
        private val writeFailure: Exception? = null,
    ) : GithubProjectDao {

        var readCount: Int = 0
            private set
        var writeCount: Int = 0
            private set

        override suspend fun findByFullName(fullName: String): GithubProjectEntity? {
            readCount += 1
            readFailure?.let { throw it }
            return entity?.takeIf { stored: GithubProjectEntity -> stored.fullName == fullName }
        }

        override suspend fun upsert(entity: GithubProjectEntity) {
            writeCount += 1
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

        const val PROJECT_ID: Long = 1L
        const val PROJECT_FULL_NAME: String = "zhoubuilds/android-starter"
        const val CACHED_AT: Long = 1_000L
        const val CURRENT_TIME: Long = CACHED_AT + 1_000L
        const val FIFTEEN_MINUTES_MILLIS: Long = 15L * 60L * 1_000L

        fun cachedEntity(): GithubProjectEntity =
            GithubProjectEntity(
                repositoryId = PROJECT_ID,
                fullName = PROJECT_FULL_NAME,
                description = "Starter",
                htmlUrl = "https://github.com/zhoubuilds/android-starter",
                starCount = 10,
                forkCount = 2,
                openItemCount = 1,
                language = "Kotlin",
                licenseName = "Apache-2.0",
                defaultBranch = "main",
                pushedAt = "2026-09-14T00:00:00Z",
                privateRepository = false,
                cachedAt = CACHED_AT,
            )

        fun networkResponse(): GithubProjectResp =
            GithubProjectResp(
                id = PROJECT_ID,
                fullName = PROJECT_FULL_NAME,
                description = "Starter",
                htmlUrl = "https://github.com/zhoubuilds/android-starter",
                starCount = 11,
                forkCount = 3,
                openItemCount = 2,
                language = "Kotlin",
                license = GithubLicenseResp(
                    name = "Apache License 2.0",
                    spdxId = "Apache-2.0",
                ),
                defaultBranch = "main",
                pushedAt = "2026-09-14T00:00:00Z",
                privateRepository = false,
            )
    }
}

private fun GithubProjectEntity.toTestProject(): GithubProject =
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
