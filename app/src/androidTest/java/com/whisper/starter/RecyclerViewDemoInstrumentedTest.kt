package com.whisper.starter

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.whisper.feature.function.recyclerview.RecyclerViewDemoActivity
import com.whisper.kit.recyclerview.decoration.RegularItemDividerDecoration
import com.whisper.kit.recyclerview.decoration.StaggeredFullSpanProvider
import com.whisper.kit.recyclerview.decoration.StaggeredItemDividerDecoration
import com.whisper.starter.ui.activity.MainActivity
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.whisper.feature.function.R as FunctionR

/**
 * 验证分割线演示的布局切换、装饰器匹配与配置变化恢复.
 *
 * @author whisper
 * @since 2026/09/15
 */
@RunWith(AndroidJUnit4::class)
class RecyclerViewDemoInstrumentedTest {

    @Test
    fun openRecyclerDemo_whenBackPressed_returnsToCatalog() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(FunctionR.string.function_recycler_title)).perform(click())
            onView(withId(FunctionR.id.function_recycler_root)).check(matches(isDisplayed()))
            pressBack()
            onView(withId(FunctionR.id.function_catalog_root)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun switchLayout_inAllOrientations_installsOnlyTheMatchingDivider() {
        ActivityScenario.launch(RecyclerViewDemoActivity::class.java).use { scenario ->
            val layoutButtons = listOf(
                FunctionR.id.function_recycler_linear,
                FunctionR.id.function_recycler_grid,
                FunctionR.id.function_recycler_staggered,
                FunctionR.id.function_recycler_grid,
                FunctionR.id.function_recycler_linear,
            )
            for (orientation in listOf(RecyclerView.VERTICAL, RecyclerView.HORIZONTAL)) {
                val orientationButtonId: Int = if (orientation == RecyclerView.VERTICAL) {
                    FunctionR.id.function_recycler_vertical
                } else {
                    FunctionR.id.function_recycler_horizontal
                }
                onView(withId(orientationButtonId)).perform(click())
                for (buttonId in layoutButtons) {
                    onView(withId(buttonId)).perform(click()).check(matches(isChecked()))
                    scenario.onActivity { activity ->
                        val list = activity.findViewById<RecyclerView>(FunctionR.id.function_recycler_list)
                        assertEquals(24, list.adapter?.itemCount)
                        assertTrue("RecyclerView has no laid out items", list.childCount > 0)
                        assertEquals(1, list.itemDecorationCount)
                        when (buttonId) {
                            FunctionR.id.function_recycler_linear -> {
                                assertEquals(LinearLayoutManager::class.java, list.layoutManager?.javaClass)
                                assertEquals(orientation, (list.layoutManager as LinearLayoutManager).orientation)
                                assertTrue(list.getItemDecorationAt(0) is RegularItemDividerDecoration)
                            }
                            FunctionR.id.function_recycler_grid -> {
                                val manager = list.layoutManager as GridLayoutManager
                                assertEquals(orientation, manager.orientation)
                                assertEquals(2, manager.spanCount)
                                assertTrue(list.getItemDecorationAt(0) is RegularItemDividerDecoration)
                            }
                            FunctionR.id.function_recycler_staggered -> {
                                val manager = list.layoutManager as StaggeredGridLayoutManager
                                assertEquals(orientation, manager.orientation)
                                assertEquals(2, manager.spanCount)
                                assertTrue(list.getItemDecorationAt(0) is StaggeredItemDividerDecoration)
                                val provider = list.adapter as StaggeredFullSpanProvider
                                for (index in 0 until list.childCount) {
                                    val child = list.getChildAt(index)
                                    val params = child.layoutParams as StaggeredGridLayoutManager.LayoutParams
                                    assertEquals(
                                        provider.isFullSpan(list.getChildAdapterPosition(child)),
                                        params.isFullSpan,
                                    )
                                }
                            }
                        }
                    }
                    repeat(2) {
                        onView(withId(FunctionR.id.function_recycler_dividers)).perform(click())
                        scenario.onActivity { activity ->
                            val list = activity.findViewById<RecyclerView>(FunctionR.id.function_recycler_list)
                            assertEquals(0, list.itemDecorationCount)
                        }
                        onView(withId(FunctionR.id.function_recycler_dividers)).perform(click())
                        scenario.onActivity { activity ->
                            val list = activity.findViewById<RecyclerView>(FunctionR.id.function_recycler_list)
                            assertEquals(1, list.itemDecorationCount)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun recreate_whenOptionsAndScrollChange_restoresThePreview() {
        ActivityScenario.launch(RecyclerViewDemoActivity::class.java).use { scenario ->
            onView(withId(FunctionR.id.function_recycler_staggered)).perform(click())
            onView(withId(FunctionR.id.function_recycler_horizontal)).perform(click())
            onView(withId(FunctionR.id.function_recycler_dividers)).perform(click())
            scenario.onActivity { activity ->
                activity.findViewById<RecyclerView>(FunctionR.id.function_recycler_list).scrollToPosition(12)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var firstVisiblePosition = 0
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(FunctionR.id.function_recycler_list)
                firstVisiblePosition = (list.layoutManager as StaggeredGridLayoutManager)
                    .findFirstVisibleItemPositions(null).minOrNull() ?: 0
                assertTrue("Scroll position did not advance", firstVisiblePosition > 0)
            }

            scenario.recreate()

            onView(withId(FunctionR.id.function_recycler_staggered)).check(matches(isChecked()))
            onView(withId(FunctionR.id.function_recycler_horizontal)).check(matches(isChecked()))
            onView(withId(FunctionR.id.function_recycler_dividers)).check(matches(not(isChecked())))
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(FunctionR.id.function_recycler_list)
                val manager = list.layoutManager as StaggeredGridLayoutManager
                assertEquals(RecyclerView.HORIZONTAL, manager.orientation)
                assertEquals(0, list.itemDecorationCount)
                assertEquals(firstVisiblePosition, manager.findFirstVisibleItemPositions(null).minOrNull())
            }
        }
    }
}
