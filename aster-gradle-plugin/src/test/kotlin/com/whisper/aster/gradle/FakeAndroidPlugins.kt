package com.whisper.aster.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Component
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.ManifestFiles
import com.android.build.api.variant.Sources
import com.android.build.api.variant.VariantSelector
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * TestKit 使用的最小 Android application plugin.
 *
 * 通过真实 Android Components public API 类型驱动 Aster, 不实现 AGP 的编译流程.
 *
 * @author whisper
 * @since 2026/07/21
 */
internal class FakeAndroidApplicationPlugin : Plugin<Project> {

    /**
     * 注册 application Android Components host.
     *
     * @param project 测试工程.
     */
    override fun apply(project: Project) {
        val namespace: String = project.property("aster.test.namespace").toString()
        project.extensions.add(
            "androidComponents",
            FakeAndroidApi.applicationComponents(project, namespace)
        )
    }
}

/**
 * TestKit 使用的最小 Android library plugin.
 *
 * 通过真实 Android Components public API 类型驱动 Aster, 不实现 AGP 的打包流程.
 *
 * @author whisper
 * @since 2026/07/21
 */
internal class FakeAndroidLibraryPlugin : Plugin<Project> {

    /**
     * 注册 library Android Components host.
     *
     * @param project 测试工程.
     */
    override fun apply(project: Project) {
        val namespace: String = project.property("aster.test.namespace").toString()
        project.extensions.add(
            "androidComponents",
            FakeAndroidApi.libraryComponents(project, namespace)
        )
    }
}

/**
 * 创建 Android Components public API 的测试代理.
 *
 * @author whisper
 * @since 2026/07/21
 */
private object FakeAndroidApi {

    /**
     * 创建 application Components 扩展.
     *
     * @param project 测试工程.
     * @param namespace 测试 namespace.
     * @return application Components 扩展代理.
     */
    fun applicationComponents(
        project: Project,
        namespace: String
    ): ApplicationAndroidComponentsExtension {
        val dsl: ApplicationExtension = proxy(ApplicationExtension::class.java) { method: Method,
            _: Array<Any?>? ->
            when (method.name) {
                "getNamespace" -> namespace
                else -> defaultValue(method.returnType)
            }
        }
        val variant: ApplicationVariant = variant(project, namespace, ApplicationVariant::class.java)
        val selector: VariantSelector = selector()
        return components(
            project = project,
            extensionType = ApplicationAndroidComponentsExtension::class.java,
            dsl = dsl,
            variant = variant,
            selector = selector
        )
    }

    /**
     * 创建 library Components 扩展.
     *
     * @param project 测试工程.
     * @param namespace 测试 namespace.
     * @return library Components 扩展代理.
     */
    fun libraryComponents(
        project: Project,
        namespace: String
    ): LibraryAndroidComponentsExtension {
        val dsl: LibraryExtension = proxy(LibraryExtension::class.java) { method: Method,
            _: Array<Any?>? ->
            when (method.name) {
                "getNamespace" -> namespace
                else -> defaultValue(method.returnType)
            }
        }
        val variant: LibraryVariant = variant(project, namespace, LibraryVariant::class.java)
        val selector: VariantSelector = selector()
        return components(
            project = project,
            extensionType = LibraryAndroidComponentsExtension::class.java,
            dsl = dsl,
            variant = variant,
            selector = selector
        )
    }

    private fun <Dsl : Any, Variant : Any, Components : Any> components(
        project: Project,
        extensionType: Class<Components>,
        dsl: Dsl,
        variant: Variant,
        selector: VariantSelector
    ): Components {
        return proxy(extensionType) { method: Method, arguments: Array<Any?>? ->
            when {
                method.name == "selector" || method.name == "getSelector" -> selector
                method.name == "finalizeDsl" || method.name == "finalizeDSl" -> {
                    project.afterEvaluate { executeCallback(arguments, dsl) }
                    null
                }
                method.name == "onVariants" -> {
                    project.afterEvaluate { executeCallback(arguments, variant) }
                    null
                }
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun selector(): VariantSelector {
        lateinit var selector: VariantSelector
        selector = proxy(VariantSelector::class.java) { _: Method, _: Array<Any?>? -> selector }
        return selector
    }

    private fun <Variant : Any> variant(
        project: Project,
        namespace: String,
        variantType: Class<Variant>
    ): Variant {
        val provider: Provider<String> = property(project, namespace)
        val manifests: ManifestFiles = proxy(ManifestFiles::class.java) { method: Method,
            _: Array<Any?>? ->
            when (method.name) {
                "getName" -> "manifests"
                "getAll" -> project.providers.provider { emptyList<Any>() }
                else -> null
            }
        }
        val sources: Sources = proxy(Sources::class.java) { method: Method,
            _: Array<Any?>? ->
            when (method.name) {
                "getManifests" -> manifests
                else -> null
            }
        }
        return proxy(variantType) { method: Method, _: Array<Any?>? ->
            when (method.name) {
                "getName" -> "debug"
                "getNamespace" -> provider
                "getSources" -> sources
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun <T : Any> proxy(
        type: Class<T>,
        handler: (Method, Array<Any?>?) -> Any?
    ): T {
        val invocationHandler: InvocationHandler = InvocationHandler { _, method, arguments ->
            when (method.name) {
                "toString" -> "Fake ${type.simpleName}"
                "hashCode" -> System.identityHashCode(type)
                "equals" -> arguments?.firstOrNull() === type
                else -> handler(method, arguments)
            }
        }
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type), invocationHandler)
            .let { value: Any -> type.cast(value) }
    }

    private fun property(project: Project, value: String): Provider<String> {
        return project.providers.provider { value }
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeCallback(arguments: Array<Any?>?, value: Any) {
        val callback: Any = arguments?.lastOrNull() ?: return
        when (callback) {
            is Action<*> -> (callback as Action<Any>).execute(value)
            is Function1<*, *> -> (callback as Function1<Any, *>).invoke(value)
        }
    }

    private fun defaultValue(type: Class<*>): Any? {
        return when {
            !type.isPrimitive -> null
            type == Boolean::class.javaPrimitiveType -> false
            type == Byte::class.javaPrimitiveType -> 0.toByte()
            type == Short::class.javaPrimitiveType -> 0.toShort()
            type == Int::class.javaPrimitiveType -> 0
            type == Long::class.javaPrimitiveType -> 0L
            type == Float::class.javaPrimitiveType -> 0F
            type == Double::class.javaPrimitiveType -> 0.0
            type == Char::class.javaPrimitiveType -> '\u0000'
            else -> null
        }
    }
}
