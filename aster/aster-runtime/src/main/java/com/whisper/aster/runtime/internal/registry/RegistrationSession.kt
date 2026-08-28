package com.whisper.aster.runtime.internal.registry

import android.app.Activity
import android.app.Application
import com.whisper.aster.runtime.Capability
import com.whisper.aster.runtime.registry.AsterRegistrar

/**
 * 收集一次 Aster 初始化过程中的路由和能力定义.
 *
 * 注册完成后将全部定义冻结为只读状态, 后续写入会立即失败.
 *
 * @aegis 保护重复声明判定, 冻结快照和关闭后拒绝写入的会话语义.
 *
 * @author whisper
 * @since 2026/07/23
 */
internal class RegistrationSession : AsterRegistrar {

    private val monitor: Any = Any()
    private val routes: MutableMap<String, Class<out Activity>> = LinkedHashMap()
    private val capabilities: MutableMap<String, CapabilityDescriptor> = LinkedHashMap()
    private var closed: Boolean = false

    override fun registerRoute(path: String, activityClass: Class<out Activity>) {
        synchronized(monitor) {
            requireOpen()
            val previous: Class<out Activity>? = routes.putIfAbsent(path, activityClass)
            if (previous != null && previous != activityClass) {
                throw IllegalArgumentException(
                    "Duplicate route path: $path, previous=${previous.name}, " +
                        "current=${activityClass.name}."
                )
            }
        }
    }

    override fun registerCapability(
        name: String,
        implClass: Class<out Capability>,
        singleton: Boolean
    ) {
        synchronized(monitor) {
            requireOpen()
            val descriptor: CapabilityDescriptor =
                CapabilityDescriptor(name, implClass, singleton)
            val previous: CapabilityDescriptor? = capabilities.putIfAbsent(name, descriptor)
            if (previous != null) {
                throw IllegalArgumentException(
                    "Duplicate capability name: $name, previous=${previous.implClass.name}, " +
                        "current=${implClass.name}."
                )
            }
        }
    }

    /**
     * 关闭注册阶段并生成完整只读状态.
     *
     * @param application 当前进程的 Application.
     * @return 包含全部路由和能力定义的 Registry 状态.
     * @exception IllegalStateException 注册阶段已经结束时抛出.
     */
    fun freeze(application: Application): RegistryState {
        return synchronized(monitor) {
            check(!closed) { "Aster registration session is closed." }
            closed = true

            val routeSnapshot: Map<String, Class<out Activity>> = routes.toMap()
            val capabilitySnapshot: Map<String, CapabilityDescriptor> = capabilities.toMap()
            routes.clear()
            capabilities.clear()

            RegistryState(
                application = application,
                routeRegistry = RouteRegistry(routeSnapshot),
                capabilityRegistry = CapabilityRegistry(application, capabilitySnapshot)
            )
        }
    }

    /**
     * 废弃当前注册会话及尚未发布的数据.
     */
    fun close() {
        synchronized(monitor) {
            closed = true
            routes.clear()
            capabilities.clear()
        }
    }

    private fun requireOpen() {
        check(!closed) { "Aster registration session is closed." }
    }
}
