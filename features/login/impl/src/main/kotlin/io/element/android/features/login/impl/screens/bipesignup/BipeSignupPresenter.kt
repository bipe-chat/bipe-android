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
import io.element.android.libraries.bipe.BipeInviteCode
import io.element.android.libraries.bipe.BipeSignupRules
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipeDeviceAccount
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.bipe.account.ui.BipeNewPinState
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiErrorCode
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeSignupRequest
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Device-first sign-up (ADR 0057, port of BipeSignupScreenViewModel on iOS), one question per screen (bipe-chat/bipe#196):
 * the invite (bipe ADR 0072), the name, the PIN, the PIN again. The Bipe API makes the account and a hidden password;
 * the app signs in with it, with no browser. Attestation: none on Android yet (Play Integrity, bipe-chat/bipe#92).
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

        // Plain remember, not rememberSaveable: the PIN must not go into the saved instance state.
        var firstStep by remember { mutableStateOf(if (params.inviteCode == null) BipeSignupStep.INVITE else BipeSignupStep.NAME) }
        var step by remember { mutableStateOf(firstStep) }
        var displayName by remember { mutableStateOf("") }
        var inviteCodeInput by remember { mutableStateOf(params.inviteCode?.value.orEmpty()) }
        var inviteCodeError by remember { mutableStateOf<String?>(null) }
        var inviterHandle by remember { mutableStateOf<String?>(null) }
        var showsNoInviteHelp by remember { mutableStateOf(false) }
        val pinState = remember { BipeNewPinState() }
        var pinError by remember { mutableStateOf<String?>(null) }
        var pinMismatch by remember { mutableStateOf(false) }
        var createAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        fun clearPins() {
            pinState.clear()
            pinMismatch = false
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
                // A dead code from a link: ask for another one before the name.
                if (createAction !is AsyncAction.Loading) {
                    clearPins()
                    firstStep = BipeSignupStep.INVITE
                    step = BipeSignupStep.INVITE
                }
            }
        }

        /** A weak PIN goes back to the PIN step, a dead invite to the invite step; anything else is a dialog. */
        fun show(error: Throwable) {
            val message = (error as? BipeApiException)?.localizedMessage(stringProvider)
                ?: stringProvider.getString(R.string.bipe_error_generic)
            createAction = AsyncAction.Uninitialized
            when ((error as? BipeApiException.Server)?.code) {
                BipeApiErrorCode.INVITE_INVALID -> {
                    clearPins()
                    inviteCodeError = message
                    firstStep = BipeSignupStep.INVITE
                    step = BipeSignupStep.INVITE
                }
                BipeApiErrorCode.PIN_WEAK -> {
                    clearPins()
                    pinError = message
                    step = BipeSignupStep.PIN
                }
                else -> {
                    // Typing the second PIN again tries again.
                    pinState.confirmation = ""
                    createAction = AsyncAction.Failure(Exception(message))
                }
            }
        }

        fun createAccount() = coroutineScope.launch {
            val request = BipeSignupRequest(
                inviteCode = BipeInviteCode.parse(inviteCodeInput)?.value,
                displayName = displayName.trim().ifEmpty { null },
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
                is BipeSignupEvent.UpdateInviteCode -> {
                    inviteCodeError = null
                    inviterHandle = null
                    // A pasted link or message collapses to the code it holds.
                    inviteCodeInput = BipeInviteCode.parse(event.value)?.value ?: event.value
                }
                BipeSignupEvent.ContinueFromInvite -> {
                    if (step == BipeSignupStep.INVITE && inviteCode != null && inviteCodeError == null) step = BipeSignupStep.NAME
                }
                BipeSignupEvent.ShowNoInviteHelp -> showsNoInviteHelp = true
                BipeSignupEvent.ContinueFromName -> {
                    val name = displayName.trim()
                    if (step == BipeSignupStep.NAME && name.isNotEmpty() && name.length <= BipeSignupRules.DISPLAY_NAME_MAX_LENGTH) {
                        clearPins()
                        step = BipeSignupStep.PIN
                    }
                }
                is BipeSignupEvent.UpdatePin -> {
                    pinState.pin = BipePin.sanitizeInput(event.value)
                    if (pinState.pin.isNotEmpty()) pinError = null
                    // A whole, acceptable PIN moves on by itself, like a bank app.
                    if (step == BipeSignupStep.PIN && pinState.pin.length == BipePin.LENGTH && BipePin.problem(pinState.pin) == null) {
                        step = BipeSignupStep.PIN_CONFIRMATION
                    }
                }
                is BipeSignupEvent.UpdatePinConfirmation -> {
                    val confirmation = BipePin.sanitizeInput(event.value)
                    pinState.confirmation = confirmation
                    if (confirmation.isNotEmpty()) pinMismatch = false
                    if (step == BipeSignupStep.PIN_CONFIRMATION && confirmation.length == BipePin.LENGTH && createAction !is AsyncAction.Loading) {
                        if (pinState.isValid) {
                            createAccount()
                        } else {
                            pinMismatch = true
                            pinState.confirmation = ""
                        }
                    }
                }
                BipeSignupEvent.Back -> {
                    if (step.ordinal > firstStep.ordinal) {
                        val previous = BipeSignupStep.entries[step.ordinal - 1]
                        if (previous == BipeSignupStep.PIN) clearPins()
                        step = previous
                    }
                }
                BipeSignupEvent.ClearError -> createAction = AsyncAction.Uninitialized
            }
        }

        return BipeSignupState(
            step = step,
            firstStep = firstStep,
            displayName = displayName,
            inviteCodeInput = inviteCodeInput,
            inviteCodeError = inviteCodeError,
            inviterHandle = inviterHandle,
            showsNoInviteHelp = showsNoInviteHelp,
            pinState = pinState,
            pinError = pinError,
            pinMismatch = pinMismatch,
            createAction = createAction,
            eventSink = ::handleEvent,
        )
    }
}
