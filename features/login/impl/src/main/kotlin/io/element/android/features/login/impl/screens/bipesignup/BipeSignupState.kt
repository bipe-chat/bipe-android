/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.bipesignup

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.BipeInviteCode
import io.element.android.libraries.bipe.BipeSignupRules
import io.element.android.libraries.bipe.account.ui.BipeNewPinState
import io.element.android.libraries.bipe.generated.BipeOnboardingStep
import io.element.android.libraries.bipe.generated.BipeOnboardingTarget

/**
 * Port of BipeSignupScreenViewState on iOS. The steps and their buttons come from bipe-shared `registries/onboarding.json`
 * (bipe-chat/bipe#415). Never log this class: it holds the PIN.
 */
data class BipeSignupState(
    /** The code step shows only when no invite came with a link. */
    val firstStep: BipeOnboardingStep,
    val step: BipeOnboardingStep,
    val displayName: String,
    val email: String,
    val inviteCodeInput: String,
    val emailError: String?,
    val inviteCodeError: String?,
    val inviterHandle: String?,
    val pinState: BipeNewPinState,
    /** The fallback step shows the e-mail field. */
    val isFallbackEmailOpen: Boolean,
    /** `false` in the F-Droid build: the recovery token does not sync with the Google account. */
    val isTokenSynced: Boolean,
    /** Account creation and sign-in. Failure carries the localised message. */
    val createAction: AsyncAction<Unit>,
    /** From `GET /v1/signup/config` (ADR 0072). `true` until the server says otherwise, and after an error. */
    val isInviteRequired: Boolean,
    /** The clickwrap box for the Termos de uso and the Política de privacidade (ADR 0113). Unticked at the start. */
    val isTermsAccepted: Boolean,
    /** "Não tem convite?" sheet. */
    val isWaitlistOpen: Boolean,
    val waitlistEmail: String,
    val waitlistAction: AsyncAction<Unit>,
    val eventSink: (BipeSignupEvent) -> Unit,
) {
    val inviteCode: BipeInviteCode? get() = BipeInviteCode.parse(inviteCodeInput)

    val isInviteCodeAcceptable: Boolean get() = (inviteCode != null || inviteCodeInput.isBlank() && !isInviteRequired) && inviteCodeError == null

    /** A typed code that is not a code. An empty field is not an error. */
    val isInviteCodeMalformed: Boolean get() = inviteCodeInput.isNotBlank() && inviteCode == null

    val canContinueInviteCode: Boolean get() = inviteCode != null && inviteCodeError == null

    val canContinueName: Boolean
        get() {
            val name = displayName.trim()
            return name.isNotEmpty() && name.length <= BipeSignupRules.DISPLAY_NAME_MAX_LENGTH && isTermsAccepted
        }

    /** The fallback step's e-mail: required once the field is open. */
    val canSubmitFallbackEmail: Boolean get() = email.isNotBlank() && BipeSignupRules.isPlausibleEmail(email.trim())

    val canJoinWaitlist: Boolean get() = BipeSignupRules.isPlausibleEmail(waitlistEmail.trim())

    /** One step back, never before the first step. */
    val canGoBack: Boolean get() = step != firstStep && step.ordinal > 0

    override fun toString(): String = "BipeSignupState(step=$step)"
}

sealed interface BipeSignupEvent {
    data class UpdateDisplayName(val value: String) : BipeSignupEvent
    data class UpdateEmail(val value: String) : BipeSignupEvent {
        override fun toString() = "UpdateEmail"
    }
    data class UpdateInviteCode(val value: String) : BipeSignupEvent

    /** The clickwrap box (ADR 0113). */
    data class SetTermsAccepted(val accepted: Boolean) : BipeSignupEvent

    /** The step's filled button: next step, or create the account. */
    data object Primary : BipeSignupEvent

    /** A text button of the step, by its target in the registry. */
    data class Text(val target: BipeOnboardingTarget) : BipeSignupEvent

    /** Back one step. */
    data object Back : BipeSignupEvent
    data object ClearError : BipeSignupEvent

    data class UpdateWaitlistEmail(val value: String) : BipeSignupEvent {
        override fun toString() = "UpdateWaitlistEmail"
    }
    data object JoinWaitlist : BipeSignupEvent
    data object CloseWaitlist : BipeSignupEvent
}
