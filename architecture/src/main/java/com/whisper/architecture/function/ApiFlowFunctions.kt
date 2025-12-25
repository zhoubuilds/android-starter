package com.whisper.architecture.function

import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.bean.transmit.ApiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 将一个传输层 `suspend` 函数包装为业务层 `Flow`。
 *
 * 该函数在 Flow 中执行 [block] 网络请求，并将返回结果通过 [transformer] 转换为业务层对象。
 *
 * 注意：
 * - 这里函数被声明为 `inline`，主要是为了允许使用 [crossinline] 和 [noinline] 修饰符，
 *   表达 lambda 的调用约束，而非性能优化。
 * - [block] 在 Flow 内延迟执行（逃逸 lambda），禁止非局部 return。
 * - [transformer] lambda 可能会被传递给其它函数使用（逃逸），不可进行非局部 return。
 *
 * @param block 网络请求的 suspend 函数
 * @param transformer 将传输层对象转换为业务层对象的函数
 * @return 返回一个 Flow，流中元素类型为 [Business]，表示加载状态、成功结果或异常
 */
inline fun <T, R> callAsBusinessFlow(
    crossinline block: suspend () -> ApiResponse<T>,
    noinline transformer: (T) -> R
): Flow<Business<R>> = flow {
    emit(Business.Loading)
    try {
        emit(block().toBusiness(transformer))
    } catch (e: Exception) {
        emit(Business.Error(e))
    }
}

/**
 * 将一个传输层的 `suspend` 函数包装为业务层 `Flow`，直接返回原始数据。
 *
 * 这是 [callAsBusinessFlow] 的简化版本，适用于不需要额外转换的场景。
 * 它会在 Flow 内执行 [block] 网络请求，并将返回结果直接包装为 [Business] 类型。
 *
 * 注意：
 * - 该函数是 `inline`，主要是为了保持高阶函数风格，并允许 `crossinline` 修饰符。
 * - [block] 在 Flow 内延迟执行（逃逸 lambda），禁止非局部 return。
 *
 * @param block 网络请求的 suspend 函数
 * @return 返回一个 Flow，流中元素类型为 [Business]，表示加载状态、成功结果或异常
 */
inline fun <T> callAsBusinessFlow(
    crossinline block: suspend () -> ApiResponse<T>
): Flow<Business<T>> = callAsBusinessFlow(block) { it }
