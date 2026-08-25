package com.whisper.architecture.business.processor

import com.whisper.architecture.business.function.consumeSuccessMeta
import com.whisper.architecture.business.model.ArchitectureBusiness
import kotlinx.coroutines.flow.Flow

/**
 * 处理业务成功元信息.
 *
 * Processor 表示业务状态处理协议, 用于避免和 Android Handler 语义混淆.
 * 该处理器接收作为成功状态附带的元信息, 不负责解释具体业务字段.
 *
 * @aegis 保护成功元信息处理协议, 默认实现和 Flow 扩展的转换语义.
 * @author whisper
 * @since 2026/07/27
 */
interface BusinessMetaProcessor {

    companion object {

        /**
         * 不处理业务元信息的默认实现.
         */
        val NONE: BusinessMetaProcessor = object : BusinessMetaProcessor {
            override fun onBusinessMeta(metadata: Any?) = Unit
        }
    }

    /**
     * 处理业务元信息.
     *
     * @param metadata 当前业务元信息.
     */
    fun onBusinessMeta(metadata: Any?)

    /**
     * 使用当前处理器处理成功元信息并转换为业务数据.
     *
     * @receiver 只包含成功状态的业务 Flow.
     * @return 业务数据 Flow.
     */
    fun <T, M> Flow<ArchitectureBusiness.Success<T, M>>.consumeSuccessMeta(): Flow<T> =
        this@consumeSuccessMeta.consumeSuccessMeta(this@BusinessMetaProcessor)
}
