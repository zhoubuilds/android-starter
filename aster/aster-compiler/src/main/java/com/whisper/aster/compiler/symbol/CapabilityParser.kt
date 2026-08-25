package com.whisper.aster.compiler.symbol

import com.whisper.aster.compiler.AsterCompilerContract
import com.whisper.aster.compiler.model.CapabilityEntry
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility

/**
 * 解析并校验 Capable 声明.
 *
 * @param logger KSP 编译期错误日志记录器.
 *
 * @aegis 保护 `@Capable` 的名称, segment, 目标类型, 构造器和单例参数校验语义.
 * @author whisper
 * @since 2026/07/22
 */
internal class CapabilityParser(
    /**
     * KSP 编译期错误日志记录器.
     */
    private val logger: KSPLogger
) {

    /**
     * 解析一个 Capable 声明.
     *
     * @param declaration 标记了 Capable 的类型声明.
     * @param segment 当前模块配置的首段.
     * @return 校验通过后的能力条目, 校验失败时返回 null.
     */
    fun parse(declaration: KSClassDeclaration, segment: String): CapabilityEntry? {
        val name: String? = declaration.stringArgument(
            AsterCompilerContract.CAPABLE_ANNOTATION,
            "name"
        )
        val singleton: Boolean = declaration.booleanArgument(
            AsterCompilerContract.CAPABLE_ANNOTATION,
            "singleton"
        ) ?: true
        val qualifiedName: String? = declaration.qualifiedName?.asString()

        if (qualifiedName == null) {
            logger.error(
                "@Capable target must be a named class. Local and anonymous " +
                    "declarations cannot be registered as capabilities.",
                declaration
            )
            return null
        }
        if (name.isNullOrBlank()) {
            logger.error(
                "@Capable name must not be blank. Expected a name such as " +
                    "'feature.api.capability'. For this module, use " +
                    "'$segment.api.capability'.",
                declaration
            )
            return null
        }
        if (!AsterCompilerContract.CAPABILITY_NAME_PATTERN.matches(name)) {
            logger.error(
                "Invalid @Capable name '$name'. Expected at least two dot-separated segments; " +
                    "each segment must start with [a-z] and contain only [a-z0-9_]. " +
                    "Example: 'feature.api.capability'. For this module, use " +
                    "'$segment.api.capability'.",
                declaration
            )
            return null
        }
        if (!name.hasSegment(segment)) {
            logger.error(
                "@Capable name '$name' does not use the configured module segment '$segment'. " +
                    "The first segment must be '$segment'. Expected a name starting with " +
                    "'$segment.', for example '$segment.api.capability'.",
                declaration
            )
            return null
        }
        if (declaration.classKind != ClassKind.CLASS) {
            logger.error(
                "@Capable target '$qualifiedName' must be a regular class. " +
                    "Kotlin objects, companion objects, interfaces, and other declaration " +
                    "kinds cannot be capability implementations.",
                declaration
            )
            return null
        }
        if (Modifier.INNER in declaration.modifiers) {
            logger.error(
                "@Capable target '$qualifiedName' must be a regular non-inner class. " +
                    "Remove the 'inner' modifier or move the implementation to a top-level " +
                    "class.",
                declaration
            )
            return null
        }
        if (!declaration.isAccessibleFromGeneratedRegistry()) {
            logger.error(
                "@Capable target '$qualifiedName' is not accessible from the generated Registry. " +
                    "Use a public or internal top-level class, or an accessible non-inner " +
                    "nested class.",
                declaration
            )
            return null
        }
        if (Modifier.ABSTRACT in declaration.modifiers) {
            logger.error(
                "@Capable target '$qualifiedName' must not be abstract. " +
                    "Use a concrete Capability class.",
                declaration
            )
            return null
        }
        if (!declaration.isSubclassOf(AsterCompilerContract.CAPABILITY_CLASS_NAME)) {
            logger.error(
                "@Capable target '$qualifiedName' must implement " +
                    "com.whisper.aster.runtime.Capability. Add ': Capability' or implement it " +
                    "through a parent type.",
                declaration
            )
            return null
        }
        if (!hasAccessibleNoArgConstructor(declaration)) {
            logger.error(
                "@Capable class '$qualifiedName' must provide a public no-argument constructor. " +
                    "Declare a public zero-parameter or secondary no-arg constructor, or " +
                    "give every parameter of a public Kotlin primary constructor a default " +
                    "value.",
                declaration
            )
            return null
        }

        return CapabilityEntry(
            name = name,
            className = qualifiedName,
            singleton = singleton,
            targetType = declaration.asKotlinPoetClassName(),
            sourceFile = declaration.containingFile
        )
    }

    private fun hasAccessibleNoArgConstructor(declaration: KSClassDeclaration): Boolean {
        val primaryConstructor: KSFunctionDeclaration? = declaration.primaryConstructor
        return declaration.getConstructors().any { constructor: KSFunctionDeclaration ->
            val isPublic: Boolean = constructor.getVisibility() == Visibility.PUBLIC
            val isZeroParameter: Boolean = constructor.parameters.isEmpty()
            val hasDefaultPrimaryConstructor: Boolean =
                constructor == primaryConstructor &&
                    constructor.parameters.all { parameter -> parameter.hasDefault }
            isPublic && (isZeroParameter || hasDefaultPrimaryConstructor)
        }
    }
}
