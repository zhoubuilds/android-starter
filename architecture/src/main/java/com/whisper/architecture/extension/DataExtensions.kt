package com.whisper.architecture.extension

import com.whisper.architecture.bean.business.Business
import com.whisper.architecture.uimode.message.UiMessage
import com.whisper.architecture.uistate.MutableArchUiStateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform


/**
 * 只处理[com.whisper.architecture.bean.business.Business.Success]的数据
 *
 * 对于[com.whisper.architecture.bean.business.Business.Error]会调用[onError]处理
 *
 * 对于[com.whisper.architecture.bean.business.Business.Loading]会忽略
 **
 * @author whisper
 * @since 2025/9/26
 */
fun <T> Flow<Business<T>>.onlySuccess(
    onStart: () -> Unit = {},
    onCompleted: () -> Unit = {},
    onError: (Throwable) -> Unit = {}
): Flow<T> = this
    .onStart { onStart() }
    .transform { business ->
        when (business) {
            is Business.Success -> emit(business.data)
            is Business.Error -> throw business.e
            is Business.Loading -> {}
        }
    }
    .catch { e -> onError(e) }
    .onCompletion { onCompleted() }

fun <T> Flow<Business<T>>.onlySuccess(
    uiStateProvider: MutableArchUiStateProvider,
    transformer: (Throwable) -> UiMessage?
): Flow<T> =
    onlySuccess(
        uiStateProvider::onWorkStarted,
        uiStateProvider::onWorkCompleted
    ) { e -> transformer(e)?.let { uiStateProvider.showUiMessage(it) } }