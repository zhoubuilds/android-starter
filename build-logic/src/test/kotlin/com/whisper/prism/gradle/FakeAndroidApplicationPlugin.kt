package com.whisper.prism.gradle

import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.VariantSelector
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Environment 插件 TestKit 使用的最小 Android application 插件.
 *
 * 通过公开 AGP DSL 和 Variant API 类型模拟最终 DSL、product flavors 和 BuildConfig 生成任务.
 *
 * @author whisper
 * @since 2026/08/24
 */
internal class FakeAndroidApplicationPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val host: FakeAndroidApplicationHost = FakeAndroidApplicationHost(project = project)
        project.extensions.add("android", host.android)
        project.extensions.add("androidComponents", host.androidComponents)
    }

    /**
     * 最小 Android application 宿主.
     *
     * @param project TestKit 工程.
     */
    private class FakeAndroidApplicationHost(
        private val project: Project
    ) {
        private var buildConfigEnabled: Boolean? = project.providers
            .gradleProperty(BUILD_CONFIG_ENABLED_PROPERTY)
            .orNull
            ?.toBooleanStrictOrNull()

        private val manifestPlaceholders: MutableMap<String, Any> = mutableMapOf()
        private val flavorDimensions: MutableList<String> = mutableListOf()
        private val buildFeatures: ApplicationBuildFeatures = proxy(
            type = ApplicationBuildFeatures::class.java
        ) { method: Method, arguments: Array<Any?>? ->
            when (method.name) {
                "getBuildConfig" -> buildConfigEnabled
                "setBuildConfig" -> {
                    buildConfigEnabled = arguments?.firstOrNull() as? Boolean
                    null
                }
                else -> defaultValue(type = method.returnType)
            }
        }
        private val defaultConfig: ApplicationDefaultConfig = variantDimension(
            type = ApplicationDefaultConfig::class.java,
            name = "defaultConfig"
        )
        private val productFlavors: NamedDomainObjectContainer<ApplicationProductFlavor> =
            project.objects.domainObjectContainer(ApplicationProductFlavor::class.java) { name: String ->
                variantDimension(type = ApplicationProductFlavor::class.java, name = name)
            }

        val android: ApplicationExtension = proxy(type = ApplicationExtension::class.java) {
                method: Method,
                arguments: Array<Any?>? ->
            when (method.name) {
                "getBuildFeatures" -> buildFeatures
                "buildFeatures" -> executeCallback(arguments = arguments, value = buildFeatures)
                "getDefaultConfig" -> defaultConfig
                "defaultConfig" -> executeCallback(arguments = arguments, value = defaultConfig)
                "getFlavorDimensions" -> flavorDimensions
                "getProductFlavors" -> productFlavors
                "productFlavors" -> executeCallback(arguments = arguments, value = productFlavors)
                else -> defaultValue(type = method.returnType)
            }
        }

        val androidComponents: ApplicationAndroidComponentsExtension = proxy(
            type = ApplicationAndroidComponentsExtension::class.java
        ) { method: Method, arguments: Array<Any?>? ->
            when {
                method.name == "selector" || method.name == "getSelector" -> variantSelector()
                method.name == "finalizeDsl" || method.name == "finalizeDSl" -> {
                    project.afterEvaluate {
                        executeCallback(arguments = arguments, value = android)
                    }
                    null
                }
                method.name == "onVariants" -> {
                    project.afterEvaluate {
                        variantNames().forEach { variantName: String ->
                            registerBuildConfigTask(variantName = variantName)
                            executeCallback(
                                arguments = arguments,
                                value = applicationVariant(name = variantName)
                            )
                        }
                    }
                    null
                }
                else -> defaultValue(type = method.returnType)
            }
        }

        private fun variantNames(): List<String> {
            val flavorNamesByDimension: List<List<String>> = flavorDimensions
                .map { dimension: String ->
                    productFlavors
                        .filter { flavor: ApplicationProductFlavor -> flavor.dimension == dimension }
                        .map { flavor: ApplicationProductFlavor -> flavor.name }
                        .sorted()
                }
                .filter(List<String>::isNotEmpty)
            if (flavorNamesByDimension.isEmpty()) {
                return listOf("debug", "release")
            }
            val flavorCombinations: List<String> = flavorNamesByDimension.fold(
                initial = listOf("")
            ) { prefixes: List<String>, flavorNames: List<String> ->
                prefixes.flatMap { prefix: String ->
                    flavorNames.map { flavorName: String ->
                        prefix + flavorName.toTaskNameSegment()
                    }
                }
            }
            return flavorCombinations.flatMap { flavorCombination: String ->
                listOf(
                    "${flavorCombination.replaceFirstChar(Char::lowercaseChar)}Debug",
                    "${flavorCombination.replaceFirstChar(Char::lowercaseChar)}Release"
                )
            }
        }

        private fun registerBuildConfigTask(variantName: String) {
            if (buildConfigEnabled != true) {
                return
            }
            project.tasks.register(
                "generate${variantName.toTaskNameSegment()}BuildConfig"
            )
        }

        private fun applicationVariant(name: String): ApplicationVariant {
            return proxy(type = ApplicationVariant::class.java) { method: Method, _: Array<Any?>? ->
                when (method.name) {
                    "getName" -> name
                    else -> defaultValue(type = method.returnType)
                }
            }
        }

        private fun variantSelector(): VariantSelector {
            lateinit var selector: VariantSelector
            selector = proxy(type = VariantSelector::class.java) { _: Method, _: Array<Any?>? ->
                selector
            }
            return selector
        }

        private fun <T : Any> variantDimension(type: Class<T>, name: String): T {
            var dimension: String? = null
            return proxy(type = type) { method: Method, arguments: Array<Any?>? ->
                when (method.name) {
                    "getName" -> name
                    "getDimension" -> dimension
                    "setDimension" -> {
                        dimension = arguments?.firstOrNull() as? String
                        null
                    }
                    "getManifestPlaceholders" -> manifestPlaceholders
                    "buildConfigField", "resValue" -> null
                    else -> defaultValue(type = method.returnType)
                }
            }
        }

        private fun String.toTaskNameSegment(): String {
            return replaceFirstChar { char: Char -> char.uppercaseChar() }
        }
    }

    private companion object {
        private const val BUILD_CONFIG_ENABLED_PROPERTY: String =
            "fake.android.buildConfigEnabled"

        private fun <T : Any> proxy(
            type: Class<T>,
            handler: (Method, Array<Any?>?) -> Any?
        ): T {
            val invocationHandler: InvocationHandler = InvocationHandler { proxy, method, arguments ->
                when (method.name) {
                    "toString" -> "Fake ${type.simpleName}"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === arguments?.firstOrNull()
                    else -> handler(method, arguments)
                }
            }
            return Proxy.newProxyInstance(type.classLoader, arrayOf(type), invocationHandler)
                .let { value: Any -> type.cast(value) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun executeCallback(arguments: Array<Any?>?, value: Any): Any? {
            val callback: Any = arguments?.lastOrNull() ?: return null
            return when (callback) {
                is Action<*> -> {
                    (callback as Action<Any>).execute(value)
                    null
                }
                is Function1<*, *> -> (callback as Function1<Any, *>).invoke(value)
                else -> null
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
}
