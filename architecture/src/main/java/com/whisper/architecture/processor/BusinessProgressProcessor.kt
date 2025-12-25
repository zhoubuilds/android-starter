package com.whisper.architecture.processor

import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.function.withBusinessProgress
import kotlinx.coroutines.flow.Flow

/**
 * 业务进度处理接口
 *
 * 用于在 Flow 中处理开始和完成事件,例如显示或隐藏加载状态
 *
 * @author whisper
 * @since 2025/12/25
 */
interface BusinessProgressProcessor {

    companion object {

        /**
         * 空实现, 表示不处理进度事件
         */
        val NONE: BusinessProgressProcessor = object : BusinessProgressProcessor {
            override fun onBusinessStart() {
            }

            override fun onBusinessCompletion() {
            }
        }

    }

    /**
     * Flow 开始收集时调用
     */
    fun onBusinessStart()

    /**
     * Flow 收集完成时调用
     */
    fun onBusinessCompletion()

    /**
     * 给 Flow 添加该进度处理器
     *
     * @param consume 是否吞掉 `Business.Loading` 事件
     *                如果为 `true`，Loading 不会向下游传播
     *                默认值为 `true`
     * @receiver 原始 `Flow<Business<T>>`
     * @return 返回一个新的 `Flow<Business<T>>`, 包含进度处理逻辑
     */
    fun <T> Flow<Business<T>>.withBusinessProgress(consume: Boolean = true): Flow<Business<T>> =
        this@withBusinessProgress.withBusinessProgress(consume, this@BusinessProgressProcessor)

}