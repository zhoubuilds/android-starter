package com.whisper.foundation.model.transmit

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.exception.BusinessException
import com.whisper.foundation.model.business.BusinessMetadata
import com.google.gson.annotations.SerializedName

/**
 * 表示服务端 API 响应.
 *
 * @property code 业务响应码.
 * @property message 业务响应信息.
 * @property data 可空响应数据.
 *
 * @author whisper
 * @since 2026/07/24
 */
data class ApiResponse<T>(

    @SerializedName("code")
    val code: Int?,

    @SerializedName("msg")
    val message: String?,

    @SerializedName("data")
    val data: T?

) {

    companion object {

        /**
         * 表示业务请求成功的响应码.
         */
        const val CODE_SUCCESS: Int = 0
    }

    /**
     * 将 API 响应转换为业务结果.
     *
     * @param transformer 响应数据转换函数.
     * @return 只包含成功或失败的业务结果.
     */
    fun <R> toBusiness(transformer: (T?) -> R): Business.Outcome<BusinessMetadata, R> {
        val metadata: BusinessMetadata = BusinessMetadata(
            code = code,
            message = message,
        )
        val resultData: R = transformer(data)
        return if (code == CODE_SUCCESS) {
            Business.Success(
                meta = metadata,
                data = resultData,
            )
        } else {
            Business.Failure(
                exception = BusinessException(message),
                meta = metadata,
                data = resultData,
            )
        }
    }

    /**
     * 将 API 响应转换为原始数据类型的业务结果.
     *
     * @return 只包含成功或失败的业务结果.
     */
    fun toBusiness(): Business.Outcome<BusinessMetadata, T?> = toBusiness { data: T? -> data }

}
