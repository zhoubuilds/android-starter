package com.whisper.foundation.model.business

/**
 * 表示当前应用统一的业务元信息.
 *
 * 该类型承载接口响应中的公共业务字段, 架构层只透传该数据, 不解释字段含义.
 *
 * @property code 业务响应码.
 * @property message 业务响应信息.
 *
 * @author whisper
 * @since 2026/07/27
 */
data class BusinessMetadata(
    val code: Int?,
    val message: String?,
) {

    companion object {

        /** 表示请求未产生可解析的业务响应元信息. */
        val EMPTY: BusinessMetadata = BusinessMetadata(
            code = null,
            message = null,
        )
    }
}
