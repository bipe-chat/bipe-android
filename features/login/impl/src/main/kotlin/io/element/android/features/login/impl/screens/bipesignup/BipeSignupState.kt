/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.bipesignup

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.BipeHandleStatus
import io.element.android.libraries.bipe.BipeInviteCode
import io.element.android.libraries.bipe.BipeSignupRules
import io.element.android.libraries.bipe.account.ui.BipeNewPinState

/** Steps of the device-first sign-up (ADR 0057). */
enum class BipeSignupStep {
    /** Name, optional @, invite code, optional e-mail. */
    FORM,

    /** The 8-digit PIN, typed twice. */
    PIN,

    /** No e-mail given: "If you lose your phone", with "Add an email" and "Not now". */
    FALLBACK,
}

/** Port of BipeSignupScreenViewState on iOS. Never log this class: it holds the PIN. */
data class BipeSignupState(
    val step: BipeSignupStep,
    val displayName: String,
    val email: String,
    val handle: String,
    val inviteCodeInput: String,
    val handleStatus: BipeHandleStatus,
    val emailError: String?,
    val handleError: String?,
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
    val eventSink: (BipeSignupEvent) -> Unit,
) {
    val inviteCode: BipeInviteCode? get() = BipeInviteCode.parse(inviteCodeInput)

    val isInviteCodeAcceptable: Boolean get() = inviteCode != null || (inviteCodeInput.isBlank() && !isInviteRequired)

    /** A typed code that is not a code. An empty field is not an error, even when an invite is required. */
    val isInviteCodeMalformed: Boolean get() = inviteCodeInput.isNotBlank() && inviteCode == null

    /** Blank, or a plausible address. */
    val isEmailAcceptable: Boolean get() = email.isBlank() || BipeSignupRules.isPlausibleEmail(email.trim())

    val canContinueForm: Boolean
        get() {
            val name = displayName.trim()
            return name.isNotEmpty() &&
                name.length <= BipeSignupRules.DISPLAY_NAME_MAX_LENGTH &&
                isEmailAcceptable &&
                !handleStatus.isBlocking &&
                isInviteCodeAcceptable &&
                inviteCodeError == null
        }

    override fun toString(): String = "BipeSignupState(step=$step)"
}

sealed interface BipeSignupEvent {
    data class UpdateDisplayName(val value: String) : BipeSignupEvent
    data class UpdateEmail(val value: String) : BipeSignupEvent {
        override fun toString() = "UpdateEmail"
    }
    data class UpdateHandle(val value: String) : BipeSignupEvent
    data class UpdateInviteCode(val value: String) : BipeSignupEvent

    /** Next step, or create the account on the last one. */
    data object Continue : BipeSignupEvent

    /** Fallback step: show the e-mail field. */
    data object OpenFallbackEmail : BipeSignupEvent

    /** Fallback step: "Not now". Creates the account without an e-mail. */
    data object SkipFallback : BipeSignupEvent

    /** Back one step. */
    data object Back : BipeSignupEvent
    data object ClearError : BipeSignupEvent
}
