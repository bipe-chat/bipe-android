/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.bipesignup

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
import io.element.android.appconfig.BipeConfig
import io.element.android.features.login.impl.login.LoginModeEvent
import io.element.android.features.login.impl.login.LoginModeState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.BipeHandle
import io.element.android.libraries.bipe.BipeHandleAvailabilityChecker
import io.element.android.libraries.bipe.BipeHandleStatus
import io.element.android.libraries.bipe.BipeInviteCode
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiErrorCode
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeSignupRequest
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Port of BipeSignupScreenViewModel on iOS: create the account through the Bipe API,
 * then sign in through MAS with the e-mail as login hint.
 */
@AssistedInject
class BipeSignupPresenter(
    @Assisted private val params: Params,
    private val apiClient: BipeApiClient,
    private val loginModePresenter: Presenter<LoginModeState>,
    private val stringProvider: StringProvider,
) : Presenter<BipeSignupState> {
    data class Params(
        /** From an invite link. */
        val inviteCode: BipeInviteCode?,
    )

    @AssistedFactory
    interface Factory {
        fun create(params: Params): BipeSignupPresenter
    }

    @Composable
    override fun present(): BipeSignupState {
        val coroutineScope = rememberCoroutineScope()
        val checker = remember { BipeHandleAvailabilityChecker(apiClient, coroutineScope) }
        val loginModeState = loginModePresenter.present()

        // Plain remember, not rememberSaveable: the password must not go into the saved instance state.
        var displayName by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordConfirmation by remember { mutableStateOf("") }
        var handle by remember { mutableStateOf("") }
        var inviteCodeInput by remember { mutableStateOf(params.inviteCode?.value.orEmpty()) }
        var handleStatus by remember { mutableStateOf<BipeHandleStatus>(BipeHandleStatus.Empty) }
        var isAccountCreated by remember { mutableStateOf(false) }
        var createdEmail by remember { mutableStateOf<String?>(null) }
        var emailError by remember { mutableStateOf<String?>(null) }
        var passwordError by remember { mutableStateOf<String?>(null) }
        var handleError by remember { mutableStateOf<String?>(null) }
        var inviteCodeError by remember { mutableStateOf<String?>(null) }
        var inviterHandle by remember { mutableStateOf<String?>(null) }
        var createAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        // Validity and the inviter's handle, each time the field holds a whole code.
        val inviteCode = BipeInviteCode.parse(inviteCodeInput)
        LaunchedEffect(inviteCode) {
            inviteCode ?: return@LaunchedEffect
            val preview = try {
                apiClient.invitePreview(inviteCode.value)
            } catch (_: BipeApiException) {
                return@LaunchedEffect
            }
            if (preview.valid) {
                inviterHandle = preview.inviterHandle
            } else {
                inviteCodeError = stringProvider.getString(R.string.bipe_error_invite_invalid)
            }
        }

        /** Field errors stay under their field; anything else is a dialog. */
        fun show(error: BipeApiException) {
            val message = error.localizedMessage(stringProvider)
            createAction = AsyncAction.Uninitialized
            when ((error as? BipeApiException.Server)?.code) {
                BipeApiErrorCode.EMAIL_TAKEN -> emailError = message
                BipeApiErrorCode.PASSWORD_WEAK -> passwordError = message
                BipeApiErrorCode.HANDLE_TAKEN, BipeApiErrorCode.HANDLE_INVALID -> handleError = message
                BipeApiErrorCode.INVITE_INVALID -> inviteCodeError = message
                else -> createAction = AsyncAction.Failure(Exception(message))
            }
        }

        suspend fun createAccount(): Boolean {
            val trimmedEmail = email.trim()
            val sanitizedHandle = BipeHandle.sanitize(handle)
            val trimmedName = displayName.trim()
            val request = BipeSignupRequest(
                inviteCode = BipeInviteCode.parse(inviteCodeInput)?.value,
                email = trimmedEmail,
                password = password,
                handle = sanitizedHandle.ifEmpty { null },
                displayName = trimmedName.ifEmpty { null },
            )
            createAction = AsyncAction.Loading
            return try {
                val response = apiClient.signup(request)
                Timber.i("Bipe sign-up created ${response.userId}")
                createdEmail = trimmedEmail
                isAccountCreated = true
                password = ""
                passwordConfirmation = ""
                createAction = AsyncAction.Uninitialized
                true
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe sign-up failed")
                show(error)
                false
            }
        }

        fun startLogin() {
            loginModeState.eventSink(
                LoginModeEvent.Submit(
                    isAccountCreation = false,
                    homeserverUrl = BipeConfig.HOMESERVER_BASE_URL,
                    resolvedHomeserverUrl = null,
                    loginHint = createdEmail,
                )
            )
        }

        fun handleEvent(event: BipeSignupEvent) {
            when (event) {
                is BipeSignupEvent.UpdateDisplayName -> displayName = event.value
                is BipeSignupEvent.UpdateEmail -> {
                    email = event.value
                    emailError = null
                }
                is BipeSignupEvent.UpdatePassword -> {
                    password = event.value
                    passwordError = null
                }
                is BipeSignupEvent.UpdatePasswordConfirmation -> passwordConfirmation = event.value
                is BipeSignupEvent.UpdateHandle -> {
                    handle = event.value
                    handleError = null
                    checker.check(event.value) { handleStatus = it }
                }
                is BipeSignupEvent.UpdateInviteCode -> {
                    inviteCodeError = null
                    inviterHandle = null
                    // A pasted link or message collapses to the code it holds.
                    inviteCodeInput = BipeInviteCode.parse(event.value)?.value ?: event.value
                }
                BipeSignupEvent.Submit -> coroutineScope.launch {
                    if (!isAccountCreated && !createAccount()) return@launch
                    startLogin()
                }
                BipeSignupEvent.ClearError -> {
                    createAction = AsyncAction.Uninitialized
                    loginModeState.eventSink(LoginModeEvent.ClearError)
                }
            }
        }

        return BipeSignupState(
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
