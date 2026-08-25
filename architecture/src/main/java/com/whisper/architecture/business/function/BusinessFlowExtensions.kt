package com.whisper.architecture.business.function

import com.whisper.architecture.business.model.ArchitectureBusiness
import com.whisper.architecture.business.processor.BusinessErrorProcessor
import com.whisper.architecture.business.processor.BusinessMetaProcessor
import com.whisper.architecture.business.processor.BusinessProgressProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform

/**
 * 为业务 Flow 添加收集进度处理, 并保留原始业务类型.
 *
 * Flow 开始收集时调用 [BusinessProgressProcessor.onBusinessStart], 收集结束时调用
 * [BusinessProgressProcessor.onBusinessCompletion]. 正常完成, 异常和取消都会结束本次收集.
 * 该方法根据 Flow 的收集生命周期处理进度, 不检查或过滤 [ArchitectureBusiness.Loading] 状态.
 * 页面级加载展示可由处理器接入 Architecture UI 状态.
 *
 * @param handler 业务进度处理器.
 * @return 添加进度处理后的原类型 Flow.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的收集生命周期语义.
 */
fun <B : ArchitectureBusiness<*, *>> Flow<B>.withBusinessProgress(
    handler: BusinessProgressProcessor,
): Flow<B> =
    onStart { handler.onBusinessStart() }
        .onCompletion { handler.onBusinessCompletion() }

/**
 * 处理收集进度并消费加载中状态.
 *
 * 收集生命周期会交给 [handler] 处理, [ArchitectureBusiness.Loading] 不会向下游发送, 其他状态保持不变.
 * 当上游没有产生业务结果或只产生加载中状态时, 返回的 Flow 不会产生任何元素.
 * 该方法不会捕获上游 Flow 抛出的异常.
 * 页面级待处理任务计数由 [handler] 决定是否更新.
 *
 * @param handler 业务进度处理器.
 * @return 只包含成功或错误的 Flow.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的状态过滤及异常传播语义.
 */
fun <T, M> Flow<ArchitectureBusiness<T, M>>.consumeLoading(
    handler: BusinessProgressProcessor,
): Flow<ArchitectureBusiness.Outcome<T, M>> =
    withBusinessProgress(handler).transform { business: ArchitectureBusiness<T, M> ->
        when (business) {
            is ArchitectureBusiness.Outcome -> emit(business)
            ArchitectureBusiness.Loading -> Unit
        }
    }

/**
 * 为业务 Flow 添加错误状态处理, 并保留原始业务类型.
 *
 * 每个 [ArchitectureBusiness.Error] 会先交给 [handler] 处理, 随后继续发送给下游.
 * 该方法只处理作为 Flow 元素传递的错误状态, 不会捕获上游 Flow 抛出的异常.
 *
 * @param handler 业务错误处理器.
 * @return 添加错误处理后的原类型 Flow.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的错误旁路处理语义.
 */
fun <B : ArchitectureBusiness<*, *>> Flow<B>.withBusinessError(
    handler: BusinessErrorProcessor,
): Flow<B> = onEach { business: B ->
    if (business is ArchitectureBusiness.Error<*, *>) {
        handler.onBusinessError(business)
    }
}

/**
 * 处理并消费错误状态.
 *
 * 每个 [ArchitectureBusiness.Error] 会交给 [handler] 处理, 但不会向下游发送;
 * [ArchitectureBusiness.Success] 保持不变.
 * 当上游只产生错误状态时, 返回的 Flow 将正常完成且不产生任何元素.
 * 该方法不会捕获上游 Flow 抛出的异常.
 *
 * @param handler 业务错误处理器.
 * @return 只包含成功结果的 Flow.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的错误消费语义.
 */
fun <T, M> Flow<ArchitectureBusiness.Outcome<T, M>>.consumeError(
    handler: BusinessErrorProcessor,
): Flow<ArchitectureBusiness.Success<T, M>> = transform { outcome: ArchitectureBusiness.Outcome<T, M> ->
    when (outcome) {
        is ArchitectureBusiness.Success -> emit(outcome)
        is ArchitectureBusiness.Error -> handler.onBusinessError(outcome)
    }
}

/**
 * 处理错误并使用业务兜底数据恢复为成功结果.
 *
 * 每个 [ArchitectureBusiness.Error] 会先交给 [handler] 处理, 再通过 [fallback] 转换为
 * [ArchitectureBusiness.Success]; 原有成功结果保持不变.
 * 仅当兜底值在业务语义上可以作为成功数据时使用该方法.
 * 该方法不会捕获上游 Flow, [handler] 或 [fallback] 抛出的异常.
 *
 * @param handler 业务错误处理器.
 * @param fallback 根据业务错误生成兜底数据.
 * @return 只包含成功结果的 Flow.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的错误恢复及元信息保留语义.
 */
fun <T, M> Flow<ArchitectureBusiness.Outcome<T, M>>.recoverError(
    handler: BusinessErrorProcessor,
    fallback: (ArchitectureBusiness.Error<T, M>) -> T,
): Flow<ArchitectureBusiness.Success<T, M>> = transform { outcome: ArchitectureBusiness.Outcome<T, M> ->
    when (outcome) {
        is ArchitectureBusiness.Success -> emit(outcome)
        is ArchitectureBusiness.Error -> {
            handler.onBusinessError(outcome)
            emit(
                ArchitectureBusiness.Success(
                    data = fallback(outcome),
                    metadata = outcome.metadata,
                )
            )
        }
    }
}

/**
 * 处理成功元信息并将成功状态转换为业务数据.
 *
 * 每个 [ArchitectureBusiness.Success] 的元信息会先交给 [handler] 处理, 再向下游发送业务数据.
 * 该方法用于显式处理成功提示等元信息, 避免脱壳时静默丢弃成功元信息.
 * 该方法不会捕获上游 Flow 或 [handler] 抛出的异常.
 *
 * @param handler 业务元信息处理器.
 * @return 业务数据 Flow.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的元信息处理顺序及脱壳语义.
 */
fun <T, M> Flow<ArchitectureBusiness.Success<T, M>>.consumeSuccessMeta(
    handler: BusinessMetaProcessor = BusinessMetaProcessor.NONE,
): Flow<T> = transform { success: ArchitectureBusiness.Success<T, M> ->
    handler.onBusinessMeta(success.metadata)
    emit(success.data)
}

/**
 * 处理错误并将业务结果转换为可空数据.
 *
 * 成功时发送业务数据; 错误时先交给 [handler] 处理, 再发送 `null`.
 * 类型参数限制为非空类型, 因此返回值中的 `null` 只表示业务错误.
 * 错误状态附带的数据不会在该方法中发送, 需要保留错误数据时应直接处理错误状态.
 * 该方法不会捕获上游 Flow 或 [handler] 抛出的异常.
 *
 * @param handler 业务错误处理器.
 * @return 可空业务数据 Flow.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的错误到 null 转换语义.
 */
fun <T : Any, M> Flow<ArchitectureBusiness.Outcome<T, M>>.dataOrNull(
    handler: BusinessErrorProcessor,
): Flow<T?> = transform { outcome: ArchitectureBusiness.Outcome<T, M> ->
    when (outcome) {
        is ArchitectureBusiness.Success -> emit(outcome.data)
        is ArchitectureBusiness.Error -> {
            handler.onBusinessError(outcome)
            emit(null)
        }
    }
}

/**
 * 为不包含加载中状态的业务结果添加加载中状态.
 *
 * 每次收集时先发送一个 [ArchitectureBusiness.Loading], 再发送上游的全部业务结果.
 * 该方法不会发送单独的加载完成状态, 也不会捕获上游 Flow 抛出的异常.
 *
 * @return 包含加载中, 成功和错误状态的 Flow.
 *
 * @aegis 保护该扩展的签名和 KDoc 已明确的加载状态发送及异常传播语义.
 */
fun <T, M> Flow<ArchitectureBusiness.Outcome<T, M>>.withLoading(): Flow<ArchitectureBusiness<T, M>> =
    flow {
        emit(ArchitectureBusiness.Loading)
        collect { outcome: ArchitectureBusiness.Outcome<T, M> -> emit(outcome) }
    }
