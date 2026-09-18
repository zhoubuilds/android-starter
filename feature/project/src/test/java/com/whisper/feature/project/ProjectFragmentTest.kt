package com.whisper.feature.project

import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.whisper.feature.project.data.GithubProjectRepository
import com.whisper.feature.project.data.database.GithubProjectDao
import com.whisper.feature.project.data.database.GithubProjectEntity
import com.whisper.feature.project.data.network.GithubProjectApi
import com.whisper.feature.project.data.network.GithubProjectResp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.IOException

/**
 * 验证保留 Fragment 的 Tab 可见切换和 Activity 重建不会重复启动加载.
 *
 * @author whisper
 * @since 2026/09/17
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ProjectFragmentTest {

    private var controller: ActivityController<TestActivity>? = null
    private var currentTime: Long = 2_000_000L
    private var requestCount: Int = 0
    private var readCount: Int = 0
    private var networkFailure: Boolean = false
    private var cachedEntity: GithubProjectEntity? = null

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun pageVisibility_whenInitiallyHidden_waitsUntilShownBeforeLoading() {
        val fragment = createFragment(hidden = true)

        assertEquals(0, readCount)
        assertEquals(0, requestCount)

        fragment.parentFragmentManager.beginTransaction().show(fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, readCount)
        assertEquals(1, requestCount)
    }

    @Test
    fun pageVisibility_whenReturningToExpiredProject_refreshesWithoutRecreatingFragment() {
        val fragment = createFragment()
        assertEquals(1, requestCount)

        fragment.parentFragmentManager.beginTransaction().hide(fragment).commitNow()
        currentTime += 15L * 60L * 1_000L
        fragment.parentFragmentManager.beginTransaction().show(fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, requestCount)
        assertEquals(1, readCount)
        assertFalse(fragment.isHidden)
    }

    @Test
    fun activityRecreation_afterInitialFailure_keepsFailureWithoutAutomaticRetry() {
        networkFailure = true
        createFragment()
        assertEquals(1, requestCount)

        checkNotNull(controller).recreate()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, requestCount)
        assertEquals(1, readCount)
    }

    private fun createFragment(hidden: Boolean = false): ProjectFragment {
        val activityController = Robolectric.buildActivity(TestActivity::class.java)
        val activity = activityController.get()
        activityController.create()
        controller = activityController

        val fragment = ProjectFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "project")
            .apply {
                if (hidden) {
                    hide(fragment)
                }
            }
            .commitNow()
        ViewModelProvider(fragment, projectViewModelFactory())[ProjectViewModel::class.java]
        activityController.start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    private fun projectViewModelFactory(): ViewModelProvider.Factory {
        val api = object : GithubProjectApi {
            override suspend fun getProject(): GithubProjectResp {
                requestCount += 1
                if (networkFailure) {
                    throw IOException("Network unavailable")
                }
                return GithubProjectResp(
                    id = 1L,
                    fullName = "zhoubuilds/android-starter",
                    description = "Starter",
                    htmlUrl = "https://github.com/zhoubuilds/android-starter",
                    starCount = requestCount,
                    forkCount = 0,
                    openItemCount = 0,
                    language = "Kotlin",
                    license = null,
                    defaultBranch = "main",
                    pushedAt = null,
                    privateRepository = false,
                )
            }
        }
        val dao = object : GithubProjectDao {
            override suspend fun findByFullName(fullName: String): GithubProjectEntity? {
                readCount += 1
                return cachedEntity
            }

            override suspend fun upsert(entity: GithubProjectEntity) {
                cachedEntity = entity
            }

            override suspend fun deleteByFullName(fullName: String) {
                cachedEntity = null
            }
        }
        val repository = GithubProjectRepository(api, dao, currentTimeMillis = { currentTime })
        return object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelClass.cast(ProjectViewModel(repository))!!
        }
    }

    /**
     * Activity 重建时继续提供项目布局所需的 Material 主题.
     */
    class TestActivity : FragmentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
            super.onCreate(savedInstanceState)
        }
    }
}
