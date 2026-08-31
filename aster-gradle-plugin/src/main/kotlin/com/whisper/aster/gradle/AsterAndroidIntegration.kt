package com.whisper.aster.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Component
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.LibraryVariant
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register

/**
 * Aster 与 Android Components 的集成.
 *
 * 该类型单独承载 AGP API, 只有 Android application 或 library 插件应用后才会被
 * AsterPlugin 调用.
 *
 * @author whisper
 * @since 2026/07/21
 */
internal class AsterAndroidIntegration {

    /**
     * 为 Android application 的每个 Variant 接入 Registry Manifest.
     *
     * @param project 应用该插件的 application 模块.
     * @param extension 当前模块的 aster DSL 扩展.
     * @param segmentRegistry 当前 Gradle Build 的 segment 注册中心.
     * @param registryPackage 根据 Android namespace 计算 Registry 包名的函数.
     * @exception GradleException 找不到 Android Components 扩展或 segment 冲突时抛出.
     */
    fun registerApplicationManifest(
        project: Project,
        extension: AsterExtension,
        segmentRegistry: Provider<AsterSegmentRegistryService>,
        registryPackage: (String?) -> String
    ) {
        val androidComponents: ApplicationAndroidComponentsExtension =
            project.extensions.findByType<ApplicationAndroidComponentsExtension>()
                ?: throw GradleException(
                    "Aster could not find ApplicationAndroidComponentsExtension after " +
                        "applying com.android.application in module ${project.path}. " +
                        "This may indicate an incompatible AGP version. Verified project " +
                        "version: $TESTED_AGP_VERSION."
                )

        androidComponents.finalizeDsl { _: ApplicationExtension ->
            registerSegment(project, extension, segmentRegistry)
        }

        androidComponents.onVariants { variant: ApplicationVariant ->
            try {
                registerVariantManifest(
                    project = project,
                    extension = extension,
                    variant = variant,
                    registryPackage = registryPackage,
                    namespace = variant.namespace
                )
            } catch (error: LinkageError) {
                throw asterAgpCompatibilityException(project, error)
            }
        }
    }

    /**
     * 为 Android library 的每个 Variant 接入 Registry Manifest.
     *
     * @param project 应用该插件的 library 模块.
     * @param extension 当前模块的 aster DSL 扩展.
     * @param segmentRegistry 当前 Gradle Build 的 segment 注册中心.
     * @param registryPackage 根据 Android namespace 计算 Registry 包名的函数.
     * @exception GradleException 找不到 Android Components 扩展或 segment 冲突时抛出.
     */
    fun registerLibraryManifest(
        project: Project,
        extension: AsterExtension,
        segmentRegistry: Provider<AsterSegmentRegistryService>,
        registryPackage: (String?) -> String
    ) {
        val androidComponents: LibraryAndroidComponentsExtension =
            project.extensions.findByType<LibraryAndroidComponentsExtension>()
                ?: throw GradleException(
                    "Aster could not find LibraryAndroidComponentsExtension after " +
                        "applying com.android.library in module ${project.path}. " +
                        "This may indicate an incompatible AGP version. Verified project " +
                        "version: $TESTED_AGP_VERSION."
                )

        androidComponents.finalizeDsl { _: LibraryExtension ->
            registerSegment(project, extension, segmentRegistry)
        }

        androidComponents.onVariants { variant: LibraryVariant ->
            try {
                registerVariantManifest(
                    project = project,
                    extension = extension,
                    variant = variant,
                    registryPackage = registryPackage,
                    namespace = variant.namespace
                )
            } catch (error: LinkageError) {
                throw asterAgpCompatibilityException(project, error)
            }
        }
    }

    /**
     * 注册一个 Variant 对应的 Manifest 生成任务.
     *
     * @param project 应用 aster 的 Android 模块.
     * @param extension 当前模块的 aster DSL 扩展.
     * @param variant 当前 Android Variant.
     * @param registryPackage 根据 Android namespace 计算 Registry 包名的函数.
     * @param namespace 当前 Android Variant 的 Android namespace Provider.
     * @exception GradleException 缺少 KSP 时抛出.
     */
    private fun registerVariantManifest(
        project: Project,
        extension: AsterExtension,
        variant: Component,
        registryPackage: (String?) -> String,
        namespace: Provider<String>
    ) {
        if (!project.pluginManager.hasPlugin(KSP_PLUGIN_ID)) {
            throw GradleException(
                "Aster requires the com.google.devtools.ksp plugin in module " +
                    "${project.path}. Add it to the module-level build.gradle.kts plugins " +
                    "block; keep all existing plugin declarations:\n\n" +
                    "plugins {\n" +
                    "    // ... keep existing plugins ...\n" +
                    "    id(\"com.google.devtools.ksp\")\n" +
                    "    id(\"com.whisper.aster\")\n" +
                    "}\n"
            )
        }
        val variantName: String = variant.name
        val variantSuffix: String = variantName.toVariantSuffix()
        val task: TaskProvider<GenerateAsterManifestTask> =
            project.tasks.register<GenerateAsterManifestTask>(
                "generateAster${variantSuffix}Manifest"
            ) {
                val qualifiedName: Provider<String> = namespace.map { value: String ->
                    "${registryPackage(value)}.$GENERATED_CLASS_NAME"
                }
                val manifestOutput: Provider<RegularFile> = project.layout.buildDirectory.file(
                    "generated/aster/$variantName/manifest/AndroidManifest.xml"
                )

                registryQualifiedName.set(qualifiedName)
                registryMetadataMarker.set(REGISTRY_METADATA_MARKER)
                manifestFile.set(manifestOutput)
            }

        variant.sources.manifests.addGeneratedManifestFile(
            task,
            GenerateAsterManifestTask::manifestFile
        )
    }

    /**
     * 冻结并注册当前模块最终使用的 segment.
     *
     * @param project 应用 Aster 插件的 Android 模块.
     * @param extension 当前模块的 Aster DSL 扩展.
     * @param segmentRegistry 当前 Gradle Build 的 segment 注册中心.
     * @exception GradleException segment 缺失或与其它模块重复时抛出.
     */
    private fun registerSegment(
        project: Project,
        extension: AsterExtension,
        segmentRegistry: Provider<AsterSegmentRegistryService>
    ) {
        val segment: String = extension.finalizeSegment(project.path)
        segmentRegistry.get().register(project.path, segment)
    }

    private fun String.toVariantSuffix(): String {
        return replaceFirstChar { character: Char -> character.uppercaseChar() }
    }

    private companion object {

        /**
         * KSP Gradle 插件 ID.
         */
        private const val KSP_PLUGIN_ID: String = "com.google.devtools.ksp"

        /**
         * 当前已验证的 AGP 版本, 仅用于兼容性错误提示.
         */
        private const val TESTED_AGP_VERSION: String = "9.2.1"

        /**
         * Registry Manifest metadata 的固定发现标记.
         */
        private const val REGISTRY_METADATA_MARKER: String = "com.whisper.aster.registry"

        /**
         * 每个模块生成的 Registry 类名.
         */
        private const val GENERATED_CLASS_NAME: String = "AsterGeneratedRegistry"
    }
}

/**
 * 创建 AGP 兼容性错误, 保留底层链接错误便于继续排查.
 *
 * @param project 应用 aster 的 Android 模块.
 * @param cause AGP 类或方法链接失败的原始错误.
 * @return 包含版本排错建议的 Gradle 异常.
 */
internal fun asterAgpCompatibilityException(
    project: Project,
    cause: LinkageError
): GradleException {
    return GradleException(
        "Aster could not configure Android Components in module " +
            "${project.path}. This may indicate an incompatible AGP version. " +
            "Verified project version: AGP 9.2.1. Plugin compile baseline: AGP 9.2.1. " +
            "Check the AGP compatibility matrix. " +
            "Original error: ${cause.javaClass.name}: ${cause.message.orEmpty()}",
        cause
    )
}
