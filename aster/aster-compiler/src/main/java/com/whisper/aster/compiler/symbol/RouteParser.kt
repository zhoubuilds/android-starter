package com.whisper.aster.compiler.symbol

import com.whisper.aster.compiler.AsterCompilerContract
import com.whisper.aster.compiler.model.RouteEntry
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier

/**
 * 解析并校验 Route 声明.
 *
 * @param logger KSP 编译期错误日志记录器.
 *
 * @aegis 保护 `@Route` 的路径, segment, 目标类型和可访问性校验语义.
 * @author whisper
 * @since 2026/07/22
 */
internal class RouteParser(
    /**
     * KSP 编译期错误日志记录器.
     */
    private val logger: KSPLogger
) {

    /**
     * 解析一个 Route 声明.
     *
     * @param declaration 标记了 Route 的类型声明.
     * @param segment 当前模块配置的首段.
     * @return 校验通过后的路由条目, 校验失败时返回 null.
     */
    fun parse(declaration: KSClassDeclaration, segment: String): RouteEntry? {
        val path: String? = declaration.stringArgument(
            AsterCompilerContract.ROUTE_ANNOTATION,
            "path"
        )
        val qualifiedName: String? = declaration.qualifiedName?.asString()

        if (qualifiedName == null) {
            logger.error(
                "@Route target must be a named class. Local and anonymous classes cannot be " +
                    "registered as routes.",
                declaration
            )
            return null
        }
        if (path.isNullOrBlank()) {
            logger.error(
                "@Route path must not be blank. Expected the format '/<segment>/<page>'. " +
                    "Example: '/feature/page'. " +
                    "For this module, use '/$segment/page'.",
                declaration
            )
            return null
        }
        if (!AsterCompilerContract.ROUTE_PATH_PATTERN.matches(path)) {
            logger.error(
                "Invalid @Route path '$path'. Expected at least two slash-separated segments; " +
                    "each segment must start with [a-z] and contain only [a-z0-9_], " +
                    "the path must start with '/' and must not end with '/'. " +
                    "Expected the format '/<segment>/<page>'. " +
                    "Example: '/feature/page'. For this module, use '/$segment/page'.",
                declaration
            )
            return null
        }
        if (!path.hasSegment(segment)) {
            logger.error(
                "@Route path '$path' does not use the configured module segment '$segment'. " +
                    "The first segment must be '$segment'. Expected a path starting with " +
                    "'/$segment/', for example '/$segment/page'.",
                declaration
            )
            return null
        }
        if (declaration.classKind != ClassKind.CLASS) {
            logger.error(
                "@Route target '$qualifiedName' must be a regular class. " +
                    "Kotlin objects, companion objects, interfaces, and other declaration " +
                    "kinds cannot be routes.",
                declaration
            )
            return null
        }
        if (Modifier.INNER in declaration.modifiers) {
            logger.error(
                "@Route target '$qualifiedName' must be a regular non-inner class. " +
                    "Remove the 'inner' modifier or move the Activity to a top-level class.",
                declaration
            )
            return null
        }
        if (!declaration.isAccessibleFromGeneratedRegistry()) {
            logger.error(
                "@Route target '$qualifiedName' is not accessible from the generated Registry. " +
                    "Use a public or internal top-level class, or an accessible non-inner " +
                    "nested class.",
                declaration
            )
            return null
        }
        if (Modifier.ABSTRACT in declaration.modifiers) {
            logger.error(
                "@Route target '$qualifiedName' must not be abstract. " +
                    "Use a concrete Activity class.",
                declaration
            )
            return null
        }
        if (!declaration.isSubclassOf(AsterCompilerContract.ACTIVITY_CLASS_NAME)) {
            logger.error(
                "@Route target '$qualifiedName' must extend android.app.Activity. " +
                    "Routes can only be declared on Activity subclasses.",
                declaration
            )
            return null
        }

        return RouteEntry(
            path = path,
            className = qualifiedName,
            targetType = declaration.asKotlinPoetClassName(),
            sourceFile = declaration.containingFile
        )
    }
}
