package com.whisper.starter

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisper.aster.runtime.Aster
import com.whisper.feature.function.R as FunctionR
import com.whisper.feature.project.R as ProjectR
import com.whisper.feature.project.api.ProjectRepositoryCapability
import com.whisper.feature.settings.R as SettingsR
import com.whisper.starter.ui.activity.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证首页三项底部导航与详情 Activity 的组合行为.
 *
 * @author whisper
 * @since 2026/09/14
 */
@RunWith(AndroidJUnit4::class)
class MainNavigationInstrumentedTest {

    @Test
    fun launch_whenNoStateExists_showsDemoCatalog() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.main_bottom_navigation)).check(matches(isDisplayed()))
            onView(withId(FunctionR.id.function_catalog_root)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun launch_whenEdgeToEdgeEnabled_doesNotApplyBottomInsetOutsideNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.main_bottom_navigation)).check { view, noViewFoundException ->
                if (noViewFoundException != null) {
                    throw noViewFoundException
                }
                val root = view.rootView.findViewById<android.view.View>(R.id.main_root)
                assertEquals(0, root.paddingBottom)
                assertEquals(root.height, view.bottom)
            }
        }
    }

    @Test
    fun dispatchInsets_whenSystemBarsChange_appliesEachEdgeOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.main_root)
                val navigation = activity.findViewById<View>(R.id.main_bottom_navigation)

                fun dispatchSystemBars(systemBars: Insets) {
                    val insets = WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), systemBars)
                        .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars(), systemBars)
                        .build()
                    ViewCompat.dispatchApplyWindowInsets(root, insets)
                }

                dispatchSystemBars(Insets.NONE)
                val navigationLeftPadding: Int = navigation.paddingLeft
                val navigationRightPadding: Int = navigation.paddingRight
                val navigationBottomPadding: Int = navigation.paddingBottom
                val systemBarPositions: List<Insets> = listOf(
                    Insets.of(48, 24, 0, 0),
                    Insets.of(0, 24, 48, 0),
                    Insets.of(0, 24, 0, 32),
                )

                systemBarPositions.forEach { systemBars ->
                    repeat(2) {
                        dispatchSystemBars(systemBars)

                        assertEquals(systemBars.left, root.paddingLeft)
                        assertEquals(systemBars.top, root.paddingTop)
                        assertEquals(systemBars.right, root.paddingRight)
                        assertEquals(0, root.paddingBottom)
                        assertEquals(navigationLeftPadding, navigation.paddingLeft)
                        assertEquals(navigationRightPadding, navigation.paddingRight)
                        assertEquals(navigationBottomPadding + systemBars.bottom, navigation.paddingBottom)
                    }
                }
            }
        }
    }

    @Test
    fun selectProject_whenActivityIsRecreated_restoresProjectTab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.main_navigation_project)).perform(click())
            onView(withId(ProjectR.id.project_root)).check(matches(isDisplayed()))

            scenario.recreate()

            onView(withId(ProjectR.id.project_root)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun selectSettings_whenActivityIsRecreated_restoresSettingsTab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.main_navigation_settings)).perform(click())
            onView(withId(SettingsR.id.settings_root)).check(matches(isDisplayed()))

            scenario.recreate()

            onView(withId(SettingsR.id.settings_root)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun openDemoAndAbout_whenBackPressed_returnsToTheirRootTabs() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(FunctionR.string.function_range_title)).perform(click())
            onView(withId(FunctionR.id.function_demo_root)).check(matches(isDisplayed()))
            pressBack()
            onView(withId(FunctionR.id.function_catalog_root)).check(matches(isDisplayed()))

            onView(withId(R.id.main_navigation_settings)).perform(click())
            onView(withId(SettingsR.id.settings_about)).perform(click())
            onView(withId(SettingsR.id.settings_about_root)).check(matches(isDisplayed()))
            pressBack()
            onView(withId(SettingsR.id.settings_root)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun openAbout_whenProjectCapabilityIsRegistered_showsRepositoryAddress() {
        val capability: ProjectRepositoryCapability = checkNotNull(
            Aster.resolve<ProjectRepositoryCapability>(),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.main_navigation_settings)).perform(click())
            onView(withId(SettingsR.id.settings_about)).perform(click())

            onView(withText(capability.repositoryUrl)).check(matches(isDisplayed()))
        }
    }
}
