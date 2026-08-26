package com.whisper.foundation.function

import com.whisper.architecture.model.domain.Business
import kotlinx.coroutines.CancellationException

/**
 * 在业务结果边界内执行请求.
 *
 * 协程取消会继续向上传播, 其他异常由 [onFailure] 转换为完整的失败状态.
 */
@PublishedApi
internal suspend inline fun <M, D> runAsBusinessOutcome(
    crossinline onFailure: (Exception) -> Business.Failure<M, D>,
    crossinline block: suspend () -> Business.Outcome<M, D>,
): Business.Outcome<M, D> {
    return try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        onFailure(exception)
    }
}
