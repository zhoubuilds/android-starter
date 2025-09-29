package com.whisper.architecture.function

import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.bean.transmit.ApiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


/**
 *
 * @author whisper
 * @since 2025/9/26
 */

fun <T, R> callAsBusinessFlow(
    transformer: (T) -> R,
    block: suspend () -> ApiResponse<T>
): Flow<Business<R>> = flow {
    emit(Business.Loading)
    try {
        emit(block().toBusiness(transformer))
    } catch (e: Exception) {
        emit(Business.Error(e))
    }
}

fun <T> callAsBusinessFlow(block: suspend () -> ApiResponse<T>): Flow<Business<T>> =
    callAsBusinessFlow({ it }, block)