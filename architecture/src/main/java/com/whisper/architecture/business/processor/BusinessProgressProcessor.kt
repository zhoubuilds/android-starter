package com.whisper.architecture.business.processor

import com.whisper.architecture.business.function.consumeLoading
import com.whisper.architecture.business.function.withBusinessProgress
import com.whisper.architecture.business.model.ArchitectureBusiness
import kotlinx.coroutines.flow.Flow

/**
 * 处理业务 Flow 的收集进度.
 *
 * Processor 表示业务状态处理协议, 用于避免和 Android Handler 语义混淆.
 * 处理时机由 Flow 的开始收集和结束收集决定, 不依赖 [ArchitectureBusiness.Loading] 状态.
 * 该处理器不负责调度业务流程.
 *
 * @aegis 保护收集进度处理协议, 默认实现和开始/结束回调语义.
 * @author whisper
 * @since 2026/07/24
 */
interface BusinessProgressProcessor {

    companion object {

        /**
         * 不处理业务进度的默认实现.
         */
        val NONE: BusinessProgressProcessor = object : BusinessProgressProcessor {
            override fun onBusinessStart() = Unit

            override fun onBusinessCompletion() = Unit
        }
    }

    /**
     * 在业务 Flow 开始收集时处理进度.
     */
    fun onBusinessStart()

    /**
     * 在业务 Flow 结束收集时处理进度.
     *
     * 正常完成, 异常和取消都会结束本次收集.
     */
    fun onBusinessCompletion()

    /**
     * 为业务 Flow 添加当前收集进度处理器.
     *
     * @receiver 原始业务 Flow.
     * @return 添加进度处理后的原类型 Flow.
     */
    fun <B : ArchitectureBusiness<*, *>> Flow<B>.withBusinessProgress(): Flow<B> =
        this@withBusinessProgress.withBusinessProgress(this@BusinessProgressProcessor)

    /**
     * 使用当前处理器处理收集进度并消费加载中状态.
     *
     * @receiver 包含加载中状态的业务 Flow.
     * @return 只可能发送成功或错误的 Flow.
     */
    fun <T, M> Flow<ArchitectureBusiness<T, M>>.consumeLoading(): Flow<ArchitectureBusiness.Outcome<T, M>> =
        this@consumeLoading.consumeLoading(this@BusinessProgressProcessor)
}
