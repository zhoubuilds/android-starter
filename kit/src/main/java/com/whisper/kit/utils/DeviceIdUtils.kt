package com.whisper.kit.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import androidx.core.content.edit


/**
 *
 * @author whisper
 * @since 2025/9/10
 */
object DeviceIdUtils {

    private const val PREFS_NAME = "kit_device_id"

    private const val KEY_DEVICE_ID = "device_id"

    @Volatile
    private var _deviceId: String? = null

    private val _lock: ReentrantReadWriteLock = ReentrantReadWriteLock()

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        _lock.read { _deviceId?.takeIf { it.isNotBlank() } }?.let { return it }
        return _lock.write {
            _deviceId?.takeIf { it.isNotBlank() }?.let { return@write it }

            val sp: SharedPreferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val deviceId: String? = sp.getString(KEY_DEVICE_ID, null)
            deviceId?.takeIf { it.isNotBlank() }?.also { _deviceId = it }?.let { return it }

            val createdDeviceId: String = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Exception) {
                null
            }?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            createdDeviceId.also {
                sp.edit { putString(KEY_DEVICE_ID, createdDeviceId) }
                _deviceId = it
            }
        }
    }

}