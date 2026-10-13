/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.bipesignup

import io.element.android.features.login.impl.login.LoginModeState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.BipeHandleStatus
import io.element.android.libraries.bipe.BipeInviteCode
import io.element.android.libraries.bipe.BipeSignupRules

/** Port of BipeSignupScreenViewState on iOS. Never log this class: it holds the password. */
data class BipeSignupState(
    val displayName: String,
    val email: String,
    val password: String,
    val passwordConfirmation: String,
    val handle: String,
    val inviteCodeInput: String,
    val handleStatus: BipeHandleStatus,
    val isAccountCreated: Boolean,
    val emailError: String?,
    val passwordError: String?,
    val handleError: String?,
    val inviteCodeError: String?,
    val inviterHandle: String?,
    /** Account creation request. Failure carries the localised message. */
    val createAction: AsyncAction<Unit>,
    val loginModeState: LoginModeState,
    val eventSink: (BipeSignupEvent) -> Unit,
) {
    val passwordMismatch: Boolean get() = passwordConfirmation.isNotEmpty() && password != passwordConfirmation

    val inviteCode: BipeInviteCode? get() = BipeInviteCode.parse(inviteCodeInput)

    val isInviteCodeAcceptable: Boolean get() = inviteCodeInput.isBlank() || inviteCode != null

    val canSubmit: Boolean
        get() {
            if (isAccountCreated) return true
            val name = displayName.trim()
            return name.isNotEmpty() &&
                name.length <= BipeSignupRules.DISPLAY_NAME_MAX_LENGTH &&
                BipeSignupRules.isPlausibleEmail(email.trim()) &&
                BipeSignupRules.isValidPassword(password) &&
                password == passwordConfirmation &&
                !handleStatus.isBlocking &&
                isInviteCodeAcceptable &&
                inviteCodeError == null
        }

    override fun toString(): String = "BipeSignupState(isAccountCreated=$isAccountCreated)"
}

sealed interface BipeSignupEvent {
    data class UpdateDisplayName(val value: String) : BipeSignupEvent
    data class UpdateEmail(val value: String) : BipeSignupEvent {
        override fun toString() = "UpdateEmail"
    }
    data class UpdatePassword(val value: String) : BipeSignupEvent {
        override fun toString() = "UpdatePassword"
    }
    data class UpdatePasswordConfirmation(val value: String) : BipeSignupEvent {
        override fun toString() = "UpdatePasswordConfirmation"
    }
    data class UpdateHandle(val value: String) : BipeSignupEvent
    data class UpdateInviteCode(val value: String) : BipeSignupEvent
    data object Submit : BipeSignupEvent
    data object ClearError : BipeSignupEvent
}
