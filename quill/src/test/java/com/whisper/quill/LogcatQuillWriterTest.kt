package com.whisper.quill

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogcatQuillWriterTest {

    @After
    fun tearDown() {
        ShadowLog.clear()
    }

    @Test
    fun isLoggableRejectsLowerThanMinimumLevel() {
        val tag: String = "MinimumTag"
        val writer: LogcatQuillWriter = LogcatQuillWriter(minimumLevel = QuillLevel.WARN)
        ShadowLog.setLoggable(tag, Log.VERBOSE)

        assertFalse(writer.isLoggable(QuillLevel.DEBUG, tag))
        assertTrue(writer.isLoggable(QuillLevel.WARN, tag))
    }

    @Test
    fun isLoggableRespectsAndroidLoggableByDefault() {
        val tag: String = "LoggableTag"
        val writer: LogcatQuillWriter = LogcatQuillWriter()
        ShadowLog.setLoggable(tag, Log.INFO)

        assertFalse(writer.isLoggable(QuillLevel.DEBUG, tag))
        assertTrue(writer.isLoggable(QuillLevel.INFO, tag))
    }

    @Test
    fun writeSplitsLongMessage() {
        val tag: String = "LongMessageTag"
        val writer: LogcatQuillWriter = LogcatQuillWriter()

        writer.write(QuillLevel.DEBUG, tag, null, "a".repeat(4001))

        val logs: List<ShadowLog.LogItem> = ShadowLog.getLogsForTag(tag)
        assertEquals(2, logs.size)
        assertEquals(4000, logs[0].msg.length)
        assertEquals(1, logs[1].msg.length)
    }

    @Test
    fun writeAppendsThrowableStackTrace() {
        val tag: String = "ThrowableTag"
        val writer: LogcatQuillWriter = LogcatQuillWriter()
        val throwable: IllegalStateException = IllegalStateException("bad state")

        writer.write(QuillLevel.ERROR, tag, throwable, "failed")

        val logs: List<ShadowLog.LogItem> = ShadowLog.getLogsForTag(tag)
        assertEquals(1, logs.size)
        assertTrue(logs[0].msg.contains("failed"))
        assertTrue(logs[0].msg.contains("IllegalStateException"))
        assertTrue(logs[0].msg.contains("bad state"))
    }

    @Test
    fun writePrintsEmptyMessage() {
        val tag: String = "EmptyMessageTag"
        val writer: LogcatQuillWriter = LogcatQuillWriter()

        writer.write(QuillLevel.INFO, tag, null, "")

        val logs: List<ShadowLog.LogItem> = ShadowLog.getLogsForTag(tag)
        assertEquals(1, logs.size)
        assertEquals("", logs[0].msg)
    }
}
