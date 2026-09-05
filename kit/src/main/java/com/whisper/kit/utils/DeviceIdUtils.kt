package com.whisper.kit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 提供无需额外权限的应用侧匿名设备标识.
 *
 * 标识首次使用时随机生成并保存在应用私有 SharedPreferences 中,
 * 适合作为验证码请求控频等场景的弱信号. 它不绑定设备或硬件,
 * 也不承诺在清除数据、卸载、备份恢复、存储异常或多进程竞争后保持不变.
 * 调用方不得将其作为身份认证、安全凭据或服务端控频的唯一依据.
 *
 * @aegis 保护公开 API、匿名标识生成与存储兼容语义、进程内并发一致性及安全使用边界.
 *
 * @author whisper
 * @since 2025/09/10
 */
object DeviceIdUtils {

    private const val PREFS_NAME = "kit_device_id"

    private const val KEY_DEVICE_ID = "device_id"

    private val deviceIdState: AtomicReference<DeviceIdState?> = AtomicReference(null)

    /**
     * 返回当前应用环境中尽量稳定的匿名标识.
     *
     * 同一进程内的并发调用返回相同值. 已由旧版本写入的非空标识会继续复用,
     * 避免升级后主动更换.
     *
     * 首次调用会同步等待小型 SharedPreferences 文件完成加载, 可以直接从主线程调用.
     * 如果希望减少首次业务请求的等待, 可以在 Application 初始化阶段通过应用级
     * CoroutineScope 在 Dispatchers.IO 上调用一次进行预热, 不应为预热创建无生命周期的
     * GlobalScope. 预热与其它线程同时调用是安全的, 同一应用进程内始终返回同一个缓存值.
     */
    fun getDeviceId(context: Context): String {
        val applicationContext: Context = context.applicationContext ?: context
        val cachedState: DeviceIdState? = deviceIdState.get()
        if (cachedState != null) {
            persistIfNeeded(cachedState, applicationContext)
            return cachedState.value
        }

        val sharedPreferences: SharedPreferences? = getSharedPreferences(applicationContext)
        val storedDeviceId: String? = getStoredDeviceId(sharedPreferences)
        val candidateDeviceId: String = storedDeviceId ?: UUID.randomUUID().toString()

        val candidateState: DeviceIdState = DeviceIdState(
            value = candidateDeviceId,
            isPersistenceScheduled = storedDeviceId != null,
        )
        while (true) {
            val currentState: DeviceIdState? = deviceIdState.get()
            if (currentState != null) {
                persistIfNeeded(currentState, applicationContext)
                return currentState.value
            }
            if (deviceIdState.compareAndSet(null, candidateState)) {
                persistIfNeeded(candidateState, sharedPreferences)
                return candidateDeviceId
            }
        }
    }

    private fun getSharedPreferences(context: Context): SharedPreferences? = try {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Exception) {
        null
    }

    private fun getStoredDeviceId(sharedPreferences: SharedPreferences?): String? = try {
        sharedPreferences?.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun persistDeviceId(context: Context, deviceId: String): Boolean {
        return persistDeviceId(getSharedPreferences(context), deviceId)
    }

    private fun persistIfNeeded(state: DeviceIdState, context: Context) {
        if (state.needsPersistence) {
            state.trySchedulePersistence {
                persistDeviceId(context, state.value)
            }
        }
    }

    private fun persistIfNeeded(
        state: DeviceIdState,
        sharedPreferences: SharedPreferences?,
    ) {
        if (state.needsPersistence) {
            state.trySchedulePersistence {
                persistDeviceId(sharedPreferences, state.value)
            }
        }
    }

    private fun persistDeviceId(
        sharedPreferences: SharedPreferences?,
        deviceId: String,
    ): Boolean {
        if (sharedPreferences == null) {
            return false
        }
        return try {
            sharedPreferences.edit { putString(KEY_DEVICE_ID, deviceId) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 绑定进程内唯一标识与持久化状态, 并原子协调失败后的补写.
     *
     * apply() 正常返回只表示异步写入已经调度, 不代表磁盘写入已经完成.
     */
    private class DeviceIdState(
        val value: String,
        isPersistenceScheduled: Boolean,
    ) {

        private val persistenceScheduled: AtomicBoolean =
            AtomicBoolean(isPersistenceScheduled)

        val needsPersistence: Boolean
            get() = !persistenceScheduled.get()

        fun trySchedulePersistence(persist: () -> Boolean) {
            if (!persistenceScheduled.compareAndSet(false, true)) {
                return
            }
            val wasScheduled: Boolean = try {
                persist()
            } catch (_: Exception) {
                false
            }
            if (!wasScheduled) {
                persistenceScheduled.set(false)
            }
        }
    }
}
