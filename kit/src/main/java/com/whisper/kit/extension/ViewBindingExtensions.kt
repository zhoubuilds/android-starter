package com.whisper.kit.extension

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * 为 Activity 延迟创建 ViewBinding.
 *
 * Binding 在首次访问时通过 [binder] 创建并在 Activity 对象生命周期内缓存, 委托不主动清理.
 * 该委托不调用 `setContentView()`, 调用方仍需显式设置 `binding.root`.
 * 委托不提供线程同步, 调用方必须在主线程创建委托并访问 Binding, 建议对委托属性使用 `@get:MainThread`.
 *
 * 使用生成 Binding 类的 `inflate()` 方法引用即可, 不需要额外包装 lambda:
 *
 * ```kotlin
 * @get:MainThread
 * private val binding by viewBinding(ActivityExampleBinding::inflate)
 * ```
 */
@MainThread
inline fun <T : ViewBinding> Activity.viewBinding(
    crossinline binder: (LayoutInflater) -> T,
): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE) {
        binder(layoutInflater)
    }
}

/**
 * 为 Dialog 延迟创建 ViewBinding.
 *
 * Binding 在首次访问时通过 [binder] 创建并在 Dialog 对象生命周期内缓存, `dismiss()` 时不主动清理.
 * 该委托不调用 `setContentView()`, 调用方仍需显式设置 `binding.root`.
 * 委托不提供线程同步, 调用方必须在主线程创建委托并访问 Binding, 建议对委托属性使用 `@get:MainThread`.
 *
 * 使用生成 Binding 类的 `inflate()` 方法引用即可, 不需要额外包装 lambda:
 *
 * ```kotlin
 * @get:MainThread
 * private val binding by viewBinding(DialogExampleBinding::inflate)
 * ```
 */
@MainThread
inline fun <T : ViewBinding> Dialog.viewBinding(
    crossinline binder: (LayoutInflater) -> T,
): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE) {
        binder(layoutInflater)
    }
}

/**
 * 将 Fragment 已创建的 View 绑定为 ViewBinding.
 *
 * 仅可在 `onViewCreated()` 至 `onDestroyView()` 之间访问. Binding 跟随 View 生命周期缓存和清理,
 * Fragment View 重建后会重新调用 [bind]. 该委托不适用于仅通过 `onCreateDialog()` 创建内容且没有 View 的 DialogFragment.
 * 委托不提供线程同步, 调用方必须在主线程创建委托并访问 Binding, 建议对委托属性使用 `@get:MainThread`.
 *
 * 使用生成 Binding 类的 `bind()` 方法引用即可, 不需要额外包装 lambda:
 *
 * ```kotlin
 * @get:MainThread
 * private val binding by viewBinding(FragmentExampleBinding::bind)
 * ```
 */
@MainThread
fun <T : ViewBinding> Fragment.viewBinding(
    bind: (View) -> T,
): ReadOnlyProperty<Fragment, T> {
    return FragmentViewBindingDelegate(bind)
}

/**
 * 管理 Fragment View 生命周期内的 Binding 引用.
 */
private class FragmentViewBindingDelegate<T : ViewBinding>(
    private val bind: (View) -> T,
) : ReadOnlyProperty<Fragment, T>, DefaultLifecycleObserver {

    private var binding: T? = null
    private var bindingLifecycleOwner: LifecycleOwner? = null

    @MainThread
    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        val view: View = thisRef.view ?: throw bindingAccessException(property)
        val lifecycleOwner: LifecycleOwner = thisRef.viewLifecycleOwner
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            throw bindingAccessException(property)
        }

        binding?.let { currentBinding: T ->
            if (bindingLifecycleOwner === lifecycleOwner) {
                return currentBinding
            }
        }

        bindingLifecycleOwner?.lifecycle?.removeObserver(this)
        binding = null
        bindingLifecycleOwner = null

        val newBinding: T = bind(view)
        binding = newBinding
        bindingLifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(this)
        return newBinding
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (bindingLifecycleOwner === owner) {
            binding = null
            bindingLifecycleOwner = null
        }
    }

    private fun bindingAccessException(property: KProperty<*>): IllegalStateException {
        return IllegalStateException(
            "ViewBinding property '${property.name}' is only available between " +
                "onViewCreated() and onDestroyView()."
        )
    }
}
