package com.whisper.aster.compiler

import com.squareup.kotlinpoet.ClassName

/**
 * Aster compiler 集中使用的常量.
 *
 * 内部实现常量与需要跨模块保持一致的外部协议常量在此分别管理.
 *
 * @aegis 保护注解类名, KSP 参数, 生成类型和 Runtime ABI 等跨模块协议常量.
 * @author whisper
 * @since 2026/07/22
 */
internal object AsterCompilerContract {

    // ---------------------------------------------------------------------
    // Compiler 内部实现常量.
    // ---------------------------------------------------------------------

    // 以下值只用于当前处理器的校验和源码生成, 不属于跨模块协议.

    /**
     * 路由路径格式, 要求以斜杠开始并至少包含两个路径段.
     */
    val ROUTE_PATH_PATTERN: Regex = Regex("^/[a-z][a-z0-9_]*(/[a-z][a-z0-9_]*)+$")

    /**
     * 模块首段格式.
     */
    val SEGMENT_PATTERN: Regex = Regex("^[a-z][a-z0-9_]*$")

    /**
     * 能力名称格式, 要求至少包含两个点号分隔的段.
     */
    val CAPABILITY_NAME_PATTERN: Regex = Regex(
        "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"
    )

    /**
     * 生成 Registry 包名的 Kotlin 包名格式.
     */
    val PACKAGE_NAME_PATTERN: Regex = Regex(
        "^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"
    )

    // ---------------------------------------------------------------------
    // Aster 外部协议常量.
    // ---------------------------------------------------------------------

    // 以下值需要与 annotation、Gradle 插件或 Runtime 中的对应协议保持一致.

    /**
     * Route 注解的全限定类名.
     */
    const val ROUTE_ANNOTATION: String = "com.whisper.aster.runtime.annotation.Route"

    /**
     * Capable 注解的全限定类名.
     */
    const val CAPABLE_ANNOTATION: String = "com.whisper.aster.runtime.annotation.Capable"

    /**
     * Android Activity 的全限定类名.
     */
    const val ACTIVITY_CLASS_NAME: String = "android.app.Activity"

    /**
     * Capability 接口的全限定类名.
     */
    const val CAPABILITY_CLASS_NAME: String = "com.whisper.aster.runtime.Capability"

    /**
     * Gradle 插件传递模块 segment 使用的 KSP 参数名.
     */
    const val OPTION_SEGMENT: String = "aster.segment"

    /**
     * Gradle 插件传递 Registry 包名使用的 KSP 参数名.
     */
    const val OPTION_REGISTRY_PACKAGE: String = "aster.registryPackage"

    /**
     * 每个模块生成的 Registry 类名.
     */
    const val REGISTRY_CLASS_NAME: String = "AsterGeneratedRegistry"

    /**
     * Registry 安装器接口的 KotlinPoet 类型.
     */
    val ASTER_REGISTRY_INSTALLER_TYPE: ClassName = ClassName(
        "com.whisper.aster.runtime.registry",
        "AsterRegistryInstaller"
    )

    /**
     * 模块注册入口的 KotlinPoet 类型.
     */
    val ASTER_REGISTRAR_TYPE: ClassName = ClassName(
        "com.whisper.aster.runtime.registry",
        "AsterRegistrar"
    )

    /**
     * Registry 安装方法名.
     */
    const val INSTALL_METHOD_NAME: String = "install"

    /**
     * Registry 安装方法的注册入口参数名.
     */
    const val REGISTRAR_PARAMETER: String = "registrar"
}
