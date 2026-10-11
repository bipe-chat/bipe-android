/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.proseiosignup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.appconfig.ProseioConfig
import io.element.android.features.login.impl.login.LoginModeEvent
import io.element.android.features.login.impl.login.LoginModeState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.proseio.ProseioHandle
import io.element.android.libraries.proseio.ProseioHandleAvailabilityChecker
import io.element.android.libraries.proseio.ProseioHandleStatus
import io.element.android.libraries.proseio.ProseioInviteCode
import io.element.android.libraries.proseio.R
import io.element.android.libraries.proseio.api.ProseioApiClient
import io.element.android.libraries.proseio.api.ProseioApiErrorCode
import io.element.android.libraries.proseio.api.ProseioApiException
import io.element.android.libraries.proseio.api.ProseioSignupRequest
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Port of ProseioSignupScreenViewModel on iOS: create the account through the Proseio API,
 * then sign in through MAS with the e-mail as login hint.
 */
@AssistedInject
class ProseioSignupPresenter(
    @Assisted private val params: Params,
    private val apiClient: ProseioApiClient,
    private val loginModePresenter: Presenter<LoginModeState>,
    private val stringProvider: StringProvider,
) : Presenter<ProseioSignupState> {
    data class Params(
        /** From an invite link. */
        val inviteCode: ProseioInviteCode?,
    )

    @AssistedFactory
    interface Factory {
        fun create(params: Params): ProseioSignupPresenter
    }

    @Composable
    override fun present(): ProseioSignupState {
        val coroutineScope = rememberCoroutineScope()
        val checker = remember { ProseioHandleAvailabilityChecker(apiClient, coroutineScope) }
        val loginModeState = loginModePresenter.present()

        // Plain remember, not rememberSaveable: the password must not go into the saved instance state.
        var displayName by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordConfirmation by remember { mutableStateOf("") }
        var handle by remember { mutableStateOf("") }
        var inviteCodeInput by remember { mutableStateOf(params.inviteCode?.value.orEmpty()) }
        var handleStatus by remember { mutableStateOf<ProseioHandleStatus>(ProseioHandleStatus.Empty) }
        var isAccountCreated by remember { mutableStateOf(false) }
        var createdEmail by remember { mutableStateOf<String?>(null) }
        var emailError by remember { mutableStateOf<String?>(null) }
        var passwordError by remember { mutableStateOf<String?>(null) }
        var handleError by remember { mutableStateOf<String?>(null) }
        var inviteCodeError by remember { mutableStateOf<String?>(null) }
        var inviterHandle by remember { mutableStateOf<String?>(null) }
        var createAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        // Validity and the inviter's handle, each time the field holds a whole code.
        val inviteCode = ProseioInviteCode.parse(inviteCodeInput)
        LaunchedEffect(inviteCode) {
            inviteCode ?: return@LaunchedEffect
            val preview = try {
                apiClient.invitePreview(inviteCode.value)
            } catch (_: ProseioApiException) {
                return@LaunchedEffect
            }
            if (preview.valid) {
                inviterHandle = preview.inviterHandle
            } else {
                inviteCodeError = stringProvider.getString(R.string.proseio_error_invite_invalid)
            }
        }

        /** Field errors stay under their field; anything else is a dialog. */
        fun show(error: ProseioApiException) {
            val message = error.localizedMessage(stringProvider)
            createAction = AsyncAction.Uninitialized
            when ((error as? ProseioApiException.Server)?.code) {
                ProseioApiErrorCode.EMAIL_TAKEN -> emailError = message
                ProseioApiErrorCode.PASSWORD_WEAK -> passwordError = message
                ProseioApiErrorCode.HANDLE_TAKEN, ProseioApiErrorCode.HANDLE_INVALID -> handleError = message
                ProseioApiErrorCode.INVITE_INVALID -> inviteCodeError = message
                else -> createAction = AsyncAction.Failure(Exception(message))
            }
        }

        suspend fun createAccount(): Boolean {
            val trimmedEmail = email.trim()
            val sanitizedHandle = ProseioHandle.sanitize(handle)
            val trimmedName = displayName.trim()
            val request = ProseioSignupRequest(
                inviteCode = ProseioInviteCode.parse(inviteCodeInput)?.value,
                email = trimmedEmail,
                password = password,
                handle = sanitizedHandle.ifEmpty { null },
                displayName = trimmedName.ifEmpty { null },
            )
            createAction = AsyncAction.Loading
            return try {
                val response = apiClient.signup(request)
                Timber.i("Proseio sign-up created ${response.userId}")
                createdEmail = trimmedEmail
                isAccountCreated = true
                password = ""
                passwordConfirmation = ""
                createAction = AsyncAction.Uninitialized
                true
            } catch (error: ProseioApiException) {
                Timber.e(error, "Proseio sign-up failed")
                show(error)
                false
            }
        }

        fun startLogin() {
            loginModeState.eventSink(
                LoginModeEvent.Submit(
                    isAccountCreation = false,
                    homeserverUrl = ProseioConfig.HOMESERVER_BASE_URL,
                    resolvedHomeserverUrl = null,
                    loginHint = createdEmail,
                )
            )
        }

        fun handleEvent(event: ProseioSignupEvent) {
            when (event) {
                is ProseioSignupEvent.UpdateDisplayName -> displayName = event.value
                is ProseioSignupEvent.UpdateEmail -> {
                    email = event.value
                    emailError = null
                }
                is ProseioSignupEvent.UpdatePassword -> {
                    password = event.value
                    passwordError = null
                }
                is ProseioSignupEvent.UpdatePasswordConfirmation -> passwordConfirmation = event.value
                is ProseioSignupEvent.UpdateHandle -> {
                    handle = event.value
                    handleError = null
                    checker.check(event.value) { handleStatus = it }
                }
                is ProseioSignupEvent.UpdateInviteCode -> {
                    inviteCodeError = null
                    inviterHandle = null
                    // A pasted link or message collapses to the code it holds.
                    inviteCodeInput = ProseioInviteCode.parse(event.value)?.value ?: event.value
                }
                ProseioSignupEvent.Submit -> coroutineScope.launch {
                    if (!isAccountCreated && !createAccount()) return@launch
                    startLogin()
                }
                ProseioSignupEvent.ClearError -> {
                    createAction = AsyncAction.Uninitialized
                    loginModeState.eventSink(LoginModeEvent.ClearError)
                }
            }
        }

        return ProseioSignupState(
            displayName = displayName,
            email = email,
            password = password,
            passwordConfirmation = passwordConfirmation,
            handle = handle,
            inviteCodeInput = inviteCodeInput,
            handleStatus = handleStatus,
            isAccountCreated = isAccountCreated,
            emailError = emailError,
            passwordError = passwordError,
            handleError = handleError,
            inviteCodeError = inviteCodeError,
            inviterHandle = inviterHandle,
            createAction = createAction,
            loginModeState = loginModeState,
            eventSink = ::handleEvent,
        )
    }
}
