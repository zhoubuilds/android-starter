package com.whisper.aster.runtime

import android.app.Activity
import android.app.Application
import android.content.Context
import com.whisper.aster.runtime.internal.CapabilityNameValidator
import com.whisper.aster.runtime.internal.LogcatErrorHandler
import com.whisper.aster.runtime.internal.RoutePathValidator
import com.whisper.aster.runtime.internal.registry.CapabilityRegistry
import com.whisper.aster.runtime.internal.registry.ManifestRegistryLoader
import com.whisper.aster.runtime.internal.registry.RegistrationSession
import com.whisper.aster.runtime.internal.registry.RegistryState
import com.whisper.aster.runtime.registry.AsterRegistryInstaller

/**
 * Aster 路由与能力发现入口.
 *
 * 负责初始化模块 Registry, 构建路由请求并解析已注册的能力实现.
 *
 * @aegis 保护公开 API, 初始化发布, 路由查询和能力解析的行为语义.
 * @author whisper
 * @since 2026/07/22
 */
object Aster {

    private val initLock: Any = Any()

    @Volatile
    private var registryState: RegistryState? = null

    /**
     * 当前进程的 Application.
     *
     * @exception IllegalStateException 在调用 [initialize] 前访问时抛出.
     */
    val application: Application
        get() = requireState().application

    /**
     * 当前进程可长期持有的 Application Context.
     *
     * @exception IllegalStateException 在调用 [initialize] 前访问时抛出.
     */
    val context: Context
        get() = application.applicationContext

    fun initialize(application: Application) {
        if (handleRepeatedInitialization(application)) {
            return
        }

        synchronized(initLock) {
            if (handleRepeatedInitialization(application)) {
                return
            }

            val registrationSession: RegistrationSession = RegistrationSession()
            try {
                val installers: List<AsterRegistryInstaller> =
                    ManifestRegistryLoader.load(application)
                installers.forEach {
                    it.install(registrationSession)
                }
                registryState = registrationSession.freeze(application)
            } finally {
                registrationSession.close()
            }
        }
    }

    /**
     * 构建 Activity 路由请求.
     *
     * @param path 至少包含两个合法路径段的路由路径.
     * @return 新的路由请求.
     * @exception IllegalStateException Aster 尚未初始化时抛出.
     */
    fun build(path: String): Postcard {
        requireState()
        val validationError: String? = RoutePathValidator.validationError(path)
        val valid: Boolean = validationError == null
        if (validationError != null) {
            reportError(validationError)
        }
        return Postcard.create(path, valid)
    }

    /**
     * 按唯一能力名获取一个能力实例.
     *
     * 调用方可以将返回值转换为 api 模块声明的具体能力接口.
     *
     * @param name 唯一能力名.
     * @return 能力实例, 未注册时返回 null.
     * @exception IllegalStateException Aster 尚未初始化时抛出.
     */
    fun resolve(name: String): Capability? {
        val state: RegistryState = requireState()
        val validationError: String? = CapabilityNameValidator.validationError(name)
        if (validationError != null) {
            reportError(validationError)
            return null
        }
        val capability: Capability? = state.capabilityRegistry.get(name)
        if (capability == null) {
            reportError("Capability not found: $name")
        }
        return capability
    }

    /**
     * 按能力契约类型获取第一个实现.
     *
     * 结果按能力名排序. 如果存在多个实现, 返回第一个实现并输出警告日志.
     *
     * @param type 能力契约类型.
     * @return 第一个匹配的能力实例, 未注册时返回 null.
     * @exception IllegalStateException Aster 尚未初始化时抛出.
     */
    fun <T : Capability> resolve(type: Class<T>): T? {
        val resolution: CapabilityRegistry.Resolution<T> =
            requireState().capabilityRegistry.resolveFirst(type)
        if (resolution.implementationCount == 0) {
            reportError("Capability not found for type: ${type.name}")
        } else if (resolution.implementationCount > 1) {
            reportWarning(
                "Multiple capabilities found for type ${type.name}. " +
                    "Returning the first capability ordered by name. " +
                    "count=${resolution.implementationCount}, " +
                    "selected=${resolution.selectedName}."
            )
        }
        return resolution.capability
    }

    /**
     * 按能力契约获取全部实现.
     *
     * @param T 能力契约类型.
     * @return 按能力名排序的能力实例列表.
     * @exception IllegalStateException Aster 尚未初始化时抛出.
     */
    inline fun <reified T : Capability> resolveAll(): List<T> {
        return resolveAll(T::class.java)
    }

    /**
     * 按能力契约类型获取全部实现.
     *
     * @param type 能力契约类型.
     * @return 按能力名排序的能力实例列表.
     * @exception IllegalStateException Aster 尚未初始化时抛出.
     */
    fun <T : Capability> resolveAll(type: Class<T>): List<T> {
        return requireState().capabilityRegistry.get(type)
    }

    fun containsCapability(name: String): Boolean {
        val state: RegistryState = requireState()
        val validationError: String? = CapabilityNameValidator.validationError(name)
        if (validationError != null) {
            reportError(validationError)
            return false
        }
        return state.capabilityRegistry.contains(name)
    }

    internal fun containsRoute(path: String): Boolean {
        return requireState().routeRegistry.contains(path)
    }

    internal fun findRoute(path: String): Class<out Activity>? {
        return requireState().routeRegistry.find(path)
    }

    internal fun reportError(message: String, cause: Throwable? = null) {
        LogcatErrorHandler.error(message, cause)
    }

    internal fun reportWarning(message: String, cause: Throwable? = null) {
        LogcatErrorHandler.warning(message, cause)
    }

    private fun handleRepeatedInitialization(application: Application): Boolean {
        val state: RegistryState = registryState ?: return false
        check(state.application === application) {
            "Aster has already been initialized with a different Application instance."
        }
        reportWarning("Aster.initialize() was called more than once.")
        return true
    }

    private fun requireState(): RegistryState {
        return registryState
            ?: throw IllegalStateException(
                "Aster.initialize() must be called before using capabilities or routes."
            )
    }
}
