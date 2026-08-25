package com.whisper.common.function

import com.whisper.architecture.business.function.withLoading
import com.whisper.common.model.business.Business
import com.whisper.common.model.business.BusinessOutcome
import com.whisper.common.model.transmit.ApiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 将传输层请求包装为只包含成功或错误的业务 Flow.
 *
 * 请求成功时将响应转换为 `BusinessSuccess` 或业务 `BusinessError`, 请求抛出异常时转换为
 * `BusinessError`. 协程取消异常会继续向上抛出.
 *
 * @param block 传输层请求.
 * @param transformer 传输数据转换函数.
 * @return 只包含成功或错误的业务 Flow.
 */
inline fun <T, R> callAsBusinessOutcomeFlow(
    crossinline block: suspend () -> ApiResponse<T>,
    noinline transformer: (T?) -> R,
): Flow<BusinessOutcome<R>> = flow {
    emit(
        runAsBusinessOutcome {
            block().toBusiness(transformer)
        }
    )
}

/**
 * 将传输层请求包装为只包含成功或错误的业务 Flow.
 *
 * @param block 传输层请求.
 * @return 只包含成功或错误的业务 Flow.
 */
inline fun <T> callAsBusinessOutcomeFlow(
    crossinline block: suspend () -> ApiResponse<T>,
): Flow<BusinessOutcome<T?>> = callAsBusinessOutcomeFlow(block) { data: T? -> data }

/**
 * 将传输层请求包装为包含加载中, 成功和错误的业务 Flow.
 *
 * @param block 传输层请求.
 * @param transformer 传输数据转换函数.
 * @return 完整业务状态 Flow.
 */
inline fun <T, R> callAsBusinessFlow(
    crossinline block: suspend () -> ApiResponse<T>,
    noinline transformer: (T?) -> R,
): Flow<Business<R>> = callAsBusinessOutcomeFlow(block, transformer).withLoading()

/**
 * 将传输层请求包装为包含加载中, 成功和错误的业务 Flow.
 *
 * @param block 传输层请求.
 * @return 完整业务状态 Flow.
 */
inline fun <T> callAsBusinessFlow(
    crossinline block: suspend () -> ApiResponse<T>,
): Flow<Business<T?>> = callAsBusinessFlow(block) { data: T? -> data }
