package com.whisper.aster.compiler

import com.whisper.aster.compiler.codegen.AsterRegistryWriter
import com.whisper.aster.compiler.model.CapabilityEntry
import com.whisper.aster.compiler.model.RouteEntry
import com.whisper.aster.compiler.symbol.AsterValidator
import com.whisper.aster.compiler.symbol.CapabilityParser
import com.whisper.aster.compiler.symbol.RouteParser
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate

/**
 * 处理模块中的 [com.whisper.aster.runtime.annotation.Route] 和
 * [com.whisper.aster.runtime.annotation.Capable] 注解并生成模块注册器.
 *
 * KSP 只处理当前模块的路由和能力. 跨模块内容由各模块生成的 Manifest 索引在运行时发现.
 *
 * @param codeGenerator KSP 源码生成器.
 * @param logger KSP 编译期错误日志记录器.
 * @param options Gradle 插件传递给 KSP 的处理器参数.
 *
 * @author whisper
 * @since 2026/07/21
 */
class AsterProcessor(
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
) : SymbolProcessor {

    /**
     * Route 声明解析器.
     */
    private val routeParser: RouteParser = RouteParser(logger)

    /**
     * Capable 声明解析器.
     */
    private val capabilityParser: CapabilityParser = CapabilityParser(logger)

    /**
     * 跨声明约束校验器.
     */
    private val validator: AsterValidator = AsterValidator(logger)

    /**
     * 模块 Registry 源码生成器.
     */
    private val registryWriter: AsterRegistryWriter = AsterRegistryWriter(
        codeGenerator = codeGenerator,
        logger = logger,
        options = options
    )

    /**
     * 跨 KSP 轮次累计的路由条目, key 为目标类全限定名.
     */
    private val routesByClassName: MutableMap<String, RouteEntry> = linkedMapOf()

    /**
     * 跨 KSP 轮次累计的能力条目, key 为实现类全限定名.
     */
    private val capabilitiesByClassName: MutableMap<String, CapabilityEntry> = linkedMapOf()

    /**
     * 当前仍需要 KSP 后续轮次解析的声明.
     */
    private var deferredSymbols: List<KSClassDeclaration> = emptyList()

    /**
     * 当前模块中参与增量处理依赖的源文件.
     */
    private val moduleSourceFiles: MutableSet<KSFile> = linkedSetOf()

    /**
     * 标记当前处理流程是否已经出现错误, 出错后禁止生成 Registry.
     */
    private var processingFailed: Boolean = false

    /**
     * 收集当前 KSP 轮次中已经可以解析的路由和能力.
     *
     * @param resolver 当前 KSP 符号解析器.
     * @return 当前轮次需要延迟处理的符号.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processingFailed) {
            return emptyList()
        }

        moduleSourceFiles += resolver.getAllFiles().toList()

        val routeSymbols: List<KSClassDeclaration> = resolver
            .getSymbolsWithAnnotation(AsterCompilerContract.ROUTE_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
        val capabilitySymbols: List<KSClassDeclaration> = resolver
            .getSymbolsWithAnnotation(AsterCompilerContract.CAPABLE_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val segment: String = readSegment() ?: run {
            processingFailed = true
            return emptyList()
        }

        val annotationCombinationsValid: Boolean = validator.validateAnnotationCombinations(
            routeSymbols = routeSymbols,
            capabilitySymbols = capabilitySymbols
        )

        val currentDeferredSymbols: MutableList<KSClassDeclaration> = mutableListOf()
        val validRouteSymbols: List<KSClassDeclaration> = routeSymbols.filter { declaration ->
            declaration.validate().also { valid: Boolean ->
                if (!valid) {
                    currentDeferredSymbols += declaration
                }
            }
        }
        val validCapabilitySymbols: List<KSClassDeclaration> =
            capabilitySymbols.filter { declaration ->
                declaration.validate().also { valid: Boolean ->
                    if (!valid) {
                        currentDeferredSymbols += declaration
                    }
                }
            }
        deferredSymbols = currentDeferredSymbols.distinct()

        var declarationsValid: Boolean = true
        validRouteSymbols.forEach { declaration: KSClassDeclaration ->
            val route: RouteEntry? = routeParser.parse(declaration, segment)
            if (route == null) {
                declarationsValid = false
            } else {
                routesByClassName[route.className] = route
            }
        }
        validCapabilitySymbols.forEach { declaration: KSClassDeclaration ->
            val capability: CapabilityEntry? = capabilityParser.parse(declaration, segment)
            if (capability == null) {
                declarationsValid = false
            } else {
                capabilitiesByClassName[capability.className] = capability
            }
        }

        val routesValid: Boolean =
            validator.validateRouteDuplicates(routesByClassName.values.toList())
        val capabilitiesValid: Boolean =
            validator.validateCapabilityDuplicates(capabilitiesByClassName.values.toList())
        val currentRoundValid: Boolean = annotationCombinationsValid &&
            declarationsValid &&
            routesValid &&
            capabilitiesValid
        if (!currentRoundValid) {
            processingFailed = true
        }
        return deferredSymbols
    }

    /**
     * 在全部 KSP 轮次结束后校验累计结果并生成模块 Registry.
     */
    override fun finish() {
        if (processingFailed) {
            return
        }

        if (deferredSymbols.isNotEmpty()) {
            deferredSymbols.forEach { declaration: KSClassDeclaration ->
                val qualifiedName: String = declaration.qualifiedName?.asString()
                    ?: declaration.simpleName.asString()
                logger.error(
                    "Aster cannot process '$qualifiedName' because referenced symbols remain " +
                        "unresolved after all KSP rounds. Ensure its generated dependencies " +
                        "are available in the same compilation.",
                    declaration
                )
            }
            processingFailed = true
            return
        }

        val routes: List<RouteEntry> = routesByClassName.values.toList()
        val capabilities: List<CapabilityEntry> = capabilitiesByClassName.values.toList()
        val routesValid: Boolean = validator.validateRouteDuplicates(routes)
        val capabilitiesValid: Boolean = validator.validateCapabilityDuplicates(capabilities)
        if (!routesValid || !capabilitiesValid) {
            processingFailed = true
            return
        }

        val annotatedSourceFiles: List<KSFile> =
            routes.mapNotNull(RouteEntry::sourceFile) +
                capabilities.mapNotNull(CapabilityEntry::sourceFile)
        val sourceFiles: List<KSFile> = annotatedSourceFiles.distinct().ifEmpty {
            moduleSourceFiles.toList()
        }
        if (!registryWriter.write(routes, capabilities, sourceFiles)) {
            processingFailed = true
        }
    }

    /**
     * 响应 KSP 或其他处理器报告的错误, 确保失败构建不会生成 Registry.
     */
    override fun onError() {
        processingFailed = true
    }

    private fun readSegment(): String? {
        val segment: String = options[AsterCompilerContract.OPTION_SEGMENT]?.trim().orEmpty()
        if (segment.isEmpty()) {
            logger.error(
                "Missing required aster.segment. Add aster { segment = \"feature\" } " +
                    "to the Android module build.gradle.kts file. The value must be one " +
                    "lowercase segment, for example 'feature'."
            )
            return null
        }
        if (!AsterCompilerContract.SEGMENT_PATTERN.matches(segment)) {
            logger.error(
                "Invalid aster.segment '$segment'. Expected exactly one segment matching " +
                    "[a-z][a-z0-9_]*, without '/' or '.'. Example: 'feature'."
            )
            return null
        }
        return segment
    }
}
