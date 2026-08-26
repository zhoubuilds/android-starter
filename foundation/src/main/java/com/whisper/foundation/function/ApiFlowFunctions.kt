package com.whisper.foundation.function

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.extension.withLoading
import com.whisper.foundation.model.business.BusinessMetadata
import com.whisper.foundation.model.transmit.ApiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 将传输层请求包装为只包含成功或失败的业务 Flow.
 *
 * 响应中的 Meta 和主要载荷会在成功及业务失败路径中完整保留. 请求未产生响应时,
 * 失败状态使用 [BusinessMetadata.EMPTY] 和 `null` 主要载荷.
 */
inline fun <T, R> callAsBusinessOutcomeFlow(
    crossinline block: suspend () -> ApiResponse<T>,
    noinline transformer: (T?) -> R,
): Flow<Business.Outcome<BusinessMetadata, R?>> = flow {
    emit(
        runAsBusinessOutcome(
            onFailure = { exception: Exception ->
                Business.Failure(
                    exception = exception,
                    meta = BusinessMetadata.EMPTY,
                    data = null,
                )
            },
            block = {
                val outcome: Business.Outcome<BusinessMetadata, R> = block().toBusiness(transformer)
                outcome
            },
        )
    )
}

/** 将传输层请求包装为只包含成功或失败的业务 Flow. */
inline fun <T> callAsBusinessOutcomeFlow(
    crossinline block: suspend () -> ApiResponse<T>,
): Flow<Business.Outcome<BusinessMetadata, T?>> =
    callAsBusinessOutcomeFlow(block) { data: T? -> data }

/** 将传输层请求包装为包含 Loading、成功和失败的业务 Flow. */
inline fun <T, R> callAsBusinessFlow(
    crossinline block: suspend () -> ApiResponse<T>,
    noinline transformer: (T?) -> R,
): Flow<Business<BusinessMetadata, R?>> =
    callAsBusinessOutcomeFlow(block, transformer).withLoading()

/** 将传输层请求包装为包含 Loading、成功和失败的业务 Flow. */
inline fun <T> callAsBusinessFlow(
    crossinline block: suspend () -> ApiResponse<T>,
): Flow<Business<BusinessMetadata, T?>> =
    callAsBusinessFlow(block) { data: T? -> data }
