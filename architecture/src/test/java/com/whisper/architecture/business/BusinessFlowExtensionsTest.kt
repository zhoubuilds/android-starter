package com.whisper.architecture.business

import com.whisper.architecture.business.function.consumeSuccessMeta
import com.whisper.architecture.business.function.withLoading
import com.whisper.architecture.business.model.ArchitectureBusiness
import com.whisper.architecture.business.processor.BusinessErrorProcessor
import com.whisper.architecture.business.processor.BusinessMetaProcessor
import com.whisper.architecture.business.processor.BusinessProgressProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/**
 * 验证业务 Flow 扩展的状态处理和类型收窄行为.
 *
 * @author whisper
 * @since 2026/07/24
 */
class BusinessFlowExtensionsTest {

    /**
     * 验证使用 `with` 提供处理器时可以连续消费 Loading 并恢复 Error.
     */
    @Test
    fun consumeLoadingAndRecoverError_withHandlers_returnsSuccessOnly() = runBlocking {
        var startCount: Int = 0
        var completionCount: Int = 0
        var errorCount: Int = 0
        val progressHandler: BusinessProgressProcessor = object : BusinessProgressProcessor {
            override fun onBusinessStart() {
                startCount += 1
            }

            override fun onBusinessCompletion() {
                completionCount += 1
            }
        }
        val errorHandler: BusinessErrorProcessor = object : BusinessErrorProcessor {
            override fun onBusinessError(error: ArchitectureBusiness.Error<*, *>) {
                errorCount += 1
            }
        }
        val source: Flow<ArchitectureBusiness<Int, Unit>> = flowOf(
            ArchitectureBusiness.Loading,
            ArchitectureBusiness.Success<Int, Unit>(1),
            ArchitectureBusiness.Error<Int, Unit>(IllegalStateException("failed")),
        )

        val outcomeFlow: Flow<ArchitectureBusiness.Outcome<Int, Unit>> = with(progressHandler) {
            source.consumeLoading()
        }
        val successFlow: Flow<ArchitectureBusiness.Success<Int, Unit>> = with(errorHandler) {
            outcomeFlow.recoverError { _: ArchitectureBusiness.Error<Int, Unit> -> -1 }
        }
        val values: List<ArchitectureBusiness.Success<Int, Unit>> = successFlow.toList()

        assertEquals(
            listOf(
                ArchitectureBusiness.Success<Int, Unit>(1),
                ArchitectureBusiness.Success<Int, Unit>(-1),
            ),
            values
        )
        assertEquals(1, startCount)
        assertEquals(1, completionCount)
        assertEquals(1, errorCount)
    }

    /**
     * 验证只处理错误时保留原始错误及 Flow 类型.
     */
    @Test
    fun withBusinessError_observesAndKeepsError() = runBlocking {
        val exception: Exception = IllegalArgumentException("failed")
        val error: ArchitectureBusiness.Error<Int, Unit> = ArchitectureBusiness.Error(exception)
        var handledError: ArchitectureBusiness.Error<*, *>? = null
        val handler: BusinessErrorProcessor = object : BusinessErrorProcessor {
            override fun onBusinessError(error: ArchitectureBusiness.Error<*, *>) {
                handledError = error
            }
        }
        val source: Flow<ArchitectureBusiness.Outcome<Int, Unit>> = flowOf(error)

        val values: List<ArchitectureBusiness.Outcome<Int, Unit>> = with(handler) {
            source.withBusinessError()
        }.toList()

        assertEquals(listOf(error), values)
        assertSame(error, handledError)
    }

    /**
     * 验证消费错误时下游只接收到成功结果.
     */
    @Test
    fun consumeError_dropsErrorAndReturnsSuccessOnly() = runBlocking {
        var errorCount: Int = 0
        val handler: BusinessErrorProcessor = object : BusinessErrorProcessor {
            override fun onBusinessError(error: ArchitectureBusiness.Error<*, *>) {
                errorCount += 1
            }
        }
        val source: Flow<ArchitectureBusiness.Outcome<Int, Unit>> = flowOf(
            ArchitectureBusiness.Success<Int, Unit>(1),
            ArchitectureBusiness.Error<Int, Unit>(IllegalStateException("failed")),
        )

        val values: List<ArchitectureBusiness.Success<Int, Unit>> = with(handler) {
            source.consumeError()
        }.toList()

        assertEquals(listOf(ArchitectureBusiness.Success<Int, Unit>(1)), values)
        assertEquals(1, errorCount)
    }

    /**
     * 验证成功元信息会被处理, 且成功状态会脱壳为业务数据.
     */
    @Test
    fun consumeSuccessMeta_handlesSuccessMetadataAndReturnsData() = runBlocking {
        val handledMetadata: MutableList<Any?> = mutableListOf()
        val handler: BusinessMetaProcessor = object : BusinessMetaProcessor {
            override fun onBusinessMeta(metadata: Any?) {
                handledMetadata.add(metadata)
            }
        }
        val source: Flow<ArchitectureBusiness.Success<Int, String>> = flowOf(
            ArchitectureBusiness.Success(
                data = 1,
                metadata = "saved",
            ),
            ArchitectureBusiness.Success(
                data = 2,
                metadata = null,
            ),
        )

        val values: List<Int> = with(handler) {
            source.consumeSuccessMeta()
        }.toList()

        assertEquals(listOf(1, 2), values)
        assertEquals(listOf("saved", null), handledMetadata)
    }

    /**
     * 验证不提供元信息处理器时可以直接脱壳成功数据.
     */
    @Test
    fun consumeSuccessMeta_withoutHandler_returnsData() = runBlocking {
        val source: Flow<ArchitectureBusiness.Success<Int, String>> = flowOf(
            ArchitectureBusiness.Success(
                data = 1,
                metadata = "saved",
            )
        )

        val values: List<Int> = source.consumeSuccessMeta().toList()

        assertEquals(listOf(1), values)
    }

    /**
     * 验证完整链路会先消费错误, 再消费成功元信息, 最后只返回业务数据.
     */
    @Test
    fun consumeErrorAndConsumeSuccessMeta_returnsDataOnly() = runBlocking {
        val handledErrors: MutableList<ArchitectureBusiness.Error<*, *>> = mutableListOf()
        val handledMetadata: MutableList<Any?> = mutableListOf()
        val errorHandler: BusinessErrorProcessor = object : BusinessErrorProcessor {
            override fun onBusinessError(error: ArchitectureBusiness.Error<*, *>) {
                handledErrors.add(error)
            }
        }
        val metaHandler: BusinessMetaProcessor = object : BusinessMetaProcessor {
            override fun onBusinessMeta(metadata: Any?) {
                handledMetadata.add(metadata)
            }
        }
        val error: ArchitectureBusiness.Error<Int, String> = ArchitectureBusiness.Error(
            exception = IllegalStateException("failed"),
            metadata = "failed meta",
        )
        val source: Flow<ArchitectureBusiness.Outcome<Int, String>> = flowOf(
            ArchitectureBusiness.Success(
                data = 1,
                metadata = "saved",
            ),
            error,
        )

        val successFlow: Flow<ArchitectureBusiness.Success<Int, String>> = with(errorHandler) {
            source.consumeError()
        }
        val values: List<Int> = with(metaHandler) {
            successFlow.consumeSuccessMeta()
        }.toList()

        assertEquals(listOf(1), values)
        assertEquals(listOf(error), handledErrors)
        assertEquals(listOf("saved"), handledMetadata)
    }

    /**
     * 验证错误可以被消费并转换为 `null` 数据.
     */
    @Test
    fun dataOrNull_convertsErrorToNull() = runBlocking {
        var errorCount: Int = 0
        val handler: BusinessErrorProcessor = object : BusinessErrorProcessor {
            override fun onBusinessError(error: ArchitectureBusiness.Error<*, *>) {
                errorCount += 1
            }
        }
        val source: Flow<ArchitectureBusiness.Outcome<Int, Unit>> = flowOf(
            ArchitectureBusiness.Success<Int, Unit>(2),
            ArchitectureBusiness.Error<Int, Unit>(IllegalStateException("failed")),
        )

        val values: List<Int?> = with(handler) {
            source.dataOrNull()
        }.toList()

        assertEquals(listOf(2, null), values)
        assertEquals(1, errorCount)
    }

    /**
     * 验证只消费 Loading 时不会向下游发送元素, 但会完整处理收集进度.
     */
    @Test
    fun consumeLoading_loadingOnly_returnsEmptyAndCompletesProgress() = runBlocking {
        var startCount: Int = 0
        var completionCount: Int = 0
        val handler: BusinessProgressProcessor = object : BusinessProgressProcessor {
            override fun onBusinessStart() {
                startCount += 1
            }

            override fun onBusinessCompletion() {
                completionCount += 1
            }
        }
        val source: Flow<ArchitectureBusiness<Int, Unit>> = flowOf(ArchitectureBusiness.Loading)

        val values: List<ArchitectureBusiness.Outcome<Int, Unit>> = with(handler) {
            source.consumeLoading()
        }.toList()

        assertEquals(emptyList<ArchitectureBusiness.Outcome<Int, Unit>>(), values)
        assertEquals(1, startCount)
        assertEquals(1, completionCount)
    }

    /**
     * 验证只消费 Error 时返回空 Flow, 且错误会交给处理器.
     */
    @Test
    fun consumeError_errorOnly_returnsEmptyAndHandlesError() = runBlocking {
        var errorCount: Int = 0
        val handler: BusinessErrorProcessor = object : BusinessErrorProcessor {
            override fun onBusinessError(error: ArchitectureBusiness.Error<*, *>) {
                errorCount += 1
            }
        }
        val source: Flow<ArchitectureBusiness.Outcome<Int, Unit>> = flowOf(
            ArchitectureBusiness.Error<Int, Unit>(IllegalStateException("failed"))
        )

        val values: List<ArchitectureBusiness.Success<Int, Unit>> = with(handler) {
            source.consumeError()
        }.toList()

        assertEquals(emptyList<ArchitectureBusiness.Success<Int, Unit>>(), values)
        assertEquals(1, errorCount)
    }

    /**
     * 验证收集取消时仍会调用进度结束处理.
     */
    @Test
    fun withBusinessProgress_cancelledFlow_completesProgressAndRethrows() = runBlocking {
        var startCount: Int = 0
        var completionCount: Int = 0
        val handler: BusinessProgressProcessor = object : BusinessProgressProcessor {
            override fun onBusinessStart() {
                startCount += 1
            }

            override fun onBusinessCompletion() {
                completionCount += 1
            }
        }
        val cancellationException: CancellationException = CancellationException("cancelled")
        val source: Flow<ArchitectureBusiness<Int, Unit>> = flow {
            throw cancellationException
        }

        try {
            with(handler) {
                source.withBusinessProgress()
            }.toList()
            fail("CancellationException should be rethrown.")
        } catch (exception: CancellationException) {
            assertSame(cancellationException, exception)
        }

        assertEquals(1, startCount)
        assertEquals(1, completionCount)
    }

    /**
     * 验证可为不包含 Loading 的业务结果追加 Loading 外壳.
     */
    @Test
    fun withLoading_prependsLoading() = runBlocking {
        val source: Flow<ArchitectureBusiness.Outcome<Int, Unit>> = flowOf(
            ArchitectureBusiness.Success<Int, Unit>(3)
        )

        val values: List<ArchitectureBusiness<Int, Unit>> = source.withLoading().toList()

        assertEquals(
            listOf(
                ArchitectureBusiness.Loading,
                ArchitectureBusiness.Success<Int, Unit>(3),
            ),
            values
        )
    }

    /**
     * 验证空业务结果追加 Loading 后只发送 Loading.
     */
    @Test
    fun withLoading_emptyOutcomeFlow_emitsLoadingOnly() = runBlocking {
        val source: Flow<ArchitectureBusiness.Outcome<Int, Unit>> = emptyFlow()

        val values: List<ArchitectureBusiness<Int, Unit>> = source.withLoading().toList()

        assertEquals(listOf(ArchitectureBusiness.Loading), values)
    }
}
