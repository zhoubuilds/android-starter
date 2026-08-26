package com.whisper.architecture.extension

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.processor.BusinessErrorProcessor
import com.whisper.architecture.processor.BusinessMetaProcessor
import com.whisper.architecture.processor.BusinessProgressProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform

/**
 * 为业务 Flow 添加收集进度处理, 并保留原始业务类型.
 *
 * Flow 开始收集时调用 [com.whisper.architecture.processor.BusinessProgressProcessor.onBusinessStart], 收集结束时调用
 * [com.whisper.architecture.processor.BusinessProgressProcessor.onBusinessCompletion]. 正常完成、异常和取消都会结束本次收集.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的收集生命周期语义.
 */
fun <B : Business<*, *>> Flow<B>.withBusinessProgress(
    handler: BusinessProgressProcessor,
): Flow<B> =
    onStart { handler.onBusinessStart() }
        .onCompletion { handler.onBusinessCompletion() }

/**
 * 处理收集进度并消费加载中状态.
 *
 * [Business.Loading] 不会向下游发送, 成功和失败状态保持其元信息及主要载荷不变.
 *
 * @aegis 保护该扩展的状态过滤和数据保留语义.
 */
fun <M, D> Flow<Business<M, D>>.consumeLoading(
    handler: BusinessProgressProcessor,
): Flow<Business.Outcome<M, D>> =
    withBusinessProgress(handler).transform { business: Business<M, D> ->
        when (business) {
            is Business.Outcome -> emit(business)
            Business.Loading -> Unit
        }
    }

/**
 * 处理失败状态并保留原始业务 Flow.
 *
 * @aegis 保护该扩展只旁路处理失败状态而不修改数据的语义.
 */
fun <M, D, B : Business<M, D>> Flow<B>.withBusinessError(
    handler: BusinessErrorProcessor<in M>,
): Flow<B> = onEach { business: B ->
    val state: Business<M, D> = business
    if (state is Business.Failure) {
        handler.onBusinessError(state)
    }
}

/**
 * 处理并消费失败状态, 只向下游发送成功状态.
 *
 * @aegis 保护失败消费和成功数据保留语义.
 */
fun <M, D> Flow<Business.Outcome<M, D>>.consumeError(
    handler: BusinessErrorProcessor<in M>,
): Flow<Business.Success<M, D>> = transform { outcome: Business.Outcome<M, D> ->
    when (outcome) {
        is Business.Success -> emit(outcome)
        is Business.Failure -> handler.onBusinessError(outcome)
    }
}

/**
 * 处理失败状态并使用兜底载荷恢复为成功状态.
 *
 * 原失败状态的 Meta 会原样保留, [fallback] 可以读取失败响应携带的主要载荷.
 *
 * @aegis 保护错误处理顺序、兜底恢复和 Meta 保留语义.
 */
fun <M, D> Flow<Business.Outcome<M, D>>.recoverError(
    handler: BusinessErrorProcessor<in M>,
    fallback: (Business.Failure<M, D>) -> D,
): Flow<Business.Success<M, D>> = transform { outcome: Business.Outcome<M, D> ->
    when (outcome) {
        is Business.Success -> emit(outcome)
        is Business.Failure -> {
            handler.onBusinessError(outcome)
            emit(
                Business.Success(
                    meta = outcome.meta,
                    data = fallback(outcome),
                )
            )
        }
    }
}

/**
 * 处理成功状态的 Meta 并脱壳主要载荷.
 *
 * @aegis 保护 Meta 处理先于数据发送且不解释 Meta 内容的语义.
 */
fun <M, D> Flow<Business.Success<M, D>>.consumeSuccessMeta(
    handler: BusinessMetaProcessor<in M> = BusinessMetaProcessor.NONE,
): Flow<D> = transform { success: Business.Success<M, D> ->
    handler.onBusinessMeta(success.meta)
    emit(success.data)
}

/**
 * 将成功状态脱壳为主要载荷, 将失败状态转换为 `null`.
 *
 * 失败状态仍会先交给 [handler] 处理. 需要使用失败载荷时应直接处理 [Business.Failure].
 *
 * @aegis 保护错误到 null 的转换和处理顺序.
 */
fun <M, D : Any> Flow<Business.Outcome<M, D>>.dataOrNull(
    handler: BusinessErrorProcessor<in M>,
): Flow<D?> = transform { outcome: Business.Outcome<M, D> ->
    when (outcome) {
        is Business.Success -> emit(outcome.data)
        is Business.Failure -> {
            handler.onBusinessError(outcome)
            emit(null)
        }
    }
}

/**
 * 为已完成的业务结果添加单例加载状态.
 *
 * 每次收集先发送 [Business.Loading], 再发送上游的全部结果.
 *
 * @aegis 保护单例 Loading 的发送顺序和结果数据保留语义.
 */
fun <M, D> Flow<Business.Outcome<M, D>>.withLoading(): Flow<Business<M, D>> =
    flow {
        emit(Business.Loading)
        collect { outcome: Business.Outcome<M, D> -> emit(outcome) }
    }
