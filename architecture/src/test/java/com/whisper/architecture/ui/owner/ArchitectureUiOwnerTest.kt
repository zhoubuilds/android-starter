package com.whisper.architecture.ui.owner

import com.whisper.architecture.model.ui.notice.NoticeImportance
import com.whisper.architecture.model.ui.notice.NoticeTone
import com.whisper.architecture.model.ui.notice.NoticeUiModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 Architecture UI Owner 的默认实现.
 *
 * @author whisper
 * @since 2026/08/26
 */
class ArchitectureUiOwnerTest {

    /**
     * 验证正在进行的操作数量会随开始和完成事件更新且不会小于零.
     */
    @Test
    fun activeOperationCount_updatesAndNeverBelowZero() {
        val owner: MutableArchitectureUiOwner = DefaultArchitectureUiOwner()

        assertEquals(0, owner.activeOperationCountFlow.value)

        owner.onOperationStarted()
        assertEquals(1, owner.activeOperationCountFlow.value)

        owner.onOperationStarted()
        assertEquals(2, owner.activeOperationCountFlow.value)

        owner.onOperationCompleted()
        assertEquals(1, owner.activeOperationCountFlow.value)

        owner.onOperationCompleted()
        assertEquals(0, owner.activeOperationCountFlow.value)

        owner.onOperationCompleted()
        assertEquals(0, owner.activeOperationCountFlow.value)
    }

    /**
     * 验证 UI 通知 Effect 可以发送给已订阅的收集者.
     */
    @Test
    fun notice_emitsEffectToActiveCollector() = runBlocking {
        val owner: MutableArchitectureUiOwner = DefaultArchitectureUiOwner()
        val notice: NoticeUiModel = NoticeUiModel(
            content = "done",
            importance = NoticeImportance.LOW,
            tone = NoticeTone.SUCCESS,
        )
        val deferredNotice: Deferred<NoticeUiModel> = async {
            owner.noticeUiEffectFlow.first()
        }

        yield()
        owner.notice(notice)

        assertEquals(notice, withTimeout(1_000) { deferredNotice.await() })
    }
}
