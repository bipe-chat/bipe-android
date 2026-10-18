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
import io.element.android.libraries.bipe.account.ui.BipeNewPinState
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiErrorCode
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeSignupRequest
import io.element.android.libraries.bipe.generated.BipeOnboardingStep
import io.element.android.libraries.bipe.generated.BipeOnboardingTarget
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Device-first sign-up (ADR 0057, port of BipeSignupScreenViewModel on iOS) in the steps of bipe-shared
 * `registries/onboarding.json` (bipe-chat/bipe#415): the invite code (unless a link brought it), the name and terms, the PIN,
 * then the fallback. The @ comes later (bipe-chat/bipe#200). The Bipe API makes the account and a hidden password; the app signs
 * in with it, with no browser. Attestation: none on Android yet (Play Integrity, bipe-chat/bipe#92).
 */
@AssistedInject
class BipeSignupPresenter(
    @Assisted private val params: Params,
    private val apiClient: BipeApiClient,
    private val deviceAccount: BipeDeviceAccount,
    private val stringProvider: StringProvider,
) : Presenter<BipeSignupState> {
    data class Params(
        /** From an invite link. Skips the code step. */
        val inviteCode: BipeInviteCode?,
        /** False when the start screen's button goes straight to the name (open sign-up, ADR 0072). */
        val startsWithInviteCode: Boolean = true,
    )

    @AssistedFactory
    interface Factory {
        fun create(params: Params): BipeSignupPresenter
    }

    @Composable
    override fun present(): BipeSignupState {
        val coroutineScope = rememberCoroutineScope()
        val firstStep = remember {
            if (params.inviteCode == null && params.startsWithInviteCode) BipeOnboardingStep.INVITE_CODE else BipeOnboardingStep.NAME
        }

        // Plain remember, not rememberSaveable: the PIN must not go into the saved instance state.
        var step by remember { mutableStateOf(firstStep) }
        var displayName by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var inviteCodeInput by remember { mutableStateOf(params.inviteCode?.value.orEmpty()) }
        var emailError by remember { mutableStateOf<String?>(null) }
        var inviteCodeError by remember { mutableStateOf<String?>(null) }
        var inviterHandle by remember { mutableStateOf<String?>(null) }
        var isFallbackEmailOpen by remember { mutableStateOf(false) }
        val pinState = remember { BipeNewPinState() }
        var createAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        var isInviteRequired by remember { mutableStateOf(true) }
        var isTermsAccepted by remember { mutableStateOf(false) }
        var isWaitlistOpen by remember { mutableStateOf(false) }
        var waitlistEmail by remember { mutableStateOf("") }
        var waitlistAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        // An error keeps the default: an invite is required (ADR 0072). An open server skips an empty code step.
        LaunchedEffect(Unit) {
            isInviteRequired = try {
                apiClient.signupConfig().inviteRequired
            } catch (_: BipeApiException) {
                true
            }
            if (!isInviteRequired && step == BipeOnboardingStep.INVITE_CODE && inviteCodeInput.isBlank()) {
                step = BipeOnboardingStep.NAME
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

        /** A field error opens the step that holds the field; anything else is a dialog. */
        fun show(error: Throwable) {
            val message = (error as? BipeApiException)?.localizedMessage(stringProvider)
                ?: stringProvider.getString(R.string.bipe_error_generic)
            createAction = AsyncAction.Uninitialized
            when ((error as? BipeApiException.Server)?.code) {
                BipeApiErrorCode.EMAIL_TAKEN -> {
                    emailError = message
                    step = BipeOnboardingStep.FALLBACK
                    isFallbackEmailOpen = true
                }
                BipeApiErrorCode.INVITE_INVALID -> {
                    inviteCodeError = message
                    step = BipeOnboardingStep.INVITE_CODE
                }
                // The server wants a legal version this build does not send (ADR 0113): back to the box, with the message.
                BipeApiErrorCode.TERMS_REQUIRED -> {
                    step = BipeOnboardingStep.NAME
                    createAction = AsyncAction.Failure(Exception(message))
                }
                BipeApiErrorCode.PIN_WEAK -> {
                    pinState.clear()
                    step = BipeOnboardingStep.PIN
                    createAction = AsyncAction.Failure(Exception(message))
                }
                else -> createAction = AsyncAction.Failure(Exception(message))
            }
        }

        fun createAccount() = coroutineScope.launch {
            // The name step does not continue without the tick; this guards any other path to here.
            if (!isTermsAccepted) {
                step = BipeOnboardingStep.NAME
                createAction = AsyncAction.Failure(Exception(stringProvider.getString(R.string.bipe_signup_terms_required)))
                return@launch
            }
            val request = BipeSignupRequest(
                inviteCode = BipeInviteCode.parse(inviteCodeInput)?.value,
                email = email.trim().ifEmpty { null },
                handle = null,
                displayName = displayName.trim().ifEmpty { null },
                termsVersion = BipeRegistries.LEGAL_VERSION,
            )
            createAction = AsyncAction.Loading
            deviceAccount.signUp(request, pinState.pin)
                .onSuccess {
                    // The session store now has the session: the root node opens the app. The view shows "Conta criada".
                    createAction = AsyncAction.Success(Unit)
                }
                .onFailure { error ->
                    Timber.e(error, "Bipe device-first sign-up failed")
                    show(error)
                }
        }

        fun joinWaitlist() = coroutineScope.launch {
            val lang = if (java.util.Locale.getDefault().language == "en") "en" else "pt"
            waitlistAction = AsyncAction.Loading
            waitlistAction = try {
                apiClient.joinWaitlist(waitlistEmail.trim(), lang)
                AsyncAction.Success(Unit)
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe waitlist failed")
                AsyncAction.Failure(Exception(error.localizedMessage(stringProvider)))
            }
        }

        fun primary() {
            when (step) {
                BipeOnboardingStep.INVITE_CODE -> if (BipeInviteCode.parse(inviteCodeInput) != null && inviteCodeError == null) {
                    step = BipeOnboardingStep.NAME
                }
                BipeOnboardingStep.NAME -> if (displayName.isNotBlank() && isTermsAccepted) step = BipeOnboardingStep.PIN
                BipeOnboardingStep.PIN -> if (pinState.isValid) step = BipeOnboardingStep.FALLBACK
                BipeOnboardingStep.FALLBACK -> when {
                    !isFallbackEmailOpen -> isFallbackEmailOpen = true
                    email.isNotBlank() && BipeSignupRules.isPlausibleEmail(email.trim()) -> createAccount()
                }
            }
        }

        fun handleEvent(event: BipeSignupEvent) {
            when (event) {
                is BipeSignupEvent.UpdateDisplayName -> displayName = event.value
                is BipeSignupEvent.UpdateEmail -> {
                    email = event.value
                    emailError = null
                }
                is BipeSignupEvent.SetTermsAccepted -> isTermsAccepted = event.accepted
                is BipeSignupEvent.UpdateInviteCode -> {
                    inviteCodeError = null
                    inviterHandle = null
                    // A pasted link or message collapses to the code it holds.
                    inviteCodeInput = BipeInviteCode.parse(event.value)?.value ?: event.value
                }
                BipeSignupEvent.Primary -> primary()
                is BipeSignupEvent.Text -> when (event.target) {
                    BipeOnboardingTarget.WAITLIST -> isWaitlistOpen = true
                    // Fallback "Agora não": the account without an e-mail.
                    BipeOnboardingTarget.CREATE -> {
                        email = ""
                        isFallbackEmailOpen = false
                        createAccount()
                    }
                    else -> Timber.e("Unexpected sign-up text button: ${event.target}")
                }
                BipeSignupEvent.Back -> if (step != firstStep && step.ordinal > 0) {
                    step = BipeOnboardingStep.entries[step.ordinal - 1]
                }
                BipeSignupEvent.ClearError -> createAction = AsyncAction.Uninitialized
                is BipeSignupEvent.UpdateWaitlistEmail -> {
                    waitlistEmail = event.value
                    if (waitlistAction is AsyncAction.Failure) waitlistAction = AsyncAction.Uninitialized
                }
                BipeSignupEvent.JoinWaitlist -> if (BipeSignupRules.isPlausibleEmail(waitlistEmail.trim())) joinWaitlist()
                BipeSignupEvent.CloseWaitlist -> isWaitlistOpen = false
            }
        }

        return BipeSignupState(
            firstStep = firstStep,
            step = step,
            displayName = displayName,
            email = email,
            inviteCodeInput = inviteCodeInput,
            emailError = emailError,
            inviteCodeError = inviteCodeError,
            inviterHandle = inviterHandle,
            pinState = pinState,
            isFallbackEmailOpen = isFallbackEmailOpen,
            isTokenSynced = deviceAccount.isTokenSynced,
            createAction = createAction,
            isInviteRequired = isInviteRequired,
            isTermsAccepted = isTermsAccepted,
            isWaitlistOpen = isWaitlistOpen,
            waitlistEmail = waitlistEmail,
            waitlistAction = waitlistAction,
            eventSink = ::handleEvent,
        )
    }
}
