package com.whisper.architecture.extension

import com.whisper.architecture.model.domain.Business
import com.whisper.architecture.processor.BusinessErrorProcessor
import com.whisper.architecture.processor.BusinessMetaProcessor
import com.whisper.architecture.processor.BusinessProgressProcessor
import java.util.concurrent.CancellationException
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

        val outcomes: Flow<Business.Outcome<String, Int>> =
            source.consumeLoading(processor = progressProcessor)
        val values: List<Business.Success<String, Int>> = outcomes.recoverError(
            processor = errorProcessor,
        ) { error: Business.Failure<String, Int> ->
            error.data + 1
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

        val values: List<Business.Outcome<String, Int>> =
            source.withBusinessError(processor = processor).toList()

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

        val values: List<Business.Success<Unit, Int>> =
            source.consumeError(processor = processor).toList()

        assertEquals(listOf(failure), handledFailures)
        assertEquals(listOf(Business.Success(meta = Unit, data = 1)), values)
    }

    @Test
    fun consumeSuccessMeta_handlesMetaBeforeEmittingData() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessMetaProcessor<String> = object : BusinessMetaProcessor<String> {
            override fun onBusinessMeta(meta: String) {
                events += "meta:$meta"
            }
        }
        val source: Flow<Business.Success<String, Int>> = flowOf(
            Business.Success(meta = "saved", data = 2),
        )

        source.consumeSuccessMeta(processor = processor)
            .collect { data: Int -> events += "data:$data" }

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

        val values: List<Int?> = source.dataOrNull(processor = processor).toList()

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
    fun withBusinessProgress_outcomeWithoutLoading_tracksSingleCollection() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessProgressProcessor = recordingProgressProcessor(events)
        val outcome: Business.Success<Unit, Int> = Business.Success(meta = Unit, data = 1)

        val values: List<Business.Success<Unit, Int>> =
            flowOf(outcome).withBusinessProgress(processor = processor).toList()

        assertEquals(listOf(outcome), values)
        assertEquals(listOf("start", "completion"), events)
    }

    @Test
    fun withBusinessProgress_preservesStatesAndTracksCollectionOnce() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessProgressProcessor = recordingProgressProcessor(events)
        val source: Flow<Business<Unit, Int>> = flowOf(
            Business.Loading,
            Business.Success(meta = Unit, data = 1),
            Business.Loading,
            Business.Success(meta = Unit, data = 2),
        )

        with(processor) {
            source.withBusinessProgress()
        }.collect { business: Business<Unit, Int> ->
            events += when (business) {
                Business.Loading -> "loading"
                is Business.Outcome -> "outcome"
            }
        }

        assertEquals(
            listOf("start", "loading", "outcome", "loading", "outcome", "completion"),
            events,
        )
    }

    @Test
    fun withBusinessProgressCycles_supportsSequentialLoadingCycles() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessProgressProcessor = recordingProgressProcessor(events)
        val source: Flow<Business<Unit, Int>> = flowOf(
            Business.Loading,
            Business.Success(meta = Unit, data = 1),
            Business.Loading,
            Business.Failure(
                exception = IllegalStateException("failed"),
                meta = Unit,
                data = 2,
            ),
        )

        with(processor) {
            source.withBusinessProgressCycles()
        }.collect { }

        assertEquals(
            listOf("start", "completion", "start", "completion"),
            events,
        )
    }

    @Test
    fun withBusinessProgressCycles_outcomeWithoutLoadingDoesNotChangeProgress() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessProgressProcessor = recordingProgressProcessor(events)
        val outcome: Business.Success<Unit, Int> = Business.Success(meta = Unit, data = 1)

        val values: List<Business.Success<Unit, Int>> =
            flowOf(outcome).withBusinessProgressCycles(processor = processor).toList()

        assertEquals(listOf(outcome), values)
        assertTrue(events.isEmpty())
    }

    @Test
    fun consumeLoadingCycles_filtersLoadingAndTracksEveryCycle() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessProgressProcessor = recordingProgressProcessor(events)
        val first: Business.Success<Unit, Int> = Business.Success(meta = Unit, data = 1)
        val second: Business.Success<Unit, Int> = Business.Success(meta = Unit, data = 2)
        val source: Flow<Business<Unit, Int>> = flowOf(
            Business.Loading,
            first,
            Business.Loading,
            second,
        )

        val values: List<Business.Outcome<Unit, Int>> =
            source.consumeLoadingCycles(processor = processor).toList()

        assertEquals(listOf(first, second), values)
        assertEquals(
            listOf("start", "completion", "start", "completion"),
            events,
        )
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
    fun withBusinessProgress_upstreamExceptionCompletesAndRethrows() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessProgressProcessor = recordingProgressProcessor(events)
        val expectedException = IllegalStateException("failed")
        val source: Flow<Business<Unit, Int>> = flow {
            emit(Business.Loading)
            throw expectedException
        }
        var actualException: Exception? = null

        try {
            with(processor) { source.withBusinessProgress() }.collect { }
        } catch (exception: Exception) {
            actualException = exception
        }

        assertSame(expectedException, actualException)
        assertEquals(listOf("start", "completion"), events)
    }

    @Test
    fun withBusinessProgress_startFailureStillAttemptsCompletion() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val startFailure = IllegalStateException("start failed")
        val processor: BusinessProgressProcessor = object : BusinessProgressProcessor {
            override fun onBusinessStart() {
                events += "start"
                throw startFailure
            }

            override fun onBusinessCompletion() {
                events += "completion"
            }
        }
        var actualFailure: Throwable? = null

        try {
            with(processor) {
                flowOf<Business<Unit, Int>>(Business.Loading).withBusinessProgress()
            }.collect { }
        } catch (failure: Throwable) {
            actualFailure = failure
        }

        assertSame(startFailure, actualFailure)
        assertEquals(listOf("start", "completion"), events)
    }

    @Test
    fun withBusinessProgress_completionFailureIsSuppressedByUpstreamFailure() = runBlocking {
        val upstreamFailure = IllegalStateException("upstream failed")
        val completionFailure = IllegalArgumentException("completion failed")
        val processor: BusinessProgressProcessor = failingCompletionProcessor(completionFailure)
        val source: Flow<Business<Unit, Int>> = flow {
            emit(Business.Loading)
            throw upstreamFailure
        }
        var actualFailure: Throwable? = null

        try {
            with(processor) { source.withBusinessProgress() }.collect { }
        } catch (failure: Throwable) {
            actualFailure = failure
        }

        assertSame(upstreamFailure, actualFailure)
        assertEquals(1, upstreamFailure.suppressed.size)
        assertSame(completionFailure, upstreamFailure.suppressed.single())
    }

    @Test
    fun withBusinessProgress_completionFailureIsSuppressedByDownstreamFailure() = runBlocking {
        val downstreamFailure = IllegalStateException("downstream failed")
        val completionFailure = IllegalArgumentException("completion failed")
        val processor: BusinessProgressProcessor = failingCompletionProcessor(completionFailure)
        val source: Flow<Business<Unit, Int>> = flowOf(Business.Loading)
        var actualFailure: Throwable? = null

        try {
            with(processor) { source.withBusinessProgress() }.collect {
                throw downstreamFailure
            }
        } catch (failure: Throwable) {
            actualFailure = failure
        }

        assertSame(downstreamFailure, actualFailure)
        assertEquals(1, downstreamFailure.suppressed.size)
        assertSame(completionFailure, downstreamFailure.suppressed.single())
    }

    @Test
    fun withBusinessProgress_completionFailureDoesNotReplaceCancellation() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val completionFailure = IllegalStateException("completion failed")
        val processor: BusinessProgressProcessor = failingCompletionProcessor(completionFailure)
        val source: Flow<Business<Unit, Int>> = flow {
            emit(Business.Loading)
            throw cancellation
        }
        var actualFailure: Throwable? = null

        try {
            with(processor) { source.withBusinessProgress() }.collect { }
        } catch (failure: Throwable) {
            actualFailure = failure
        }

        assertSame(cancellation, actualFailure)
        assertEquals(1, cancellation.suppressed.size)
        assertSame(completionFailure, cancellation.suppressed.single())
    }

    @Test
    fun withBusinessProgress_completionFailureWithoutPrimaryFailureIsRethrown() = runBlocking {
        val completionFailure = IllegalStateException("completion failed")
        val processor: BusinessProgressProcessor = failingCompletionProcessor(completionFailure)
        val source: Flow<Business<Unit, Int>> = flowOf(
            Business.Loading,
            Business.Success(meta = Unit, data = 1),
        )
        val values: MutableList<Business<Unit, Int>> = mutableListOf()
        var actualFailure: Throwable? = null

        try {
            with(processor) { source.withBusinessProgress() }.collect(values::add)
        } catch (failure: Throwable) {
            actualFailure = failure
        }

        assertSame(completionFailure, actualFailure)
        assertEquals(2, values.size)
        assertTrue(completionFailure.suppressed.isEmpty())
    }

    @Test
    fun withBusinessProgressCycles_concurrentCollectorsKeepIndependentState() = runBlocking {
        val events: MutableList<String> = mutableListOf()
        val processor: BusinessProgressProcessor = recordingProgressProcessor(events)
        val source: Flow<Business<Unit, Int>> = flow {
            emit(Business.Loading)
            awaitCancellation()
        }
        val firstJob = launch {
            with(processor) { source.withBusinessProgressCycles() }.collect { }
        }
        val secondJob = launch {
            with(processor) { source.withBusinessProgressCycles() }.collect { }
        }
        while (events.count { event: String -> event == "start" } < 2) {
            yield()
        }

        firstJob.cancelAndJoin()
        secondJob.cancelAndJoin()

        assertEquals(2, events.count { event: String -> event == "start" })
        assertEquals(2, events.count { event: String -> event == "completion" })
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

    private fun failingCompletionProcessor(
        completionFailure: Throwable,
    ): BusinessProgressProcessor = object : BusinessProgressProcessor {
        override fun onBusinessStart() = Unit

        override fun onBusinessCompletion() {
            throw completionFailure
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
