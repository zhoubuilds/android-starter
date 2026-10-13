package com.whisper.starter.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.whisper.feature.function.FunctionFragment
import com.whisper.feature.project.ProjectFragment
import com.whisper.feature.settings.SettingsFragment
import com.whisper.kit.extension.applyWindowInsetsPadding
import com.whisper.kit.extension.viewBinding
import com.whisper.starter.R
import com.whisper.starter.databinding.ActivityMainBinding

/**
 * 承载 Starter 三个顶层页面的应用首页.
 *
 * 首页只负责应用级 Tab 组合和状态恢复, 不承载 feature 内部业务逻辑.
 *
 * @author whisper
 * @since 2026/09/14
 */
class MainActivity : AppCompatActivity() {

    companion object {

        private const val KEY_SELECTED_TAB_ID: String = "selected_tab_id"
        private const val TAG_FUNCTION: String = "function"
        private const val TAG_PROJECT: String = "project"
        private const val TAG_SETTINGS: String = "settings"
    }

    @get:MainThread
    private val viewBinding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(viewBinding.root)
        configureWindowInsets()
        configureBottomNavigation(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(
            KEY_SELECTED_TAB_ID,
            viewBinding.mainBottomNavigation.selectedItemId,
        )
        super.onSaveInstanceState(outState)
    }

    /**
     * 根布局只避让顶部和两侧, 底部 Insets 交由 BottomNavigationView 处理, 避免重复留白.
     */
    private fun configureWindowInsets() {
        viewBinding.mainRoot.applyWindowInsetsPadding(bottom = false)
    }

    /**
     * 恢复菜单选中项后显式显示对应 Fragment, 不依赖菜单状态赋值触发选择回调.
     */
    private fun configureBottomNavigation(savedInstanceState: Bundle?) {
        viewBinding.mainBottomNavigation.setOnItemSelectedListener { item ->
            showTab(item.itemId)
        }
        val selectedTabId: Int = savedInstanceState?.getInt(
            KEY_SELECTED_TAB_ID,
            R.id.main_navigation_function,
        ) ?: R.id.main_navigation_function
        viewBinding.mainBottomNavigation.menu.findItem(selectedTabId).isChecked = true
        showTab(selectedTabId)
    }

    /**
     * 优先复用 FragmentManager 恢复的页面, 通过 show/hide 保留 Tab 的 View 状态.
     *
     * 切换根页面不加入返回栈, 详情交给各自的独立 Activity. hide 不会降低 Fragment 生命周期,
     * 页面需要感知可见性时应同时处理隐藏状态变化.
     */
    private fun showTab(@IdRes itemId: Int): Boolean {
        val tab: MainTab = MainTab.fromItemId(itemId) ?: return false
        val fragmentManager = supportFragmentManager
        val targetFragment: Fragment = fragmentManager.findFragmentByTag(tab.fragmentTag)
            ?: tab.createFragment()
        if (fragmentManager.primaryNavigationFragment === targetFragment && !targetFragment.isHidden) {
            return true
        }

        val transaction: FragmentTransaction = fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
        fragmentManager.fragments
            .filter { fragment: Fragment ->
                fragment.id == R.id.main_fragment_container && fragment !== targetFragment
            }
            .forEach(transaction::hide)
        if (targetFragment.isAdded) {
            transaction.show(targetFragment)
        } else {
            transaction.add(
                R.id.main_fragment_container,
                targetFragment,
                tab.fragmentTag,
            )
        }
        transaction
            .setPrimaryNavigationFragment(targetFragment)
            .commit()
        return true
    }

    /**
     * 组合根持有的底部菜单与根 Fragment 映射.
     *
     * @property itemId 对应底部导航菜单项的资源 ID.
     * @property fragmentTag 用于查找已添加或由 FragmentManager 恢复的根页面.
     * @property createFragment 仅在尚无对应页面实例时调用的创建函数.
     */
    private enum class MainTab(
        @param:IdRes val itemId: Int,
        val fragmentTag: String,
        val createFragment: () -> Fragment,
    ) {
        FUNCTION(
            itemId = R.id.main_navigation_function,
            fragmentTag = TAG_FUNCTION,
            createFragment = ::FunctionFragment,
        ),
        PROJECT(
            itemId = R.id.main_navigation_project,
            fragmentTag = TAG_PROJECT,
            createFragment = ::ProjectFragment,
        ),
        SETTINGS(
            itemId = R.id.main_navigation_settings,
            fragmentTag = TAG_SETTINGS,
            createFragment = ::SettingsFragment,
        );

        companion object {

            fun fromItemId(@IdRes itemId: Int): MainTab? = entries.firstOrNull { tab: MainTab ->
                tab.itemId == itemId
            }
        }
    }
}
