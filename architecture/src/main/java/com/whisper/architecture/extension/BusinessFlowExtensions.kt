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
 * 将一次 Flow 收集作为单轮业务操作处理进度, 并保留原始业务类型.
 *
 * 开始收集时开始进度, 收集正常结束、发生异常或取消时结束进度. [Business.Loading] 和
 * [Business.Outcome] 会原样发送给下游, 不参与进度控制. 每个 Collector 独立形成一轮进度.
 * 进度完成回调失败时不覆盖正在传播的上游、下游或取消异常, 回调异常会作为 suppressed exception 保留.
 * 同一次收集可能包含多轮 Loading / Outcome 时使用 [withBusinessProgressCycles].
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的单轮收集生命周期语义.
 * @aegis-audit 2026-08-26 | whisper | 将进度从 Flow 收集生命周期调整为 Business 状态生命周期.
 * @aegis-audit 2026-08-26 | whisper | 保留业务管线主异常并配对处理进度回调失败.
 * @aegis-audit 2026-08-27 | whisper | 统一公开处理器参数命名为 processor.
 * @aegis-audit 2026-08-27 | whisper | 授权拆分单轮收集与多轮状态进度契约, 简化常用单轮入口.
 * @aegis-audit 2026-08-28 | whisper | 授权使用标准 Flow 操作符简化单轮进度实现并保持异常契约.
 */
fun <B : Business<*, *>> Flow<B>.withBusinessProgress(
    processor: BusinessProgressProcessor,
): Flow<B> =
    onStart {
        processor.onBusinessStart()
    }.onCompletion { failure: Throwable? ->
        processor.completeBusinessProgress(failure)
    }

/**
 * 根据业务状态处理多轮进度, 并保留原始业务类型.
 *
 * 每轮首次收到 [Business.Loading] 时开始进度, 后续 [Business.Outcome] 完成下游发送后结束该轮进度.
 * 同轮重复 Loading 不会重复开始; Loading 后发生异常、取消或 Flow 直接结束时也会结束当前进度.
 * 每个 Collector 独立维护状态, Outcome 前没有 Loading 时不会触发进度回调.
 * 进度完成回调失败时不覆盖正在传播的上游、下游或取消异常, 回调异常会作为 suppressed exception 保留.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的多轮业务状态生命周期语义.
 */
fun <B : Business<*, *>> Flow<B>.withBusinessProgressCycles(
    processor: BusinessProgressProcessor,
): Flow<B> = flow {
    var isLoading: Boolean = false

    fun completeLoading(primaryFailure: Throwable? = null) {
        if (!isLoading) {
            return
        }
        isLoading = false
        processor.completeBusinessProgress(primaryFailure)
    }

    var primaryFailure: Throwable? = null
    try {
        collect { business: B ->
            val state: Business<*, *> = business
            when (state) {
                Business.Loading -> {
                    if (!isLoading) {
                        isLoading = true
                        processor.onBusinessStart()
                    }
                    emit(business)
                }

                is Business.Outcome -> {
                    emit(business)
                    completeLoading()
                }
            }
        }
    } catch (failure: Throwable) {
        primaryFailure = failure
        throw failure
    } finally {
        completeLoading(primaryFailure)
    }
}

/**
 * 将一次 Flow 收集作为单轮业务操作处理进度, 并消费加载中状态.
 *
 * [Business.Loading] 不会向下游发送, 成功和失败状态保持其元信息及主要载荷不变.
 * 开始收集时开始进度, 收集正常结束、发生异常或取消时结束进度.
 * 同一次收集可能包含多轮 Loading / Outcome 时使用 [consumeLoadingCycles].
 *
 * @aegis 保护该扩展的单轮进度、状态过滤和数据保留语义.
 * @aegis-audit 2026-08-27 | whisper | 统一公开处理器参数命名为 processor.
 * @aegis-audit 2026-08-27 | whisper | 授权将常用入口收敛为单轮收集进度语义.
 */
fun <M, D> Flow<Business<M, D>>.consumeLoading(
    processor: BusinessProgressProcessor,
): Flow<Business.Outcome<M, D>> =
    withBusinessProgress(processor).transform { business: Business<M, D> ->
        when (business) {
            is Business.Outcome -> emit(business)
            Business.Loading -> Unit
        }
    }

/**
 * 根据业务状态处理多轮进度, 并消费每轮加载中状态.
 *
 * [Business.Loading] 不会向下游发送, 成功和失败状态保持其元信息及主要载荷不变.
 * 每轮 Loading / Outcome 分别配对开始和完成进度.
 *
 * @aegis 保护该扩展的多轮进度、状态过滤和数据保留语义.
 */
fun <M, D> Flow<Business<M, D>>.consumeLoadingCycles(
    processor: BusinessProgressProcessor,
): Flow<Business.Outcome<M, D>> =
    withBusinessProgressCycles(processor).transform { business: Business<M, D> ->
        when (business) {
            is Business.Outcome -> emit(business)
            Business.Loading -> Unit
        }
    }

/**
 * 处理失败状态并保留原始业务 Flow.
 *
 * @aegis 保护该扩展只旁路处理失败状态而不修改数据的语义.
 * @aegis-audit 2026-08-27 | whisper | 统一公开处理器参数命名为 processor.
 */
fun <M, D, B : Business<M, D>> Flow<B>.withBusinessError(
    processor: BusinessErrorProcessor<in M>,
): Flow<B> = onEach { business: B ->
    val state: Business<M, D> = business
    if (state is Business.Failure) {
        processor.onBusinessError(state)
    }
}

/**
 * 处理并消费失败状态, 只向下游发送成功状态.
 *
 * @aegis 保护失败消费和成功数据保留语义.
 * @aegis-audit 2026-08-27 | whisper | 统一公开处理器参数命名为 processor.
 */
fun <M, D> Flow<Business.Outcome<M, D>>.consumeError(
    processor: BusinessErrorProcessor<in M>,
): Flow<Business.Success<M, D>> = transform { outcome: Business.Outcome<M, D> ->
    when (outcome) {
        is Business.Success -> emit(outcome)
        is Business.Failure -> processor.onBusinessError(outcome)
    }
}

/**
 * 处理失败状态并使用兜底载荷恢复为成功状态.
 *
 * 原失败状态的 Meta 会原样保留, [fallback] 可以读取失败响应携带的主要载荷.
 *
 * @aegis 保护错误处理顺序、兜底恢复和 Meta 保留语义.
 * @aegis-audit 2026-08-27 | whisper | 统一公开处理器参数命名为 processor.
 */
fun <M, D> Flow<Business.Outcome<M, D>>.recoverError(
    processor: BusinessErrorProcessor<in M>,
    fallback: (Business.Failure<M, D>) -> D,
): Flow<Business.Success<M, D>> = transform { outcome: Business.Outcome<M, D> ->
    when (outcome) {
        is Business.Success -> emit(outcome)
        is Business.Failure -> {
            processor.onBusinessError(outcome)
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
 * @aegis-audit 2026-08-27 | whisper | 统一公开处理器参数命名为 processor.
 */
fun <M, D> Flow<Business.Success<M, D>>.consumeSuccessMeta(
    processor: BusinessMetaProcessor<in M> = BusinessMetaProcessor.NONE,
): Flow<D> = transform { success: Business.Success<M, D> ->
    processor.onBusinessMeta(meta = success.meta)
    emit(success.data)
}

/**
 * 将成功状态脱壳为主要载荷, 将失败状态转换为 `null`.
 *
 * 失败状态仍会先交给 [processor] 处理. 需要使用失败载荷时应直接处理 [Business.Failure].
 *
 * @aegis 保护错误到 null 的转换和处理顺序.
 * @aegis-audit 2026-08-27 | whisper | 统一公开处理器参数命名为 processor.
 */
fun <M, D : Any> Flow<Business.Outcome<M, D>>.dataOrNull(
    processor: BusinessErrorProcessor<in M>,
): Flow<D?> = transform { outcome: Business.Outcome<M, D> ->
    when (outcome) {
        is Business.Success -> emit(outcome.data)
        is Business.Failure -> {
            processor.onBusinessError(outcome)
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

private fun BusinessProgressProcessor.completeBusinessProgress(primaryFailure: Throwable?) {
    try {
        onBusinessCompletion()
    } catch (completionFailure: Throwable) {
        if (primaryFailure == null) {
            throw completionFailure
        }
        if (completionFailure !== primaryFailure) {
            primaryFailure.addSuppressed(completionFailure)
        }
    }
}
