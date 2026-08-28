package com.whisper.aster.runtime.internal.registry

import android.app.Application
import com.whisper.aster.runtime.Capability
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * 查询已冻结的能力定义并按声明创建能力实例.
 *
 * 单例能力按能力名缓存, 非单例能力每次获取都会创建并初始化一个新实例.
 *
 * @aegis 保护单例/非单例生命周期, 按名称排序的类型解析和初始化语义.
 *
 * @author whisper
 * @since 2026/07/21
 */
internal class CapabilityRegistry(
    private val application: Application,
    descriptors: Map<String, CapabilityDescriptor>
) {

    private val descriptors: Map<String, CapabilityDescriptor> = descriptors.toMap()
    private val instances: MutableMap<String, Capability> = ConcurrentHashMap()
    private val typeCache: MutableMap<Class<*>, List<String>> = ConcurrentHashMap()

    /**
     * 单个能力类型解析结果.
     *
     * @param capability 按能力名排序后的第一个实现.
     * @param implementationCount 符合能力契约的实现数量.
     * @param selectedName 被选中的能力名.
     */
    internal data class Resolution<T : Capability>(
        val capability: T?,
        val implementationCount: Int,
        val selectedName: String?
    )

    fun contains(name: String): Boolean {
        return descriptors.containsKey(name)
    }

    fun get(name: String): Capability? {
        val descriptor: CapabilityDescriptor = descriptors[name] ?: return null
        if (!descriptor.singleton) {
            return createInstance(descriptor)
        }

        return instances[name] ?: synchronized(descriptor) {
            instances[name] ?: createInstance(descriptor).also {
                instances[name] = it
            }
        }
    }

    fun <T : Capability> get(type: Class<T>): List<T> {
        val names: List<String> = matchingNames(type)

        return names.map {
            resolveMatched(it, type)
        }
    }

    fun <T : Capability> resolveFirst(type: Class<T>): Resolution<T> {
        val names: List<String> = matchingNames(type)
        val selectedName: String? = names.firstOrNull()
        val capability: T? = selectedName?.let { resolveMatched(it, type) }
        return Resolution(
            capability = capability,
            implementationCount = names.size,
            selectedName = selectedName
        )
    }

    private fun matchingNames(type: Class<out Capability>): List<String> {
        return typeCache[type] ?: synchronized(typeCache) {
            typeCache[type] ?: descriptors.values
                .filter { type.isAssignableFrom(requireCapabilityClass(it)) }
                .map { it.name }
                .sorted()
                .also { typeCache[type] = it }
        }
    }

    private fun createInstance(descriptor: CapabilityDescriptor): Capability {
        val constructor: Constructor<out Capability> = validateTarget(descriptor)
        val capability: Capability = try {
            constructor.newInstance()
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
        capability.initialize(application)
        return capability
    }

    private fun <T : Capability> resolveMatched(
        name: String,
        type: Class<T>
    ): T {
        val instance: Capability = checkNotNull(get(name)) {
            "Capability registry invariant violated: matched capability '$name' is missing."
        }
        check(type.isInstance(instance)) {
            "Capability registry invariant violated for name '$name': " +
                "'${instance.javaClass.name}' is not assignable to '${type.name}'."
        }
        return checkNotNull(type.cast(instance)) {
            "Capability registry invariant violated for name '$name': " +
                "casting to '${type.name}' returned null."
        }
    }

    private fun requireCapabilityClass(
        descriptor: CapabilityDescriptor
    ): Class<out Capability> {
        val rawClass: Class<*> = descriptor.implClass
        if (!Capability::class.java.isAssignableFrom(rawClass)) {
            throw IllegalStateException(
                "Invalid capability target for name '${descriptor.name}': '${rawClass.name}' " +
                    "must implement com.whisper.aster.runtime.Capability."
            )
        }
        return rawClass.asSubclass(Capability::class.java)
    }

    private fun validateTarget(
        descriptor: CapabilityDescriptor
    ): Constructor<out Capability> {
        val capabilityClass: Class<out Capability> =
            requireCapabilityClass(descriptor)
        if (!Modifier.isPublic(capabilityClass.modifiers)) {
            throw IllegalStateException(
                "Invalid capability target for name '${descriptor.name}': " +
                    "'${capabilityClass.name}' must be public."
            )
        }
        if (Modifier.isAbstract(capabilityClass.modifiers)) {
            throw IllegalStateException(
                "Invalid capability target for name '${descriptor.name}': " +
                    "'${capabilityClass.name}' must be a concrete class."
            )
        }
        return try {
            capabilityClass.getConstructor()
        } catch (exception: NoSuchMethodException) {
            throw IllegalStateException(
                "Invalid capability target for name '${descriptor.name}': " +
                    "'${capabilityClass.name}' must provide a public no-argument constructor.",
                exception
            )
        }
    }
}
