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
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.BipeHandle
import io.element.android.libraries.bipe.BipeHandleAvailabilityChecker
import io.element.android.libraries.bipe.BipeHandleStatus
import io.element.android.libraries.bipe.BipeInviteCode
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipeDeviceAccount
import io.element.android.libraries.bipe.account.ui.BipeNewPinState
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiErrorCode
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeSignupRequest
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Device-first sign-up (ADR 0057, port of BipeSignupScreenViewModel on iOS): name, optional @, invite code, optional e-mail,
 * then the PIN. The Bipe API makes the account and a hidden password; the app signs in with it, with no browser.
 * Attestation: none on Android yet (Play Integrity, bipe-chat/bipe#92).
 */
@AssistedInject
class BipeSignupPresenter(
    @Assisted private val params: Params,
    private val apiClient: BipeApiClient,
    private val deviceAccount: BipeDeviceAccount,
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

        // Plain remember, not rememberSaveable: the PIN must not go into the saved instance state.
        var step by remember { mutableStateOf(BipeSignupStep.FORM) }
        var displayName by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var handle by remember { mutableStateOf("") }
        var inviteCodeInput by remember { mutableStateOf(params.inviteCode?.value.orEmpty()) }
        var handleStatus by remember { mutableStateOf<BipeHandleStatus>(BipeHandleStatus.Empty) }
        var emailError by remember { mutableStateOf<String?>(null) }
        var handleError by remember { mutableStateOf<String?>(null) }
        var inviteCodeError by remember { mutableStateOf<String?>(null) }
        var inviterHandle by remember { mutableStateOf<String?>(null) }
        var isFallbackEmailOpen by remember { mutableStateOf(false) }
        val pinState = remember { BipeNewPinState() }
        var createAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        var isInviteRequired by remember { mutableStateOf(true) }
        var isTermsAccepted by remember { mutableStateOf(false) }

        // An error keeps the default: an invite is required (ADR 0072).
        LaunchedEffect(Unit) {
            isInviteRequired = try {
                apiClient.signupConfig().inviteRequired
            } catch (_: BipeApiException) {
                true
            }
        }

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

        /** Field errors go back to the form, under their field; anything else is a dialog. */
        fun show(error: Throwable) {
            val message = (error as? BipeApiException)?.localizedMessage(stringProvider)
                ?: stringProvider.getString(R.string.bipe_error_generic)
            createAction = AsyncAction.Uninitialized
            when ((error as? BipeApiException.Server)?.code) {
                BipeApiErrorCode.EMAIL_TAKEN -> {
                    emailError = message
                    step = BipeSignupStep.FORM
                }
                BipeApiErrorCode.HANDLE_TAKEN, BipeApiErrorCode.HANDLE_INVALID -> {
                    handleError = message
                    step = BipeSignupStep.FORM
                }
                BipeApiErrorCode.INVITE_INVALID -> {
                    inviteCodeError = message
                    step = BipeSignupStep.FORM
                }
                // The server wants a legal version this build does not send (ADR 0113): back to the box, with the message.
                BipeApiErrorCode.TERMS_REQUIRED -> {
                    step = BipeSignupStep.FORM
                    createAction = AsyncAction.Failure(Exception(message))
                }
                BipeApiErrorCode.PIN_WEAK -> {
                    pinState.clear()
                    step = BipeSignupStep.PIN
                    createAction = AsyncAction.Failure(Exception(message))
                }
                else -> createAction = AsyncAction.Failure(Exception(message))
            }
        }

        fun createAccount() = coroutineScope.launch {
            // The form step does not continue without the tick; this guards any other path to here.
            if (!isTermsAccepted) {
                step = BipeSignupStep.FORM
                createAction = AsyncAction.Failure(Exception(stringProvider.getString(R.string.bipe_signup_terms_required)))
                return@launch
            }
            val request = BipeSignupRequest(
                inviteCode = BipeInviteCode.parse(inviteCodeInput)?.value,
                email = email.trim().ifEmpty { null },
                handle = BipeHandle.sanitize(handle).ifEmpty { null },
                displayName = displayName.trim().ifEmpty { null },
                termsVersion = BipeRegistries.LEGAL_VERSION,
            )
            createAction = AsyncAction.Loading
            deviceAccount.signUp(request, pinState.pin)
                .onSuccess {
                    // The session store now has the session: the root node opens the app.
                    createAction = AsyncAction.Uninitialized
                }
                .onFailure { error ->
                    Timber.e(error, "Bipe device-first sign-up failed")
                    show(error)
                }
        }

        fun handleEvent(event: BipeSignupEvent) {
            when (event) {
                is BipeSignupEvent.UpdateDisplayName -> displayName = event.value
                is BipeSignupEvent.UpdateEmail -> {
                    email = event.value
                    emailError = null
                }
                is BipeSignupEvent.UpdateHandle -> {
                    handle = event.value
                    handleError = null
                    checker.check(event.value) { handleStatus = it }
                }
                is BipeSignupEvent.SetTermsAccepted -> isTermsAccepted = event.accepted
                is BipeSignupEvent.UpdateInviteCode -> {
                    inviteCodeError = null
                    inviterHandle = null
                    // A pasted link or message collapses to the code it holds.
                    inviteCodeInput = BipeInviteCode.parse(event.value)?.value ?: event.value
                }
                BipeSignupEvent.Continue -> when (step) {
                    BipeSignupStep.FORM -> step = BipeSignupStep.PIN
                    BipeSignupStep.PIN -> {
                        if (pinState.isValid) {
                            if (email.isBlank()) {
                                step = BipeSignupStep.FALLBACK
                            } else {
                                createAccount()
                            }
                        }
                    }
                    BipeSignupStep.FALLBACK -> {
                        createAccount()
                    }
                }
                BipeSignupEvent.OpenFallbackEmail -> isFallbackEmailOpen = true
                BipeSignupEvent.SkipFallback -> {
                    email = ""
                    isFallbackEmailOpen = false
                    createAccount()
                }
                BipeSignupEvent.Back -> step = when (step) {
                    BipeSignupStep.FORM, BipeSignupStep.PIN -> BipeSignupStep.FORM
                    BipeSignupStep.FALLBACK -> BipeSignupStep.PIN
                }
                BipeSignupEvent.ClearError -> createAction = AsyncAction.Uninitialized
            }
        }

        return BipeSignupState(
            step = step,
            displayName = displayName,
            email = email,
            handle = handle,
            inviteCodeInput = inviteCodeInput,
            handleStatus = handleStatus,
            emailError = emailError,
            handleError = handleError,
            inviteCodeError = inviteCodeError,
            inviterHandle = inviterHandle,
            pinState = pinState,
            isFallbackEmailOpen = isFallbackEmailOpen,
            isTokenSynced = deviceAccount.isTokenSynced,
            createAction = createAction,
            isInviteRequired = isInviteRequired,
            isTermsAccepted = isTermsAccepted,
            eventSink = ::handleEvent,
        )
    }
}
