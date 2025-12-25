package com.whisper.architecture.function

import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.processor.BusinessErrorProcessor
import com.whisper.architecture.processor.BusinessProgressProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform


/**
 * 给 Flow 添加业务进度处理功能。
 *
 * 会在 Flow 开始收集时调用 [BusinessProgressProcessor.onBusinessStart]
 *
 * 在 Flow 完成收集时调用 [BusinessProgressProcessor.onBusinessCompletion]
 *
 * 并根据 [consume] 参数决定是否吞掉 `Business.Loading` 事件。
 *
 * @param consume 如果为 `true`，会吞掉 `Business.Loading` 事件，不向下游传播。
 *                如果为 `false`，`Business.Loading` 会继续向下游传递。
 *                默认值为 [BusinessProgressProcessor.NONE] 时可以保留 Loading。
 * @param processor 用于处理进度事件的 [BusinessProgressProcessor]，默认值为 [BusinessProgressProcessor.NONE]。
 * @return 返回一个新的 `Flow<Business<T>>`，其包含进度事件处理逻辑。
 */
fun <T> Flow<Business<T>>.withBusinessProgress(
    consume: Boolean = false,
    processor: BusinessProgressProcessor
): Flow<Business<T>> =
    this.onStart { processor.onBusinessStart() }
        .onCompletion { processor.onBusinessCompletion() }
        .filter { value -> !consume || value !is Business.Loading }

/**
 * 给 Flow 添加业务错误处理功能。
 *
 * 对于每个 `Business.Error` 会调用 [BusinessErrorProcessor.onBusinessError]。
 * 如果返回 `true`，该错误会被吞掉，不再向下游传播；
 * 如果返回 `false`，该错误会继续向下游传递。
 *
 * @param processor 用于处理错误事件的 [BusinessErrorProcessor]，默认值为 [BusinessErrorProcessor.NONE]。
 * @return 返回一个新的 `Flow<Business<T>>`，包含错误处理逻辑。
 */
fun <T> Flow<Business<T>>.withBusinessError(processor: BusinessErrorProcessor): Flow<Business<T>> =
    this.transform { value ->
        when (value) {
            is Business.Error -> {
                if (!processor.onBusinessError(value)) {
                    emit(value)
                }
            }

            else -> emit(value)
        }
    }

/**
 * 将 Flow 中的 `Business.Success<T>` 提取出来，丢弃其他类型事件。
 *
 * @return 返回一个新的 `Flow<T>`，只包含 `Business.Success` 的数据。
 */
fun <T> Flow<Business<T>>.onlySuccess(): Flow<T> =
    this.transform { value ->
        if (value is Business.Success<T>) {
            emit(value.data)
        }
    }