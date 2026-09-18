package com.whisper.starter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisper.feature.project.data.database.GithubProjectDao
import com.whisper.feature.project.data.database.GithubProjectEntity
import com.whisper.habitat.runtime.HabitatFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证 app 数据库通过 Habitat 提供 Project Dao.
 *
 * 测试访问 Application 初始化的真实数据库, 使用独立仓库标识并在 finally 中清理,
 * 避免覆盖或删除项目页已有的仓库缓存.
 *
 * @author whisper
 * @since 2026/09/14
 */
@RunWith(AndroidJUnit4::class)
class HabitatDatabaseInstrumentedTest {

    @Test
    fun getGithubProjectDao_whenApplicationInitialized_supportsRoomRoundTrip() = runBlocking {
        val dao: GithubProjectDao? = HabitatFactory.get<GithubProjectDao>()
        assertNotNull(dao)
        val requiredDao: GithubProjectDao = checkNotNull(dao)
        val entity = GithubProjectEntity(
            repositoryId = -1L,
            fullName = TEST_PROJECT_FULL_NAME,
            description = "Habitat provider test",
            htmlUrl = "https://example.invalid/habitat-test",
            starCount = 1,
            forkCount = 2,
            openItemCount = 3,
            language = "Kotlin",
            licenseName = "Test",
            defaultBranch = "main",
            pushedAt = "2026-09-14T00:00:00Z",
            privateRepository = false,
            cachedAt = 1L,
        )
        try {
            requiredDao.upsert(entity)

            val stored: GithubProjectEntity? = requiredDao.findByFullName(TEST_PROJECT_FULL_NAME)

            assertEquals(entity, stored)
        } finally {
            requiredDao.deleteByFullName(TEST_PROJECT_FULL_NAME)
        }
    }

    private companion object {

        const val TEST_PROJECT_FULL_NAME: String = "habitat/provider-test"
    }
}
