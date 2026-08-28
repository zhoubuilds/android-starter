package com.whisper.aster.runtime.registry

import android.app.Activity
import com.whisper.aster.runtime.Capability

/**
 * Aster 模块注册入口.
 *
 * Registry Installer 只能在 Aster 初始化期间通过该接口提交路由和能力定义.
 * KSP 或手写 Registry 负责保证映射内容合法, Runtime 在目标首次使用时执行防御性检查.
 *
 * @aegis 保护生成代码使用的注册 API, 重复声明失败和注册阶段边界.
 *
 * @author whisper
 * @since 2026/07/23
 */
interface AsterRegistrar {

    /**
     * 注册一个 Activity 路由.
     *
     * @param path 全局唯一的路由路径.
     * @param activityClass 路由目标 Activity 类型.
     * @exception IllegalArgumentException 路径与已有路由冲突时抛出.
     * @exception IllegalStateException 注册阶段已经结束时抛出.
     */
    fun registerRoute(path: String, activityClass: Class<out Activity>)

    /**
     * 注册一个能力定义.
     *
     * @param name 全局唯一的能力名.
     * @param implClass 能力实现类.
     * @param singleton 是否按能力名缓存实例.
     * @exception IllegalArgumentException 能力名已经注册时抛出.
     * @exception IllegalStateException 注册阶段已经结束时抛出.
     */
    fun registerCapability(
        name: String,
        implClass: Class<out Capability>,
        singleton: Boolean
    )
}
