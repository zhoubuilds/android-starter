package com.whisper.architecture.extension

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.processor.BusinessErrorProcessor
import com.whisper.architecture.processor.BusinessMetaProcessor
import com.whisper.architecture.processor.BusinessProgressProcessor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证业务状态 Flow 的分流、处理、恢复与脱壳契约. */
class BusinessFlowExtensionsTest {

    @Test
    fun consumeLoadingAndRecoverError_preservesMetaAndUsesFailureData() = runBlocking {
        val progressEvents: MutableList<String> = mutableListOf()
        val handledFailures: MutableList<Business.Failure<String, *>> = mutableListOf()
        val progressProcessor: BusinessProgressProcessor = recordingProgressProcessor(progressEvents)
        val errorProcessor: BusinessErrorProcessor<String> = recordingErrorProcessor(handledFailures)
        val failure: Business.Failure<String, Int> = Business.Failure(
            exception = IllegalStateException("failed"),
            meta = "failure-meta",
            data = 7,
        )
        val source: Flow<Business<String, Int>> = flowOf(
            Business.Loading,
            Business.Success(meta = "success-meta", data = 1),
            failure,
        )

        val outcomes: Flow<Business.Outcome<String, Int>> = with(progressProcessor) {
            source.consumeLoading()
        }
        val values: List<Business.Success<String, Int>> = with(errorProcessor) {
            outcomes.recoverError { error: Business.Failure<String, Int> -> error.data + 1 }
        }.toList()

        assertEquals(listOf("start", "completion"), progressEvents)
        assertEquals(listOf(failure), handledFailures)
        assertEquals(
            listOf(
                Business.Success(meta = "success-meta", data = 1),
                Business.Success(meta = "failure-meta", data = 8),
            ),
            values,
        )
    }

    @Test
    fun withBusinessError_observesFailureAndKeepsOriginalFlow() = runBlocking {
        val failure: Business.Failure<String, Int> = Business.Failure(
            exception = IllegalArgumentException("failed"),
            meta = "meta",
            data = 3,
        )
        val handledFailures: MutableList<Business.Failure<String, *>> = mutableListOf()
        val processor: BusinessErrorProcessor<String> = recordingErrorProcessor(handledFailures)
        val source: Flow<Business.Outcome<String, Int>> = flowOf(failure)

        val values: List<Business.Outcome<String, Int>> = with(processor) {
            source.withBusinessError()
        }.toList()

        assertEquals(listOf(failure), handledFailures)
        assertEquals(listOf(failure), values)
    }

    @Test
    fun consumeError_dropsFailureAndReturnsSuccessOnly() = runBlocking {
        val failure: Business.Failure<Unit, Int> = Business.Failure(
            exception = IllegalStateException("failed"),
            meta = Unit,
            data = -1,
        )
        val handledFailures: MutableList<Business.Failure<Unit, *>> = mutableListOf()
        val processor: BusinessErrorProcessor<Unit> = recordingErrorProcessor(handledFailures)
        val source: Flow<Business.Outcome<Unit, Int>> = flowOf(
            Business.Success(meta = Unit, data = 1),
            failure,
        )

        val values: List<Business.Success<Unit, Int>> = with(processor) {
            source.consumeError()
        }.toList()

        assertEquals(listOf(failure), handledFailures)
        assertEquals(listOf(Business.Success(meta = Unit, data = 1)), values)
    }

    @Test
    fun consumeSuccessMeta_handlesMetaBeforeEmittingData() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessMetaProcessor<String> = object : BusinessMetaProcessor<String> {
            override fun onBusinessMeta(metadata: String) {
                events += "meta:$metadata"
            }
        }
        val source: Flow<Business.Success<String, Int>> = flowOf(
            Business.Success(meta = "saved", data = 2),
        )

        with(processor) {
            source.consumeSuccessMeta()
        }.collect { data: Int -> events += "data:$data" }

        assertEquals(listOf("meta:saved", "data:2"), events)
    }

    @Test
    fun consumeSuccessMeta_withoutProcessor_returnsData() = runBlocking {
        val source: Flow<Business.Success<String, Int>> = flowOf(
            Business.Success(meta = "meta", data = 4),
        )

        assertEquals(listOf(4), source.consumeSuccessMeta().toList())
    }

    @Test
    fun dataOrNull_handlesFailureAndDoesNotExposeItsData() = runBlocking {
        val failure: Business.Failure<Unit, Int> = Business.Failure(
            exception = IllegalStateException("failed"),
            meta = Unit,
            data = 99,
        )
        val handledFailures: MutableList<Business.Failure<Unit, *>> = mutableListOf()
        val processor: BusinessErrorProcessor<Unit> = recordingErrorProcessor(handledFailures)
        val source: Flow<Business.Outcome<Unit, Int>> = flowOf(
            Business.Success(meta = Unit, data = 2),
            failure,
        )

        val values: List<Int?> = with(processor) { source.dataOrNull() }.toList()

        assertEquals(listOf(2, null), values)
        assertEquals(listOf(failure), handledFailures)
    }

    @Test
    fun consumeLoading_loadingOnlyCompletesProgressWithoutOutcome() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessProgressProcessor = recordingProgressProcessor(events)
        val source: Flow<Business<Unit, Int>> = flowOf(Business.Loading)

        val values: List<Business.Outcome<Unit, Int>> = with(processor) {
            source.consumeLoading()
        }.toList()

        assertTrue(values.isEmpty())
        assertEquals(listOf("start", "completion"), events)
    }

    @Test
    fun withBusinessProgress_cancellationCompletesAndRethrows() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessProgressProcessor = recordingProgressProcessor(events)
        val source: Flow<Business<Unit, Int>> = flow {
            emit(Business.Loading)
            awaitCancellation()
        }

        val job = launch {
            with(processor) { source.withBusinessProgress() }.collect { }
        }
        while (events.isEmpty()) {
            yield()
        }
        job.cancelAndJoin()

        assertEquals(listOf("start", "completion"), events)
        assertTrue(job.isCancelled)
    }

    @Test
    fun withLoading_prependsTheSingletonLoading() = runBlocking {
        val outcome: Business.Success<Unit, Int> = Business.Success(meta = Unit, data = 3)
        val source: Flow<Business.Outcome<Unit, Int>> = flowOf(outcome)

        val values: List<Business<Unit, Int>> = source.withLoading().toList()

        assertSame(Business.Loading, values[0])
        assertEquals(outcome, values[1])
    }

    @Test
    fun withLoading_emptyFlowEmitsOnlyTheSingletonLoading() = runBlocking {
        val source: Flow<Business.Outcome<Unit, Int>> = emptyFlow()

        val values: List<Business<Unit, Int>> = source.withLoading().toList()

        assertEquals(1, values.size)
        assertSame(Business.Loading, values.single())
    }

    private fun recordingProgressProcessor(events: MutableList<String>): BusinessProgressProcessor =
        object : BusinessProgressProcessor {
            override fun onBusinessStart() {
                events += "start"
            }

            override fun onBusinessCompletion() {
                events += "completion"
            }
        }

    private fun <M> recordingErrorProcessor(
        failures: MutableList<Business.Failure<M, *>>,
    ): BusinessErrorProcessor<M> = object : BusinessErrorProcessor<M> {
        override fun onBusinessError(error: Business.Failure<M, *>) {
            failures += error
        }
    }
}
