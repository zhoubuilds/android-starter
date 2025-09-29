package com.whisper.architecture.bean.transmit

import com.google.gson.annotations.SerializedName
import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.exception.BusinessException


/**
 * 传输层对象
 *
 * @author whisper
 * @since 2025/9/22
 */
data class ApiResponse<T>(

    @SerializedName("code")
    val code: Int?,

    @SerializedName("message")
    val message: String?,

    @SerializedName("data")
    val data: T

) {

    companion object {
        const val CODE_SUCCESS = 0
    }

    fun <R> toBusiness(transformer: (T) -> R): Business<R> = if (code == CODE_SUCCESS) {
        Business.Success(transformer(data))
    } else {
        Business.Error(
            BusinessException(
                code,
                message
            )
        )
    }

    fun toBusiness(): Business<T> = toBusiness { it }

}