package com.whisper.architecture.extension

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
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
    object : ReadOnlyProperty<Fragment, T> {

        private var binding: T? = null

        init {
            viewLifecycleOwnerLiveData.observe(this@viewBinding) { owner ->
                owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        binding = null
                    }
                })
            }
        }

        override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
            binding?.let { return it }
            val view = thisRef.view
                ?: throw IllegalStateException("Fragment view is not created or already destroyed")
            return bind(view).also { binding = it }
        }
    }



