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
 * @aegis-audit 2026-09-01 | whisper | 经授权收敛名称类型安全解析和类型唯一解析契约.
 *
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
     * 按唯一能力名获取动态类型实例.
     *
     * 该入口不校验业务契约类型, 调用方需要自行使用安全类型转换.
     * Capability 构造或初始化异常会原样向上传播.
     *
     * @param name 唯一能力名.
     * @return 能力实例, 名称格式非法或未注册时返回 null.
     * @exception IllegalStateException Aster 尚未初始化或已注册目标无效时抛出.
     * @exception Throwable Capability 构造或初始化失败时原样抛出.
     */
    fun resolveCapability(name: String): Capability? {
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
     * 按唯一能力名获取类型安全实例.
     *
     * 名称已注册但实现类型与 [T] 不匹配时会在实例化前失败.
     * 异常信息包含能力名, 请求类型, 已注册实现类型和检查建议.
     * Capability 构造或初始化异常会原样向上传播.
     *
     * @param T 请求的能力契约类型.
     * @param name 唯一能力名.
     * @return 类型匹配的能力实例, 名称格式非法或未注册时返回 null.
     * @exception IllegalStateException Aster 尚未初始化, 已注册目标无效或实现类型不匹配时抛出.
     * @exception Throwable Capability 构造或初始化失败时原样抛出.
     */
    inline fun <reified T : Capability> resolve(name: String): T? {
        return resolve(name, T::class.java)
    }

    /**
     * 按唯一能力名和契约类型获取类型安全实例.
     *
     * 名称已注册但实现类型与 [type] 不匹配时会在实例化前失败.
     * 异常信息包含能力名, 请求类型, 已注册实现类型和检查建议.
     * Capability 构造或初始化异常会原样向上传播.
     *
     * @param name 唯一能力名.
     * @param type 请求的能力契约类型.
     * @return 类型匹配的能力实例, 名称格式非法或未注册时返回 null.
     * @exception IllegalStateException Aster 尚未初始化, 已注册目标无效或实现类型不匹配时抛出.
     * @exception Throwable Capability 构造或初始化失败时原样抛出.
     */
    fun <T : Capability> resolve(name: String, type: Class<T>): T? {
        val state: RegistryState = requireState()
        val validationError: String? = CapabilityNameValidator.validationError(name)
        if (validationError != null) {
            reportError(validationError)
            return null
        }
        val capability: T? = state.capabilityRegistry.resolve(name, type)
        if (capability == null) {
            reportError("Capability not found: $name")
        }
        return capability
    }

    /**
     * 按能力契约类型获取唯一实现.
     * 存在多个匹配实现时会在实例化任何候选能力前失败.
     * 异常信息包含请求类型, 匹配数量, 按名称排序的候选能力名和显式选择建议.
     * Capability 构造或初始化异常会原样向上传播.
     *
     * @param type 能力契约类型.
     * @return 唯一匹配的能力实例, 未注册时返回 null.
     * @exception IllegalStateException Aster 尚未初始化, 已注册目标无效或存在多个匹配实现时
     * 抛出.
     * @exception Throwable Capability 构造或初始化失败时原样抛出.
     */
    fun <T : Capability> resolve(type: Class<T>): T? {
        val capability: T? = requireState().capabilityRegistry.resolveSingle(type)
        if (capability == null) {
            reportError("Capability not found for type: ${type.name}")
        }
        return capability
    }

    /**
     * 按能力契约获取全部实现.
     * Capability 构造或初始化异常会原样向上传播.
     *
     * @param T 能力契约类型.
     * @return 按能力名排序的能力实例列表.
     * @exception IllegalStateException Aster 尚未初始化或已注册目标无效时抛出.
     * @exception Throwable Capability 构造或初始化失败时原样抛出.
     */
    inline fun <reified T : Capability> resolveAll(): List<T> {
        return resolveAll(T::class.java)
    }

    /**
     * 按能力契约类型获取全部实现.
     * Capability 构造或初始化异常会原样向上传播.
     *
     * @param type 能力契约类型.
     * @return 按能力名排序的能力实例列表.
     * @exception IllegalStateException Aster 尚未初始化或已注册目标无效时抛出.
     * @exception Throwable Capability 构造或初始化失败时原样抛出.
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
