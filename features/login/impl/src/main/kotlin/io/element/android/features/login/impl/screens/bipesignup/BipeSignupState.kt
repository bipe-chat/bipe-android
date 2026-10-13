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

/**
 * One question per screen (ADR 0057, bipe-chat/bipe#196, port of BipeSignupStep on iOS). The @ and the recovery e-mail
 * come later (bipe-chat/bipe#200, bipe-chat/bipe#202).
 */
enum class BipeSignupStep {
    /** Only without a code from a link. Sign-up is invite-only (bipe ADR 0072). */
    INVITE,
    NAME,

    /** The 8-digit PIN. A whole, acceptable PIN moves on by itself. */
    PIN,

    /** The same PIN again. A match creates the account. */
    PIN_CONFIRMATION,
}

/** Port of BipeSignupScreenViewState on iOS. Never log this class: it holds the PIN. */
data class BipeSignupState(
    val step: BipeSignupStep,
    /** [BipeSignupStep.INVITE] without a code from a link, else [BipeSignupStep.NAME]. */
    val firstStep: BipeSignupStep,
    val displayName: String,
    val inviteCodeInput: String,
    val inviteCodeError: String?,
    val inviterHandle: String?,
    /** "Não tenho convite" was tapped. */
    val showsNoInviteHelp: Boolean,
    val pinState: BipeNewPinState,
    /** Shown on the PIN step: a weak PIN from the server. */
    val pinError: String?,
    /** The second PIN did not match the first. */
    val pinMismatch: Boolean,
    /** Account creation and sign-in. Failure carries the localised message. */
    val createAction: AsyncAction<Unit>,
    val eventSink: (BipeSignupEvent) -> Unit,
) {
    val inviteCode: BipeInviteCode? get() = BipeInviteCode.parse(inviteCodeInput)

    val inviteCodeFooterIsInvalid: Boolean get() = inviteCodeError != null || (inviteCodeInput.isNotBlank() && inviteCode == null)

    val canContinueFromInvite: Boolean get() = inviteCode != null && inviteCodeError == null

    val canContinueFromName: Boolean
        get() {
            val name = displayName.trim()
            return name.isNotEmpty() && name.length <= BipeSignupRules.DISPLAY_NAME_MAX_LENGTH
        }

    /** The back button opens the step before, or leaves the screen on the first step. */
    val hasPreviousStep: Boolean get() = step.ordinal > firstStep.ordinal

    override fun toString(): String = "BipeSignupState(step=$step)"
}

sealed interface BipeSignupEvent {
    data class UpdateDisplayName(val value: String) : BipeSignupEvent
    data class UpdateInviteCode(val value: String) : BipeSignupEvent
    data class UpdatePin(val value: String) : BipeSignupEvent {
        override fun toString() = "UpdatePin"
    }
    data class UpdatePinConfirmation(val value: String) : BipeSignupEvent {
        override fun toString() = "UpdatePinConfirmation"
    }

    data object ContinueFromInvite : BipeSignupEvent
    data object ShowNoInviteHelp : BipeSignupEvent
    data object ContinueFromName : BipeSignupEvent

    /** Back one step. */
    data object Back : BipeSignupEvent
    data object ClearError : BipeSignupEvent
}
