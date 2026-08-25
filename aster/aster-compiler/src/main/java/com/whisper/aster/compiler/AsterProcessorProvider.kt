package com.whisper.aster.compiler

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * 创建 Aster KSP 处理器.
 *
 * AutoService 会为该 Provider 生成标准的 ServiceLoader 注册文件.
 *
 * @aegis 保护 KSP 服务发现使用的 Provider 类型和处理器创建契约.
 * @author whisper
 * @since 2026/07/21
 */
@AutoService(SymbolProcessorProvider::class)
class AsterProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return AsterProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options
        )
    }
}
