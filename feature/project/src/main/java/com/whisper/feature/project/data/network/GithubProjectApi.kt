package com.whisper.feature.project.data.network

import com.whisper.architecture.network.annotation.ApplicationInterceptors
import retrofit2.http.GET

/**
 * 读取无需登录的 GitHub 公开仓库信息.
 *
 * 接口级拦截器依次完成 Endpoint 路由和 GitHub 共用 Header 注入, 方法仅声明相对路径.
 * 保持 GitHub 原生响应结构, 由 Repository 映射为业务管线结果.
 */
@ApplicationInterceptors(
    GithubEndpointRoutingInterceptor::class,
    GithubSystemParametersInterceptor::class,
)
internal interface GithubProjectApi {

    @GET("repos/zhoubuilds/android-starter")
    suspend fun getProject(): GithubProjectResp
}
