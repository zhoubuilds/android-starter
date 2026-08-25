package com.whisper.aster.compiler.codegen

import com.whisper.aster.compiler.AsterCompilerContract
import com.whisper.aster.compiler.model.CapabilityEntry
import com.whisper.aster.compiler.model.RouteEntry
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec

/**
 * 生成当前模块的 Aster Registry 源码.
 *
 * @param codeGenerator KSP 源码生成器.
 * @param logger KSP 编译期错误日志记录器.
 * @param options Gradle 插件传递给 KSP 的处理器参数.
 *
 * @aegis 保护生成 Registry 的类名/ABI, 条目稳定排序和增量依赖语义.
 * @author whisper
 * @since 2026/07/22
 */
internal class AsterRegistryWriter(
    /**
     * KSP 源码生成器.
     */
    private val codeGenerator: CodeGenerator,
    /**
     * KSP 编译期错误日志记录器.
     */
    private val logger: KSPLogger,
    /**
     * Gradle 插件传递给 KSP 的处理器参数.
     */
    private val options: Map<String, String>
) {

    /**
     * 生成包含全部路由和能力条目的模块 Registry.
     *
     * @param routes 当前模块的全部路由条目.
     * @param capabilities 当前模块的全部能力条目.
     * @param sourceFiles 生成结果依赖的源文件.
     * @return 是否成功生成 Registry.
     */
    fun write(
        routes: List<RouteEntry>,
        capabilities: List<CapabilityEntry>,
        sourceFiles: List<KSFile>
    ): Boolean {
        val registryPackage: String? =
            options[AsterCompilerContract.OPTION_REGISTRY_PACKAGE]
                ?.trim()
                ?.takeIf(String::isNotBlank)
        if (
            registryPackage == null ||
            !AsterCompilerContract.PACKAGE_NAME_PATTERN.matches(registryPackage)
        ) {
            logger.error(
                "Invalid or missing KSP option " +
                    "'${AsterCompilerContract.OPTION_REGISTRY_PACKAGE}'. The Aster Gradle " +
                    "plugin must provide a valid Kotlin package name, for example " +
                    "'com.example.aster.generated'. Apply com.whisper.aster to an " +
                    "Android application or library module."
            )
            return false
        }

        val installCode: CodeBlock = CodeBlock.builder().apply {
            routes.sortedWith(compareBy(RouteEntry::path, RouteEntry::className))
                .forEach { route: RouteEntry ->
                    addStatement(
                        "%N.registerRoute(%S, %T::class.java)",
                        AsterCompilerContract.REGISTRAR_PARAMETER,
                        route.path,
                        route.targetType
                    )
                }
            if (routes.isNotEmpty() && capabilities.isNotEmpty()) {
                add("\n")
            }
            capabilities.sortedWith(
                compareBy(CapabilityEntry::name, CapabilityEntry::className)
            ).forEach { capability: CapabilityEntry ->
                addStatement(
                    "%N.registerCapability(%S, %T::class.java, %L)",
                    AsterCompilerContract.REGISTRAR_PARAMETER,
                    capability.name,
                    capability.targetType,
                    capability.singleton
                )
            }
        }.build()
        val registryType: TypeSpec =
            TypeSpec.classBuilder(AsterCompilerContract.REGISTRY_CLASS_NAME)
                .addModifiers(KModifier.PUBLIC)
                .addKdoc(
                    "当前模块的 Aster 路由和能力注册器.\n\n" +
                        "@author aster\n"
                )
                .addSuperinterface(AsterCompilerContract.ASTER_REGISTRY_INSTALLER_TYPE)
                .addFunction(
                    FunSpec.builder(AsterCompilerContract.INSTALL_METHOD_NAME)
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .addParameter(
                            AsterCompilerContract.REGISTRAR_PARAMETER,
                            AsterCompilerContract.ASTER_REGISTRAR_TYPE
                        )
                        .addCode(installCode)
                        .build()
                )
                .build()
        val fileSpec: FileSpec = FileSpec.builder(
            registryPackage,
            AsterCompilerContract.REGISTRY_CLASS_NAME
        ).addType(registryType).build()

        codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = true,
                sources = sourceFiles.toTypedArray()
            ),
            packageName = registryPackage,
            fileName = AsterCompilerContract.REGISTRY_CLASS_NAME,
            extensionName = "kt"
        ).bufferedWriter(Charsets.UTF_8).use { writer ->
            fileSpec.writeTo(writer)
        }
        return true
    }
}
