package com.whisper.architecture.extension

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

inline fun <reified T : ViewBinding> Activity.viewBinding(
    crossinline binder: (LayoutInflater) -> T
): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE) {
        binder(layoutInflater)
    }
}

inline fun <reified T : ViewBinding> Dialog.viewBinding(
    crossinline binder: (LayoutInflater) -> T
): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE) {
        binder(layoutInflater)
    }
}

fun <T : ViewBinding> Fragment.viewBinding(bind: (View) -> T): ReadOnlyProperty<Fragment, T> =
    object : ReadOnlyProperty<Fragment, T>, DefaultLifecycleObserver {
        private var binding: T? = null

        override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
            // 如果已经初始化，直接返回
            binding?.let { return it }

            // 检查 Fragment 视图生命周期
            val lifecycle = thisRef.viewLifecycleOwner.lifecycle
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
                throw IllegalStateException("当 Fragment 视图已销毁时，不应访问 Binding")
            }

            // 注册生命周期监听，用于自动置空
            thisRef.viewLifecycleOwner.lifecycle.addObserver(this)

            return bind(thisRef.requireView()).also { binding = it }
        }

        // 当视图生命周期进入 DESTROYED 状态时，自动清理引用
        override fun onDestroy(owner: LifecycleOwner) {
            binding = null
        }
    }



