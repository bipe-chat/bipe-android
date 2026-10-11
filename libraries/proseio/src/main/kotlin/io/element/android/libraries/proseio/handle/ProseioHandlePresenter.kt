/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.handle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.proseio.ProseioHandle
import io.element.android.libraries.proseio.ProseioHandleAvailabilityChecker
import io.element.android.libraries.proseio.ProseioHandleStatus
import io.element.android.libraries.proseio.api.ProseioApiClient
import io.element.android.libraries.proseio.api.ProseioApiErrorCode
import io.element.android.libraries.proseio.api.ProseioApiException
import io.element.android.libraries.proseio.api.ProseioMyHandle
import io.element.android.libraries.proseio.di.ProseioSession
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

/** Port of ProseioHandleScreenViewModel on iOS. */
@Inject
class ProseioHandlePresenter(
    private val proseioSession: ProseioSession,
    private val stringProvider: StringProvider,
) : Presenter<ProseioHandleState> {
    @Composable
    override fun present(): ProseioHandleState {
        val coroutineScope = rememberCoroutineScope()
        val checker = remember { ProseioHandleAvailabilityChecker(proseioSession.apiClient, coroutineScope) }

        var isLoading by remember { mutableStateOf(true) }
        var currentHandle by remember { mutableStateOf<String?>(null) }
        var changeableAt by remember { mutableStateOf<Instant?>(null) }
        var handleInput by remember { mutableStateOf("") }
        var handleStatus by remember { mutableStateOf<ProseioHandleStatus>(ProseioHandleStatus.Empty) }
        var handleError by remember { mutableStateOf<String?>(null) }
        var saveAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        fun apply(myHandle: ProseioMyHandle) {
            currentHandle = myHandle.handle
            changeableAt = myHandle.changeableAt
            handleInput = myHandle.handle.orEmpty()
            handleStatus = ProseioHandleStatus.Empty
            proseioSession.handleResolver.store(myHandle.handle, myHandle.userId)
        }

        /** Field errors stay under the field; anything else is a dialog. */
        fun show(error: ProseioApiException) {
            val isFieldError = error is ProseioApiException.Server && error.code in FIELD_ERRORS
            val message = error.localizedMessage(stringProvider)
            if (isFieldError) {
                handleError = message
                saveAction = AsyncAction.Uninitialized
            } else {
                saveAction = AsyncAction.Failure(Exception(message))
            }
        }

        LaunchedEffect(Unit) {
            try {
                apply(withTokenRetry { token -> proseioSession.apiClient.myHandle(token) })
            } catch (error: ProseioApiException) {
                Timber.e(error, "Proseio handle load failed")
                show(error)
            } finally {
                isLoading = false
            }
        }

        fun handleEvent(event: ProseioHandleEvent) {
            when (event) {
                is ProseioHandleEvent.UpdateHandle -> {
                    handleInput = event.input
                    handleError = null
                    val handle = ProseioHandle.sanitize(event.input)
                    if (handle == currentHandle) {
                        checker.cancel()
                        handleStatus = ProseioHandleStatus.Empty
                    } else {
                        checker.check(handle) { handleStatus = it }
                    }
                }
                ProseioHandleEvent.Save -> coroutineScope.launch {
                    val handle = ProseioHandle.sanitize(handleInput)
                    isLoading = true
                    saveAction = AsyncAction.Loading
                    try {
                        apply(withTokenRetry { token -> proseioSession.apiClient.setMyHandle(handle, token) })
                        saveAction = AsyncAction.Success(Unit)
                    } catch (error: ProseioApiException) {
                        Timber.e(error, "Proseio handle change failed")
                        show(error)
                    } finally {
                        isLoading = false
                    }
                }
                ProseioHandleEvent.ClearError -> saveAction = AsyncAction.Uninitialized
            }
        }

        return ProseioHandleState(
            isLoading = isLoading,
            currentHandle = currentHandle,
            changeableAt = changeableAt,
            handleInput = handleInput,
            handleStatus = handleStatus,
            handleError = handleError,
            saveAction = saveAction,
            now = Instant.now(),
            eventSink = ::handleEvent,
        )
    }

    private suspend fun <T> withTokenRetry(request: suspend (String) -> T): T =
        ProseioApiClient.withTokenRetry(proseioSession.accessToken, request)

    private companion object {
        val FIELD_ERRORS = setOf(ProseioApiErrorCode.HANDLE_TAKEN, ProseioApiErrorCode.HANDLE_INVALID, ProseioApiErrorCode.HANDLE_COOLDOWN)
    }
}
