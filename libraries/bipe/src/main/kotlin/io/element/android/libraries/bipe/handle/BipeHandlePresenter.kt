/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.handle

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
import io.element.android.libraries.bipe.BipeHandle
import io.element.android.libraries.bipe.BipeHandleAvailabilityChecker
import io.element.android.libraries.bipe.BipeHandleStatus
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiErrorCode
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeMyHandle
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

/** Port of BipeHandleScreenViewModel on iOS. */
@Inject
class BipeHandlePresenter(
    private val bipeSession: BipeSession,
    private val stringProvider: StringProvider,
) : Presenter<BipeHandleState> {
    @Composable
    override fun present(): BipeHandleState {
        val coroutineScope = rememberCoroutineScope()
        val checker = remember { BipeHandleAvailabilityChecker(bipeSession.apiClient, coroutineScope) }

        var isLoading by remember { mutableStateOf(true) }
        var currentHandle by remember { mutableStateOf<String?>(null) }
        var changeableAt by remember { mutableStateOf<Instant?>(null) }
        var handleInput by remember { mutableStateOf("") }
        var handleStatus by remember { mutableStateOf<BipeHandleStatus>(BipeHandleStatus.Empty) }
        var handleError by remember { mutableStateOf<String?>(null) }
        var saveAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        var removeAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        fun apply(myHandle: BipeMyHandle) {
            currentHandle = myHandle.handle
            changeableAt = myHandle.changeableAt
            handleInput = myHandle.handle.orEmpty()
            handleStatus = BipeHandleStatus.Empty
            bipeSession.handleResolver.store(myHandle.handle, myHandle.userId)
        }

        /** Field errors stay under the field; anything else is a dialog. */
        fun show(error: BipeApiException) {
            val isFieldError = error is BipeApiException.Server && error.code in FIELD_ERRORS
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
                apply(withTokenRetry { token -> bipeSession.apiClient.myHandle(token) })
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe handle load failed")
                show(error)
            } finally {
                isLoading = false
            }
        }

        fun handleEvent(event: BipeHandleEvent) {
            when (event) {
                is BipeHandleEvent.UpdateHandle -> {
                    handleInput = event.input
                    handleError = null
                    val handle = BipeHandle.sanitize(event.input)
                    if (handle == currentHandle) {
                        checker.cancel()
                        handleStatus = BipeHandleStatus.Empty
                    } else {
                        checker.check(handle) { handleStatus = it }
                    }
                }
                BipeHandleEvent.Save -> coroutineScope.launch {
                    val handle = BipeHandle.sanitize(handleInput)
                    isLoading = true
                    saveAction = AsyncAction.Loading
                    try {
                        apply(withTokenRetry { token -> bipeSession.apiClient.setMyHandle(handle, token) })
                        saveAction = AsyncAction.Success(Unit)
                    } catch (error: BipeApiException) {
                        Timber.e(error, "Bipe handle change failed")
                        show(error)
                    } finally {
                        isLoading = false
                    }
                }
                BipeHandleEvent.ClearError -> saveAction = AsyncAction.Uninitialized
                BipeHandleEvent.AskRemove -> removeAction = AsyncAction.ConfirmingNoParams
                BipeHandleEvent.CancelRemove -> removeAction = AsyncAction.Uninitialized
                BipeHandleEvent.Remove -> coroutineScope.launch {
                    isLoading = true
                    removeAction = AsyncAction.Loading
                    try {
                        apply(withTokenRetry { token -> bipeSession.apiClient.removeMyHandle(token) })
                        removeAction = AsyncAction.Success(Unit)
                    } catch (error: BipeApiException) {
                        Timber.e(error, "Bipe handle removal failed")
                        removeAction = AsyncAction.Failure(Exception(error.localizedMessage(stringProvider)))
                    } finally {
                        isLoading = false
                    }
                }
            }
        }

        return BipeHandleState(
            isLoading = isLoading,
            currentHandle = currentHandle,
            changeableAt = changeableAt,
            handleInput = handleInput,
            handleStatus = handleStatus,
            handleError = handleError,
            saveAction = saveAction,
            removeAction = removeAction,
            now = Instant.now(),
            eventSink = ::handleEvent,
        )
    }

    private suspend fun <T> withTokenRetry(request: suspend (String) -> T): T =
        BipeApiClient.withTokenRetry(bipeSession.accessToken, request)

    private companion object {
        val FIELD_ERRORS = setOf(BipeApiErrorCode.HANDLE_TAKEN, BipeApiErrorCode.HANDLE_INVALID, BipeApiErrorCode.HANDLE_COOLDOWN)
    }
}
