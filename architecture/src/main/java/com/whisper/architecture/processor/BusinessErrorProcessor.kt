package com.whisper.architecture.processor

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.extension.consumeError
import com.whisper.architecture.extension.dataOrNull
import com.whisper.architecture.extension.recoverError
import com.whisper.architecture.extension.withBusinessError
import kotlinx.coroutines.flow.Flow

/**
 * 处理业务错误状态.
 *
 * Processor 表示业务状态处理协议, 用于避免和 Android Handler 语义混淆.
 * 该处理器接收作为 Flow 元素传递的 [Business.Failure], 不负责捕获 Flow 抛出的异常,
 * 也不负责调度业务流程.
 *
 * @aegis 保护错误处理协议, 默认实现和 Flow 扩展的状态消费语义.
 *
 * @author whisper
 * @since 2026/07/24
 */
interface BusinessErrorProcessor<M> {

    companion object {

        /**
         * 静默丢弃错误的实现.
         */
        val NONE: BusinessErrorProcessor<Any?> = object : BusinessErrorProcessor<Any?> {
            override fun onBusinessError(error: Business.Failure<Any?, *>) = Unit
        }
    }

    /**
     * 处理业务错误.
     *
     * @param error 当前业务错误.
     */
    fun onBusinessError(error: Business.Failure<M, *>)

    /**
     * 为业务 Flow 添加当前错误状态处理器.
     *
     * @receiver 原始业务 Flow.
     * @return 添加错误处理后的 Flow.
     */
    fun <D, B : Business<M, D>> Flow<B>.withBusinessError(): Flow<B> =
        this@withBusinessError.withBusinessError(this@BusinessErrorProcessor)

    /**
     * 使用当前处理器处理并消费错误.
     *
     * @receiver 只包含成功或失败的业务 Flow.
     * @return 只可能发送成功结果的 Flow.
     */
    fun <D> Flow<Business.Outcome<M, D>>.consumeError(): Flow<Business.Success<M, D>> =
        this@consumeError.consumeError(this@BusinessErrorProcessor)

    /**
     * 使用当前处理器处理错误并恢复为成功结果.
     *
     * @param fallback 根据业务错误生成兜底数据.
     * @receiver 只包含成功或失败的业务 Flow.
     * @return 只可能发送成功结果的 Flow.
     */
    fun <D> Flow<Business.Outcome<M, D>>.recoverError(
        fallback: (Business.Failure<M, D>) -> D,
    ): Flow<Business.Success<M, D>> =
        this@recoverError.recoverError(this@BusinessErrorProcessor, fallback)

    /**
     * 使用当前处理器处理错误并转换为可空数据.
     *
     * @receiver 只包含成功或失败的业务 Flow.
     * @return 可空业务数据 Flow.
     */
    fun <D : Any> Flow<Business.Outcome<M, D>>.dataOrNull(): Flow<D?> =
        this@dataOrNull.dataOrNull(this@BusinessErrorProcessor)
}
