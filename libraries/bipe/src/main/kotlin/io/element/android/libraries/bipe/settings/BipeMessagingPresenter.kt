/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.di.BipeSession
import kotlinx.coroutines.launch

@Inject
class BipeMessagingPresenter(
    private val bipeSession: BipeSession,
) : Presenter<BipeMessagingState> {
    @Composable
    override fun present(): BipeMessagingState {
        val scope = rememberCoroutineScope()
        val selected by bipeSession.privacy.messaging.collectAsState()
        var saveAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        LaunchedEffect(Unit) { bipeSession.privacy.refreshMessaging() }

        fun handleEvent(event: BipeMessagingEvent) {
            when (event) {
                is BipeMessagingEvent.Select -> {
                    if (event.value == selected || saveAction.isLoading()) return
                    saveAction = AsyncAction.Loading
                    scope.launch {
                        saveAction = bipeSession.privacy.setMessaging(event.value).fold(
                            onSuccess = { AsyncAction.Uninitialized },
                            onFailure = { AsyncAction.Failure(Exception(bipeSession.account.messageOf(it))) },
                        )
                    }
                }
                BipeMessagingEvent.ClearError -> saveAction = AsyncAction.Uninitialized
            }
        }

        return BipeMessagingState(selected = selected, saveAction = saveAction, eventSink = ::handleEvent)
    }
}
