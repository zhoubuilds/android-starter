package com.whisper.architecture.ui.component

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.whisper.architecture.model.ui.notice.NoticeImportance
import com.whisper.architecture.model.ui.notice.NoticeTone
import com.whisper.architecture.model.ui.notice.NoticeUiModel
import com.whisper.architecture.ui.effect.NoticeUiEffect
import com.whisper.architecture.ui.state.ActiveOperationCountUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode

/**
 * 验证 Architecture UI 组件的生命周期绑定、来源聚合和重复绑定语义.
 *
 * @author whisper
 * @since 2026/08/27
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ArchitectureUiComponentTest {

    @Test
    fun bind_withoutActiveOperationSources_emitsZeroWhenStarted() {
        val component: RecordingArchitectureUiComponent = RecordingArchitectureUiComponent()
        val lifecycleOwner: TestLifecycleOwner = TestLifecycleOwner()

        component.bind(emptyList(), emptyList(), lifecycleOwner)
        idleMainLooper()

        assertTrue(component.activeOperationCounts.isEmpty())

        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        idleMainLooper()

        assertEquals(listOf(0), component.activeOperationCounts)
        lifecycleOwner.moveTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun bind_withMultipleActiveOperationSources_emitsTheirCurrentSum() {
        val component: RecordingArchitectureUiComponent = RecordingArchitectureUiComponent()
        val lifecycleOwner: TestLifecycleOwner = TestLifecycleOwner()
        val firstState: TestActiveOperationCountUiState = TestActiveOperationCountUiState(1)
        val secondState: TestActiveOperationCountUiState = TestActiveOperationCountUiState(2)

        component.bind(listOf(firstState, secondState), emptyList(), lifecycleOwner)
        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        idleMainLooper()

        assertEquals(3, component.activeOperationCounts.last())

        firstState.value = 4
        idleMainLooper()

        assertEquals(6, component.activeOperationCounts.last())
        lifecycleOwner.moveTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun bind_belowStartedStopsCollectionAndRestartRestoresCurrentState() {
        val component: RecordingArchitectureUiComponent = RecordingArchitectureUiComponent()
        val lifecycleOwner: TestLifecycleOwner = TestLifecycleOwner()
        val state: TestActiveOperationCountUiState = TestActiveOperationCountUiState(1)

        component.bind(listOf(state), emptyList(), lifecycleOwner)
        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        idleMainLooper()

        lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        idleMainLooper()
        state.value = 5
        idleMainLooper()

        assertEquals(listOf(1), component.activeOperationCounts)

        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        idleMainLooper()

        assertEquals(listOf(1, 5), component.activeOperationCounts)
        lifecycleOwner.moveTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun bind_sameLifecycleOwnerRepeatedlyDoesNotDuplicateCollectors() {
        val component: RecordingArchitectureUiComponent = RecordingArchitectureUiComponent()
        val lifecycleOwner: TestLifecycleOwner = TestLifecycleOwner()
        val state: TestActiveOperationCountUiState = TestActiveOperationCountUiState(1)

        component.bind(listOf(state), emptyList(), lifecycleOwner)
        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        idleMainLooper()
        component.bind(listOf(state), emptyList(), lifecycleOwner)

        state.value = 2
        idleMainLooper()

        assertEquals(listOf(1, 2), component.activeOperationCounts)
        lifecycleOwner.moveTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun bind_differentLifecycleOwnerWhileActiveFails() {
        val component: RecordingArchitectureUiComponent = RecordingArchitectureUiComponent()
        val firstLifecycleOwner: TestLifecycleOwner = TestLifecycleOwner()
        val secondLifecycleOwner: TestLifecycleOwner = TestLifecycleOwner()

        component.bind(emptyList(), emptyList(), firstLifecycleOwner)

        assertThrows(IllegalStateException::class.java) {
            component.bind(emptyList(), emptyList(), secondLifecycleOwner)
        }

        firstLifecycleOwner.moveTo(Lifecycle.State.DESTROYED)
        secondLifecycleOwner.moveTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun bind_afterPreviousLifecycleOwnerDestroyedAcceptsNewOwner() {
        val component: RecordingArchitectureUiComponent = RecordingArchitectureUiComponent()
        val firstLifecycleOwner: TestLifecycleOwner = TestLifecycleOwner()
        val secondLifecycleOwner: TestLifecycleOwner = TestLifecycleOwner()
        val firstState: TestActiveOperationCountUiState = TestActiveOperationCountUiState(1)
        val secondState: TestActiveOperationCountUiState = TestActiveOperationCountUiState(2)

        component.bind(listOf(firstState), emptyList(), firstLifecycleOwner)
        firstLifecycleOwner.moveTo(Lifecycle.State.STARTED)
        idleMainLooper()
        firstLifecycleOwner.moveTo(Lifecycle.State.DESTROYED)
        idleMainLooper()

        component.bind(listOf(secondState), emptyList(), secondLifecycleOwner)
        secondLifecycleOwner.moveTo(Lifecycle.State.STARTED)
        idleMainLooper()

        assertEquals(listOf(1, 2), component.activeOperationCounts)
        secondLifecycleOwner.moveTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun bind_withMultipleNoticeSources_consumesEverySourceWithoutOrderingContract() {
        val component: RecordingArchitectureUiComponent = RecordingArchitectureUiComponent()
        val lifecycleOwner: TestLifecycleOwner = TestLifecycleOwner()
        val firstEffect: TestNoticeUiEffect = TestNoticeUiEffect()
        val secondEffect: TestNoticeUiEffect = TestNoticeUiEffect()
        val firstNotice: NoticeUiModel = notice("first")
        val secondNotice: NoticeUiModel = notice("second")

        component.bind(emptyList(), listOf(firstEffect, secondEffect), lifecycleOwner)
        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        idleMainLooper()

        assertTrue(firstEffect.emit(firstNotice))
        assertTrue(secondEffect.emit(secondNotice))
        idleMainLooper()

        assertEquals(2, component.notices.size)
        assertEquals(setOf(firstNotice, secondNotice), component.notices.toSet())
        lifecycleOwner.moveTo(Lifecycle.State.DESTROYED)
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun notice(content: String): NoticeUiModel =
        NoticeUiModel(
            content = content,
            importance = NoticeImportance.LOW,
            tone = NoticeTone.INFO,
        )

    /** 记录组件分发结果, 不引入具体 UI 渲染行为. */
    private class RecordingArchitectureUiComponent : ArchitectureUiComponent() {

        val activeOperationCounts: MutableList<Int> = mutableListOf()
        val notices: MutableList<NoticeUiModel> = mutableListOf()

        protected override fun onActiveOperationCountChanged(count: Int) {
            activeOperationCounts += count
        }

        protected override fun handleNotice(notice: NoticeUiModel) {
            notices += notice
        }
    }

    /** 提供可控生命周期状态的测试 Owner. */
    private class TestLifecycleOwner : LifecycleOwner {

        private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle = lifecycleRegistry

        fun moveTo(state: Lifecycle.State) {
            lifecycleRegistry.currentState = state
        }
    }

    /** 提供可更新的操作数量 StateFlow. */
    private class TestActiveOperationCountUiState(initialValue: Int) : ActiveOperationCountUiState {

        private val mutableActiveOperationCountFlow: MutableStateFlow<Int> =
            MutableStateFlow(initialValue)

        override val activeOperationCountFlow: StateFlow<Int> =
            mutableActiveOperationCountFlow.asStateFlow()

        var value: Int
            get() = mutableActiveOperationCountFlow.value
            set(value) {
                mutableActiveOperationCountFlow.value = value
            }
    }

    /** 提供可主动发送通知的 SharedFlow. */
    private class TestNoticeUiEffect : NoticeUiEffect {

        private val mutableNoticeUiEffectFlow: MutableSharedFlow<NoticeUiModel> =
            MutableSharedFlow(extraBufferCapacity = 1)

        override val noticeUiEffectFlow: SharedFlow<NoticeUiModel> =
            mutableNoticeUiEffectFlow.asSharedFlow()

        fun emit(notice: NoticeUiModel): Boolean =
            mutableNoticeUiEffectFlow.tryEmit(notice)
    }
}
