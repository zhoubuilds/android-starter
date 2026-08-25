package com.whisper.aster.compiler.symbol

import com.whisper.aster.compiler.model.CapabilityEntry
import com.whisper.aster.compiler.model.RouteEntry
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * 校验跨声明的 Aster 约束.
 *
 * @param logger KSP 编译期错误日志记录器.
 *
 * @aegis 保护 Route/Capable 互斥和名称/路径重复声明的失败语义.
 * @author whisper
 * @since 2026/07/22
 */
internal class AsterValidator(
    /**
     * KSP 编译期错误日志记录器.
     */
    private val logger: KSPLogger
) {

    /**
     * 校验路由和能力注解是否错误地同时出现在同一个类上.
     *
     * @param routeSymbols 当前模块中标记了 Route 的类.
     * @param capabilitySymbols 当前模块中标记了 Capable 的类.
     * @return 所有类的注解组合是否合法.
     */
    fun validateAnnotationCombinations(
        routeSymbols: List<KSClassDeclaration>,
        capabilitySymbols: List<KSClassDeclaration>
    ): Boolean {
        val capabilityClassNames: Set<String> = capabilitySymbols
            .mapNotNull { declaration: KSClassDeclaration ->
                declaration.qualifiedName?.asString()
            }
            .toSet()
        var valid: Boolean = true
        routeSymbols.forEach { declaration: KSClassDeclaration ->
            val qualifiedName: String? = declaration.qualifiedName?.asString()
            if (qualifiedName != null && qualifiedName in capabilityClassNames) {
                valid = false
                logger.error(
                    "Class '$qualifiedName' cannot use both @Route and @Capable. " +
                        "Use @Route for Activity navigation or @Capable for a Capability " +
                        "implementation.",
                    declaration
                )
            }
        }
        return valid
    }

    fun validateRouteDuplicates(routes: List<RouteEntry>): Boolean {
        var valid: Boolean = true
        routes.groupBy(RouteEntry::path)
            .filterValues { entries: List<RouteEntry> -> entries.size > 1 }
            .forEach { duplicate: Map.Entry<String, List<RouteEntry>> ->
                val path: String = duplicate.key
                val entries: List<RouteEntry> = duplicate.value
                valid = false
                logger.error(
                    "Duplicate @Route path '$path' in the current module. Each path must map " +
                        "to one Activity class. Conflicting classes: " +
                        "${entries.map(RouteEntry::className)}."
                )
            }
        return valid
    }

    fun validateCapabilityDuplicates(capabilities: List<CapabilityEntry>): Boolean {
        var valid: Boolean = true
        capabilities.groupBy(CapabilityEntry::name)
            .filterValues { entries: List<CapabilityEntry> -> entries.size > 1 }
            .forEach { duplicate: Map.Entry<String, List<CapabilityEntry>> ->
                val name: String = duplicate.key
                val entries: List<CapabilityEntry> = duplicate.value
                valid = false
                logger.error(
                    "Duplicate @Capable name '$name' in the current module. " +
                        "Each capability name must identify one implementation. " +
                        "Conflicting classes: ${entries.map(CapabilityEntry::className)}."
                )
            }
        return valid
    }
}
