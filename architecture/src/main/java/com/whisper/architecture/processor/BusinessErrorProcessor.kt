package com.whisper.architecture.processor

import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.function.withBusinessError
import kotlinx.coroutines.flow.Flow


/**
 * 业务错误处理接口
 *
 * 用于在 Flow 中对 [Business.Error] 事件进行处理,并决定是否向下游传播
 *
 * @author whisper
 * @since 2025/12/25
 */
interface BusinessErrorProcessor {

    companion object {

        /**
         * 空实现,表示不处理错误
         */
        val NONE: BusinessErrorProcessor = object : BusinessErrorProcessor {
            override fun onBusinessError(error: Business.Error) = false
        }

    }

    /**
     * 处理业务错误事件
     *
     * @param error 当前的 [Business.Error] 对象
     *
     * @return
     * * true 表示该 error 不再向下游传播
     * * false 表示该 error 继续向下游传播
     */
    fun onBusinessError(error: Business.Error): Boolean

    fun <T> Flow<Business<T>>.withBusinessError(): Flow<Business<T>> =
        this@withBusinessError.withBusinessError(this@BusinessErrorProcessor)

}