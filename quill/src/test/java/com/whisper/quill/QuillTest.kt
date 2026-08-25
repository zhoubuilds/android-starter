package com.whisper.quill

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillTest {

    @After
    fun tearDown() {
        Quill.clearWriters()
    }

    @Test
    fun messageSupplierIsNotExecutedWhenNoWriterAdded() {
        var executed: Boolean = false

        val result: Int = Quill.d {
            executed = true
            "unused"
        }

        assertEquals(0, result)
        assertFalse(executed)
    }

    @Test
    fun writerCanBeAddedAndRemoved() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.DEBUG)

        assertTrue(Quill.addWriter(writer))
        assertEquals(1, Quill.writerCount)
        assertTrue(Quill.removeWriter(writer))
        assertEquals(0, Quill.writerCount)
    }

    @Test
    fun duplicateWriterIsIgnored() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.DEBUG)
        Quill.addWriter(writer)

        assertFalse(Quill.addWriter(writer))
        assertEquals(1, Quill.writerCount)
    }

    @Test
    fun missingWriterRemovalIsIgnored() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.DEBUG)

        assertFalse(Quill.removeWriter(writer))
        assertEquals(0, Quill.writerCount)
    }

    @Test
    fun messageSupplierIsNotExecutedWhenWriterRejectsLevel() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.WARN)
        var executed: Boolean = false
        Quill.addWriter(writer)

        val result: Int = Quill.d {
            executed = true
            "unused"
        }

        assertEquals(0, result)
        assertFalse(executed)
        assertTrue(writer.records.isEmpty())
    }

    @Test
    fun messageSupplierIsNotExecutedWhenWriterRejectsLoggable() {
        val writer: RecordingWriter = RecordingWriter(
            minimumLevel = QuillLevel.VERBOSE,
            loggable = false,
        )
        var executed: Boolean = false
        Quill.addWriter(writer)

        val result: Int = Quill.e("Unit") {
            executed = true
            "unused"
        }

        assertEquals(0, result)
        assertFalse(executed)
        assertTrue(writer.records.isEmpty())
    }

    @Test
    fun messageSupplierIsExecutedWhenWriterAcceptsLevel() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.DEBUG)
        Quill.addWriter(writer)

        val result: Int = Quill.d("Unit") { "accepted" }

        assertEquals(1, result)
        assertEquals(1, writer.records.size)
        assertEquals(QuillLevel.DEBUG, writer.records[0].level)
        assertEquals("Unit", writer.records[0].tag)
        assertEquals("accepted", writer.records[0].message)
    }

    @Test
    fun publishReturnsFirstPositiveValueWithoutAddingWriterResults() {
        val firstWriter: RecordingWriter = RecordingWriter(QuillLevel.DEBUG, writeResult = 2)
        val secondWriter: RecordingWriter = RecordingWriter(QuillLevel.DEBUG, writeResult = 3)
        Quill.addWriter(firstWriter)
        Quill.addWriter(secondWriter)

        val result: Int = Quill.d("Unit") { "accepted" }

        assertEquals(2, result)
        assertEquals(1, firstWriter.records.size)
        assertEquals(1, secondWriter.records.size)
    }

    @Test
    fun publishReturnsLaterPositiveValueWhenEarlierWriterDoesNotWrite() {
        val firstWriter: RecordingWriter = RecordingWriter(QuillLevel.DEBUG, writeResult = 0)
        val secondWriter: RecordingWriter = RecordingWriter(QuillLevel.DEBUG, writeResult = 4)
        Quill.addWriter(firstWriter)
        Quill.addWriter(secondWriter)

        val result: Int = Quill.d("Unit") { "accepted" }

        assertEquals(4, result)
        assertEquals(1, firstWriter.records.size)
        assertEquals(1, secondWriter.records.size)
    }

    @Test
    fun infoOverloadAcceptsTagThrowableAndLazyMessage() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.INFO)
        val throwable: IllegalStateException = IllegalStateException("network")
        Quill.addWriter(writer)

        val result: Int = Quill.i("InfoTag", throwable) { "info failed" }

        assertEquals(1, result)
        assertEquals(QuillLevel.INFO, writer.records[0].level)
        assertEquals("InfoTag", writer.records[0].tag)
        assertEquals(throwable, writer.records[0].throwable)
        assertEquals("info failed", writer.records[0].message)
    }

    @Test
    fun warnOverloadAcceptsTagAndThrowableWithoutMessage() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.WARN)
        val throwable: IllegalStateException = IllegalStateException("warning")
        Quill.addWriter(writer)

        val result: Int = Quill.w("WarnTag", throwable)

        assertEquals(1, result)
        assertEquals(QuillLevel.WARN, writer.records[0].level)
        assertEquals(throwable, writer.records[0].throwable)
        assertEquals("", writer.records[0].message)
    }

    @Test
    fun throwableOverloadKeepsCause() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.ERROR)
        val throwable: IllegalArgumentException = IllegalArgumentException("bad input")
        Quill.addWriter(writer)

        val result: Int = Quill.e(throwable) { "failed" }

        assertEquals(1, result)
        assertEquals(throwable, writer.records[0].throwable)
        assertEquals("failed", writer.records[0].message)
    }

    @Test
    fun messageSupplierExceptionIsConvertedToLogRecord() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.DEBUG)
        Quill.addWriter(writer)

        val result: Int = Quill.d { throw IllegalStateException("boom") }

        assertEquals(1, result)
        assertEquals("Quill message supplier failed.", writer.records[0].message)
        assertTrue(writer.records[0].throwable is IllegalStateException)
    }

    @Test
    fun messageSupplierErrorIsConvertedToLogRecord() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.DEBUG)
        Quill.addWriter(writer)

        val result: Int = Quill.d { throw AssertionError("boom") }

        assertEquals(1, result)
        assertEquals("Quill message supplier failed.", writer.records[0].message)
        assertTrue(writer.records[0].throwable is AssertionError)
    }

    @Test
    fun messageSupplierExceptionKeepsOriginalThrowableAsSuppressed() {
        val writer: RecordingWriter = RecordingWriter(QuillLevel.ERROR)
        val originalThrowable: IllegalArgumentException = IllegalArgumentException("original")
        Quill.addWriter(writer)

        val result: Int = Quill.e("Unit", originalThrowable) {
            throw IllegalStateException("supplier")
        }

        assertEquals(1, result)
        val supplierException: Throwable? = writer.records[0].throwable
        assertTrue(supplierException is IllegalStateException)
        assertEquals(1, supplierException!!.suppressed.size)
        assertSame(originalThrowable, supplierException.suppressed[0])
        assertEquals(
            "Quill message supplier failed. Original throwable is attached as suppressed.",
            writer.records[0].message,
        )
    }

    @Test
    fun writerLoggableErrorIsIgnored() {
        val writer: RecordingWriter = RecordingWriter(
            minimumLevel = QuillLevel.DEBUG,
            loggableError = AssertionError("loggable failed"),
        )
        var executed: Boolean = false
        Quill.addWriter(writer)

        val result: Int = Quill.d {
            executed = true
            "unused"
        }

        assertEquals(0, result)
        assertFalse(executed)
        assertTrue(writer.records.isEmpty())
    }

    @Test
    fun writerWriteErrorIsIgnored() {
        val failedWriter: RecordingWriter = RecordingWriter(
            minimumLevel = QuillLevel.DEBUG,
            writeError = AssertionError("write failed"),
        )
        val successWriter: RecordingWriter = RecordingWriter(
            minimumLevel = QuillLevel.DEBUG,
            writeResult = 5,
        )
        Quill.addWriter(failedWriter)
        Quill.addWriter(successWriter)

        val result: Int = Quill.d("Unit") { "message" }

        assertEquals(5, result)
        assertTrue(failedWriter.records.isEmpty())
        assertEquals(1, successWriter.records.size)
    }

    private class RecordingWriter(
        private val minimumLevel: QuillLevel,
        private val writeResult: Int = 1,
        private val loggable: Boolean = true,
        private val loggableError: Throwable? = null,
        private val writeError: Throwable? = null,
    ) : QuillWriter {

        val records: MutableList<Record> = ArrayList()

        override fun isLoggable(level: QuillLevel, tag: String?): Boolean {
            if (loggableError != null) {
                throw loggableError
            }
            return loggable && level.priority >= minimumLevel.priority
        }

        override fun write(
            level: QuillLevel,
            tag: String?,
            throwable: Throwable?,
            message: String,
        ): Int {
            if (writeError != null) {
                throw writeError
            }
            records.add(Record(level, tag, throwable, message))
            return writeResult
        }
    }

    private data class Record(
        val level: QuillLevel,
        val tag: String?,
        val throwable: Throwable?,
        val message: String,
    )
}
