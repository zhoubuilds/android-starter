package com.whisper.architecture.processor

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.extension.consumeLoading
import com.whisper.architecture.extension.consumeLoadingCycles
import com.whisper.architecture.extension.withBusinessProgress
import com.whisper.architecture.extension.withBusinessProgressCycles
import kotlinx.coroutines.flow.Flow

/**
 * 处理业务状态的加载进度.
 *
 * Processor 表示业务状态处理协议, 用于避免和 Android Handler 语义混淆.
 * 常用单轮入口将一次 Flow 收集视为一轮业务进度; 多轮入口根据每组 [Business.Loading] 和 Outcome 驱动进度.
 * 单轮或当前多轮状态发生异常、取消或 Flow 结束时同样会结束进度. 开始回调失败后仍会尝试配对完成;
 * 完成回调不会覆盖正在传播的管线异常, 其异常会作为 suppressed exception 保留.
 * 回调实现仍应保持快速、同步且不抛异常.
 * 同一个处理器被多条 Flow 并发使用时, 实现还必须保证线程安全. 该处理器不负责调度业务流程, 也不解释业务数据.
 *
 * @aegis 保护收集进度处理协议, 默认实现和开始/结束回调语义.
 * @aegis-audit 2026-08-26 | whisper | 将进度回调调整为由 Loading 和 Outcome 状态驱动.
 * @aegis-audit 2026-08-26 | whisper | 明确进度回调失败时的异常保留与配对语义.
 * @aegis-audit 2026-08-27 | whisper | 授权拆分单轮收集与多轮状态进度处理入口.
 *
 * @author whisper
 * @since 2026/07/24
 */
interface BusinessProgressProcessor {

    companion object {

        /**
         * 静默丢弃进度的实现.
         */
        val NONE: BusinessProgressProcessor = object : BusinessProgressProcessor {
            override fun onBusinessStart() = Unit

            override fun onBusinessCompletion() = Unit
        }
    }

    /**
     * 在一轮业务进度开始时处理进度.
     */
    fun onBusinessStart()

    /**
     * 在当前业务进度结束时处理进度.
     *
     * 单轮收集结束或多轮状态产生 Outcome 时结束进度; 异常、取消和 Flow 结束也会结束已经开始的进度.
     */
    fun onBusinessCompletion()

    /**
     * 将一次业务 Flow 收集作为单轮操作添加进度处理器.
     *
     * @receiver 原始业务 Flow.
     * @return 添加状态进度处理后的原类型 Flow.
     */
    fun <B : Business<*, *>> Flow<B>.withBusinessProgress(): Flow<B> =
        this@withBusinessProgress.withBusinessProgress(this@BusinessProgressProcessor)

    /**
     * 为业务 Flow 中的多轮 Loading / Outcome 状态添加进度处理器.
     *
     * @receiver 可能包含多轮状态的业务 Flow.
     * @return 添加多轮状态进度处理后的原类型 Flow.
     */
    fun <B : Business<*, *>> Flow<B>.withBusinessProgressCycles(): Flow<B> =
        this@withBusinessProgressCycles.withBusinessProgressCycles(this@BusinessProgressProcessor)

    /**
     * 使用当前处理器处理单轮收集进度并消费加载中状态.
     *
     * @receiver 包含加载中状态的业务 Flow.
     * @return 只可能发送成功或失败的 Flow.
     */
    fun <M, D> Flow<Business<M, D>>.consumeLoading(): Flow<Business.Outcome<M, D>> =
        this@consumeLoading.consumeLoading(this@BusinessProgressProcessor)

    /**
     * 使用当前处理器处理多轮状态进度并消费每轮加载中状态.
     *
     * @receiver 可能包含多轮加载状态的业务 Flow.
     * @return 只可能发送成功或失败的 Flow.
     */
    fun <M, D> Flow<Business<M, D>>.consumeLoadingCycles(): Flow<Business.Outcome<M, D>> =
        this@consumeLoadingCycles.consumeLoadingCycles(this@BusinessProgressProcessor)
}
