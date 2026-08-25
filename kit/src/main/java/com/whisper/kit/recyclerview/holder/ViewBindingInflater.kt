package com.whisper.kit.recyclerview.holder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

/**
 * ViewBinding inflate 方法适配接口.
 *
 * 该接口用于接收生成 binding 类的 `inflate(LayoutInflater, ViewGroup, Boolean)` 方法引用.
 *
 * @author whisper
 * @since 2026/07/30
 */
fun interface ViewBindingInflater<VB : ViewBinding> {

    /**
     * 创建 ViewBinding 实例.
     *
     * @param inflater 布局加载器.
     * @param parent item 所属父容器.
     * @param attachToParent 是否立即挂载到父容器.
     * @return 创建完成的 ViewBinding.
     */
    fun inflate(inflater: LayoutInflater, parent: ViewGroup, attachToParent: Boolean): VB
}
