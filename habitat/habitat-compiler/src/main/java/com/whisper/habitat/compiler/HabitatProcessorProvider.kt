package com.whisper.habitat.compiler

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Habitat KSP 处理器提供者.
 *
 * 为 KSP 创建 Habitat 注解处理器实例.
 *
 * @aegis 保护 KSP 服务发现使用的 Provider 类型和处理器创建契约.
 * @author whisper
 * @since 2026/07/27
 */
@AutoService(SymbolProcessorProvider::class)
class HabitatProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val codeGenerator: CodeGenerator = environment.codeGenerator
        val logger: KSPLogger = environment.logger
        val options: Map<String, String> = environment.options
        return HabitatSymbolProcessor(codeGenerator, logger, options)
    }
}
