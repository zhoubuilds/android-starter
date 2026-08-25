package com.whisper.common.function

import com.whisper.common.model.business.Business
import com.whisper.common.model.business.BusinessOutcome
import kotlinx.coroutines.CancellationException

/**
 * 在业务结果边界内执行请求.
 *
 * 协程取消会继续向上传播, 其他异常会转换为业务错误结果.
 *
 * @param block 业务结果构建逻辑.
 * @return 业务结果.
 */
@PublishedApi
internal suspend inline fun <T> runAsBusinessOutcome(
    crossinline block: suspend () -> BusinessOutcome<T>,
): BusinessOutcome<T> {
    return try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Business.error(exception)
    }
}
