package com.whisper.architecture.bean.business


/**
 * 这是一个业务数据
 *
 * 业务数据可能有三种状态:
 * * 正在加载
 * * 加载成功(提供数据, `null` 也是有效的数据)
 * * 加载失败(提供异常, 如果是业务异常, 应该是[com.whisper.architecture.exception.BusinessException])
 *
 * 传输层对象[com.whisper.architecture.bean.transmit.ApiResponse]提供[com.whisper.architecture.bean.transmit.ApiResponse.toBusiness]转换业务层数据
 *
 * @author whisper
 * @since 2025/9/22
 */
sealed class Business<out T> {

    data class Success<T>(val data: T) : Business<T>()
    data class Error(val e: Exception) : Business<Nothing>()
    object Loading : Business<Nothing>()

}