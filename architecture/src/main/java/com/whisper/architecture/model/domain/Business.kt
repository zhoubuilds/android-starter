package com.whisper.architecture.model.domain

/**
 * 表示业务数据在一次加载过程中的状态.
 *
 * 架构层只根据 [Loading]、[Success] 和 [Failure] 分流, 不解释 [M] 或 [D] 的内容.
 * [M] 承载主要载荷之外的完整元信息, [D] 承载主要业务数据. 成功和失败状态都保留两类数据.
 *
 * @param M 元信息类型.
 * @param D 主要载荷类型.
 * @author whisper
 * @since 2026/8/25
 */
sealed interface Business<out M, out D> {

    /** 表示一次加载已经结束并产生成功或失败结果. */
    sealed interface Outcome<out M, out D> :
        Business<M, D>

    /** 表示业务数据正在加载. */
    object Loading : Business<Nothing, Nothing>

    /** 表示加载成功, 并完整保留元信息和主要载荷. */
    data class Success<out M, out D>(
        val meta: M,
        val data: D,
    ) : Outcome<M, D>

    /** 表示加载失败, 并完整保留异常、元信息和失败响应中的主要载荷. */
    data class Failure<out M, out D>(
        val exception: Exception,
        val meta: M,
        val data: D,
    ) : Outcome<M, D>
}
