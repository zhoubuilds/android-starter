package com.whisper.aster.runtime.registry

/**
 * 模块路由和能力注册器契约.
 *
 * KSP 为每个模块生成实现类, runtime 通过 Manifest 索引定位并调用实现.
 *
 * @aegis 保护生成 Registry 与 Runtime 之间的安装器 ABI 和调用契约.
 * @author whisper
 * @since 2026/07/20
 */
interface AsterRegistryInstaller {

    /**
     * 将当前模块的路由和能力提交到注册会话.
     *
     * @param registrar 当前初始化过程的模块注册入口.
     */
    fun install(registrar: AsterRegistrar)
}
