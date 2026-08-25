package com.whisper.architecture.business.model

/**
 * 表示架构层业务执行状态.
 *
 * 通过类型层级描述业务链路中仍可能出现的状态: [ArchitectureBusiness] 包含加载中, 成功和错误;
 * [Outcome] 不包含加载中. 架构层只承载业务数据和业务元信息, 不假设元信息字段结构.
 * 该状态描述单个业务链路, 不表示页面全局待处理任务数量.
 *
 * @aegis 保护状态层级, 泛型, 工厂方法和各状态承载数据的公开契约.
 * @author whisper
 * @since 2026/07/27
 */
sealed class ArchitectureBusiness<out T, out M> {

    companion object {

        /**
         * 创建业务成功状态.
         *
         * @param data 业务结果数据.
         * @param metadata 业务元信息.
         * @return 业务成功状态.
         */
        fun <T, M> success(
            data: T,
            metadata: M? = null,
        ): Success<T, M> = Success(
            data = data,
            metadata = metadata,
        )

        /**
         * 创建业务错误状态.
         *
         * @param exception 业务异常.
         * @param data 失败时附带的业务数据.
         * @param metadata 业务元信息.
         * @return 业务错误状态.
         */
        fun <T, M> error(
            exception: Exception,
            data: T? = null,
            metadata: M? = null,
        ): Error<T, M> = Error(
            exception = exception,
            data = data,
            metadata = metadata,
        )

        /**
         * 创建业务加载中状态.
         *
         * @return 业务加载中状态.
         */
        fun <T, M> loading(): ArchitectureBusiness<T, M> = Loading
    }

    /**
     * 表示不包含加载中状态的业务结果.
     *
     * 结果可能是 [Success] 或 [Error]. 该类型只约束可能出现的状态,
     * 不表示承载结果的链路已经完成.
     */
    sealed class Outcome<out T, out M> : ArchitectureBusiness<T, M>()

    /**
     * 表示业务执行成功并携带结果数据.
     *
     * @property data 业务结果数据.
     * @property metadata 业务元信息.
     */
    data class Success<T, M>(
        val data: T,
        val metadata: M? = null,
    ) : Outcome<T, M>()

    /**
     * 表示业务执行失败并携带供统一处理的异常.
     *
     * 该类型是业务链路中传递的状态, 不表示 Flow 抛出的异常.
     *
     * @property exception 业务异常.
     * @property data 失败时附带的业务数据.
     * @property metadata 业务元信息.
     */
    data class Error<T, M>(
        val exception: Exception,
        val data: T? = null,
        val metadata: M? = null,
    ) : Outcome<T, M>()

    /**
     * 表示业务正在执行.
     *
     * 该状态只属于当前业务 Flow, 页面级加载展示由 Architecture UI 状态统一聚合.
     */
    data object Loading : ArchitectureBusiness<Nothing, Nothing>()
}
