package com.whisper.kit.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 验证匿名设备标识的生成、迁移兼容和进程内并发语义.
 *
 * @author whisper
 * @since 2026/09/05
 */
@RunWith(RobolectricTestRunner::class)
class DeviceIdUtilsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        resetDeviceIdState()
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }

    @Test
    fun getDeviceId_whenStoredIdExists_reusesStoredValue() {
        val storedDeviceId = "legacy-device-id"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_DEVICE_ID, storedDeviceId)
        }

        assertEquals(storedDeviceId, DeviceIdUtils.getDeviceId(context))
    }

    @Test
    fun getDeviceId_whenNoStoredId_generatesAndPersistsUuidInsteadOfAndroidId() {
        val androidId = "platform-android-id"
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, androidId)

        val deviceId: String = DeviceIdUtils.getDeviceId(context)

        UUID.fromString(deviceId)
        assertNotEquals(androidId, deviceId)
        assertEquals(
            deviceId,
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID, null),
        )
    }

    @Test
    fun getDeviceId_whenCalledConcurrently_returnsSingleProcessValue() {
        val executor = Executors.newFixedThreadPool(8)

        try {
            val deviceIds: Set<String> = executor.invokeAll(
                List(32) { Callable { DeviceIdUtils.getDeviceId(context) } },
            ).map { it.get() }.toSet()

            assertEquals(1, deviceIds.size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun getDeviceId_whenStorageRecoversConcurrently_persistsCachedValueOnce() {
        val recoveringContext = RecoveringStorageContext(context)
        val deviceId: String = DeviceIdUtils.getDeviceId(recoveringContext)

        assertNull(readStoredDeviceId())
        recoveringContext.restoreStorage()

        val executor = Executors.newFixedThreadPool(8)
        try {
            val recoveredDeviceIds: Set<String> = executor.invokeAll(
                List(32) { Callable { DeviceIdUtils.getDeviceId(recoveringContext) } },
            ).map { it.get() }.toSet()

            assertEquals(setOf(deviceId), recoveredDeviceIds)
            assertEquals(deviceId, readStoredDeviceId())
            assertEquals(1, recoveringContext.successfulAccessCount.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun getDeviceId_whenApplyRecovers_persistsSameCachedValue() {
        val recoveringContext = RecoveringApplyContext(context)
        val deviceId: String = DeviceIdUtils.getDeviceId(recoveringContext)

        assertNull(readStoredDeviceId())
        recoveringContext.restoreApply()

        assertEquals(deviceId, DeviceIdUtils.getDeviceId(recoveringContext))
        assertEquals(deviceId, readStoredDeviceId())
        assertEquals(2, recoveringContext.applyAttemptCount.get())
    }

    private fun readStoredDeviceId(): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID, null)
    }

    /**
     * 隔离单例的进程缓存, 不通过生产 API 暴露测试专用重置入口.
     */
    @Suppress("UNCHECKED_CAST")
    private fun resetDeviceIdState() {
        val stateField = DeviceIdUtils::class.java.getDeclaredField("deviceIdState").apply {
            isAccessible = true
        }
        val state: AtomicReference<Any?> =
            stateField.get(DeviceIdUtils) as AtomicReference<Any?>
        state.set(null)
    }

    /**
     * 模拟凭据存储暂时不可用并在后续调用前恢复.
     */
    private class RecoveringStorageContext(
        context: Context,
    ) : ContextWrapper(context) {

        private val isStorageAvailable: AtomicBoolean = AtomicBoolean(false)

        val successfulAccessCount: AtomicInteger = AtomicInteger(0)

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            if (!isStorageAvailable.get()) {
                throw IllegalStateException("Storage is temporarily unavailable.")
            }
            successfulAccessCount.incrementAndGet()
            return baseContext.getSharedPreferences(name, mode)
        }

        fun restoreStorage() {
            isStorageAvailable.set(true)
        }
    }

    /**
     * 模拟 SharedPreferences apply() 首次失败并在后续调用前恢复.
     */
    private class RecoveringApplyContext(
        context: Context,
    ) : ContextWrapper(context) {

        private val sharedPreferences: RecoveringApplySharedPreferences =
            RecoveringApplySharedPreferences(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )

        val applyAttemptCount: AtomicInteger
            get() = sharedPreferences.applyAttemptCount

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return sharedPreferences
        }

        fun restoreApply() {
            sharedPreferences.restoreApply()
        }
    }

    /**
     * 只拦截 Editor.apply(), 其它 SharedPreferences 行为委托给真实实现.
     */
    private class RecoveringApplySharedPreferences(
        private val delegate: SharedPreferences,
    ) : SharedPreferences by delegate {

        private val shouldFailApply: AtomicBoolean = AtomicBoolean(true)

        val applyAttemptCount: AtomicInteger = AtomicInteger(0)

        override fun edit(): SharedPreferences.Editor {
            val editor: SharedPreferences.Editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun apply() {
                    applyAttemptCount.incrementAndGet()
                    if (shouldFailApply.get()) {
                        throw IllegalStateException("Apply is temporarily unavailable.")
                    }
                    editor.apply()
                }
            }
        }

        fun restoreApply() {
            shouldFailApply.set(false)
        }
    }

    private companion object {

        const val PREFS_NAME: String = "kit_device_id"
        const val KEY_DEVICE_ID: String = "device_id"
    }
}
