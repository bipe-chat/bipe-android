/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.terms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import kotlinx.coroutines.launch

/**
 * The box starts unticked. Continue sends the acceptance with [acceptTerms] (`BipeAccountSession.acceptTerms`).
 * On success the account status carries the current version and the host removes the screen. A failure keeps the screen.
 */
class BipeTermsReacceptPresenter(
    private val acceptTerms: suspend () -> Result<Unit>,
) : Presenter<BipeTermsReacceptState> {
    @Composable
    override fun present(): BipeTermsReacceptState {
        val coroutineScope = rememberCoroutineScope()
        var isAccepted by rememberSaveable { mutableStateOf(false) }
        var saveAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        fun handleEvent(event: BipeTermsReacceptEvent) {
            when (event) {
                is BipeTermsReacceptEvent.SetAccepted -> {
                    isAccepted = event.accepted
                    if (saveAction is AsyncAction.Failure) saveAction = AsyncAction.Uninitialized
                }
                BipeTermsReacceptEvent.Continue -> {
                    if (!isAccepted || saveAction is AsyncAction.Loading) return
                    saveAction = AsyncAction.Loading
                    coroutineScope.launch {
                        saveAction = acceptTerms().fold(
                            onSuccess = { AsyncAction.Success(Unit) },
                            onFailure = { AsyncAction.Failure(it) },
                        )
                    }
                }
            }
        }

        return BipeTermsReacceptState(
            isAccepted = isAccepted,
            saveAction = saveAction,
            eventSink = ::handleEvent,
        )
    }
}
