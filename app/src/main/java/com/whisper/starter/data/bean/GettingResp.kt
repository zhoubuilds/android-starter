package com.whisper.starter.data.bean

import com.google.gson.annotations.SerializedName


/**
 *
 * @author whisper
 * @since 2025/9/22
 */
data class GettingResp(

    @SerializedName("id")
    val id: Long?,

    @SerializedName("description")
    val description: String?,

    )