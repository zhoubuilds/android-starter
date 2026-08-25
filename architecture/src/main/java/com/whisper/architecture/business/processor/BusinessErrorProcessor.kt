package com.whisper.architecture.business.processor

import com.whisper.architecture.business.function.consumeError
import com.whisper.architecture.business.function.dataOrNull
import com.whisper.architecture.business.function.recoverError
import com.whisper.architecture.business.function.withBusinessError
import com.whisper.architecture.business.model.ArchitectureBusiness
import kotlinx.coroutines.flow.Flow

/**
 * 处理业务错误状态.
 *
 * Processor 表示业务状态处理协议, 用于避免和 Android Handler 语义混淆.
 * 该处理器接收作为 Flow 元素传递的 [ArchitectureBusiness.Error], 不负责捕获 Flow 抛出的异常,
 * 也不负责调度业务流程.
 *
 * @aegis 保护错误处理协议, 默认实现和 Flow 扩展的状态消费语义.
 * @author whisper
 * @since 2026/07/24
 */
interface BusinessErrorProcessor {

    companion object {

        /**
         * 不处理业务错误的默认实现.
         */
        val NONE: BusinessErrorProcessor = object : BusinessErrorProcessor {
            override fun onBusinessError(error: ArchitectureBusiness.Error<*, *>) = Unit
        }
    }

    /**
     * 处理业务错误.
     *
     * @param error 当前业务错误.
     */
    fun onBusinessError(error: ArchitectureBusiness.Error<*, *>)

    /**
     * 为业务 Flow 添加当前错误状态处理器.
     *
     * @receiver 原始业务 Flow.
     * @return 添加错误处理后的 Flow.
     */
    fun <B : ArchitectureBusiness<*, *>> Flow<B>.withBusinessError(): Flow<B> =
        this@withBusinessError.withBusinessError(this@BusinessErrorProcessor)

    /**
     * 使用当前处理器处理并消费错误.
     *
     * @receiver 只包含成功或错误的业务 Flow.
     * @return 只可能发送成功结果的 Flow.
     */
    fun <T, M> Flow<ArchitectureBusiness.Outcome<T, M>>.consumeError(): Flow<ArchitectureBusiness.Success<T, M>> =
        this@consumeError.consumeError(this@BusinessErrorProcessor)

    /**
     * 使用当前处理器处理错误并恢复为成功结果.
     *
     * @param fallback 根据业务错误生成兜底数据.
     * @receiver 只包含成功或错误的业务 Flow.
     * @return 只可能发送成功结果的 Flow.
     */
    fun <T, M> Flow<ArchitectureBusiness.Outcome<T, M>>.recoverError(
        fallback: (ArchitectureBusiness.Error<T, M>) -> T,
    ): Flow<ArchitectureBusiness.Success<T, M>> =
        this@recoverError.recoverError(this@BusinessErrorProcessor, fallback)

    /**
     * 使用当前处理器处理错误并转换为可空数据.
     *
     * @receiver 只包含成功或错误的业务 Flow.
     * @return 可空业务数据 Flow.
     */
    fun <T : Any, M> Flow<ArchitectureBusiness.Outcome<T, M>>.dataOrNull(): Flow<T?> =
        this@dataOrNull.dataOrNull(this@BusinessErrorProcessor)
}
