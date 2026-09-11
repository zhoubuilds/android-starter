package com.whisper.kit.widget.refreshloadlayout

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import com.whisper.kit.test.R

/**
 * 为刷新加载的触摸分发和窗口生命周期回归提供真实窗口.
 *
 * @author whisper
 * @since 2026/09/11
 */
class KitRefreshLoadLayoutTestActivity : Activity() {

    lateinit var host: FrameLayout
        private set

    lateinit var refreshLayout: KitRefreshLoadLayout
        private set

    lateinit var scrollView: NestedScrollView
        private set

    val actions: MutableList<KitRefreshLoadLayout.RefreshLoadAction> = mutableListOf()

    var contentClickCount: Int = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scrollView = NestedScrollView(this).apply {
            id = R.id.kit_test_refresh_load_content
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                FrameLayout(context).apply {
                    minimumHeight = 2_000
                    addView(
                        Button(context).apply {
                            id = R.id.kit_test_refresh_load_content_button
                            isAllCaps = false
                            setText(R.string.kit_test_refresh_load_content_button)
                            setOnClickListener { contentClickCount++ }
                        },
                        FrameLayout.LayoutParams(280, 96),
                    )
                },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        refreshLayout = KitRefreshLoadLayout(this).apply {
            id = R.id.kit_test_refresh_load_container
            dragResistance = 1f
            refreshTriggerDistance = 40
            loadMoreTriggerDistance = 40
            maxDragDistance = 1_000
            animationDurationMillis = 120L
            addView(
                View(context),
                KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60).apply {
                    role = KitRefreshLoadLayout.ChildRole.Header
                },
            )
            addView(scrollView, contentLayoutParams())
            addView(
                View(context),
                KitRefreshLoadLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60).apply {
                    role = KitRefreshLoadLayout.ChildRole.Footer
                },
            )
            setOnRefreshLoadListener { action: KitRefreshLoadLayout.RefreshLoadAction -> actions += action }
        }
        intent.getStringExtra(EXTRA_ACTIVE_ACTION)?.let { actionName: String ->
            val action: KitRefreshLoadLayout.RefreshLoadAction? = KitRefreshLoadLayout.RefreshLoadAction.entries
                .firstOrNull { it.name == actionName }
            refreshLayout.setActiveAction(action, animate = false)
        }
        host = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(refreshLayout, FrameLayout.LayoutParams(320, 600))
        }
        setContentView(host)
    }

    fun useDirectContent() {
        refreshLayout.removeView(scrollView)
        refreshLayout.addView(View(this).apply { isClickable = true }, contentLayoutParams())
    }

    fun useFrameworkScrollContent(): ScrollView {
        val frameworkScrollView: ScrollView = ScrollView(this).apply {
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                View(context).apply { minimumHeight = 2_000 },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        refreshLayout.removeView(scrollView)
        refreshLayout.addView(frameworkScrollView, contentLayoutParams())
        return frameworkScrollView
    }

    private fun contentLayoutParams(): KitRefreshLoadLayout.LayoutParams {
        return KitRefreshLoadLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    companion object {
        const val EXTRA_ACTIVE_ACTION: String = "kit_test_active_action"
    }
}
