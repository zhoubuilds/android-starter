package com.whisper.architecture.processor

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.extension.consumeSuccessMeta
import kotlinx.coroutines.flow.Flow

/**
 * 处理业务成功元信息.
 *
 * Processor 表示业务状态处理协议, 用于避免和 Android Handler 语义混淆.
 * 该处理器接收作为成功状态附带的元信息, 不负责解释具体业务字段.
 *
 * @aegis 保护成功元信息处理协议, 默认实现和 Flow 扩展的转换语义.
 * @aegis-audit 2026-08-27 | whisper | 统一领域元信息参数命名为 meta.
 *
 * @author whisper
 * @since 2026/07/27
 */
interface BusinessMetaProcessor<M> {

    companion object {

        /**
         * 静默丢弃meta数据的实现.
         */
        val NONE: BusinessMetaProcessor<Any?> = object : BusinessMetaProcessor<Any?> {
            override fun onBusinessMeta(meta: Any?) = Unit
        }
    }

    /**
     * 处理业务元信息.
     *
     * @param meta 当前业务元信息.
     */
    fun onBusinessMeta(meta: M)

    /**
     * 使用当前处理器处理成功元信息并转换为业务数据.
     *
     * @receiver 只包含成功状态的业务 Flow.
     * @return 业务数据 Flow.
     */
    fun <D> Flow<Business.Success<M, D>>.consumeSuccessMeta(): Flow<D> =
        this@consumeSuccessMeta.consumeSuccessMeta(this@BusinessMetaProcessor)
}
