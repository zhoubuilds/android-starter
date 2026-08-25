package com.whisper.aster.runtime

import android.app.Application

/**
 * Aster 能力体系的基础契约.
 *
 * 业务能力接口可以继承该接口, 能力实现由 KSP 自动注册并在首次获取时初始化.
 * 能力的构造函数和 [initialize] 只能初始化自身状态, 不得直接或间接解析其他能力.
 *
 * @aegis 保护能力接口和同步, 自包含, 禁止递归解析的初始化约束.
 * @author whisper
 * @since 2026/07/21
 */
interface Capability {

    /**
     * 初始化当前能力实例.
     *
     * 该方法必须同步完成当前实例自身的初始化, 不得解析其他能力, 也不得等待会解析能力的异步任务.
     *
     * @param application 当前进程的 Application.
     */
    fun initialize(application: Application)
}
