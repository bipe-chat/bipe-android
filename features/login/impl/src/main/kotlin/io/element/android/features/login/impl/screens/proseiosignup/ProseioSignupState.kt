/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.proseiosignup

import io.element.android.features.login.impl.login.LoginModeState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.proseio.ProseioHandleStatus
import io.element.android.libraries.proseio.ProseioInviteCode
import io.element.android.libraries.proseio.ProseioSignupRules

/** Port of ProseioSignupScreenViewState on iOS. Never log this class: it holds the password. */
data class ProseioSignupState(
    val displayName: String,
    val email: String,
    val password: String,
    val passwordConfirmation: String,
    val handle: String,
    val inviteCodeInput: String,
    val handleStatus: ProseioHandleStatus,
    val isAccountCreated: Boolean,
    val emailError: String?,
    val passwordError: String?,
    val handleError: String?,
    val inviteCodeError: String?,
    val inviterHandle: String?,
    /** Account creation request. Failure carries the localised message. */
    val createAction: AsyncAction<Unit>,
    val loginModeState: LoginModeState,
    val eventSink: (ProseioSignupEvent) -> Unit,
) {
    val passwordMismatch: Boolean get() = passwordConfirmation.isNotEmpty() && password != passwordConfirmation

    val inviteCode: ProseioInviteCode? get() = ProseioInviteCode.parse(inviteCodeInput)

    val isInviteCodeAcceptable: Boolean get() = inviteCodeInput.isBlank() || inviteCode != null

    val canSubmit: Boolean
        get() {
            if (isAccountCreated) return true
            val name = displayName.trim()
            return name.isNotEmpty() &&
                name.length <= ProseioSignupRules.DISPLAY_NAME_MAX_LENGTH &&
                ProseioSignupRules.isPlausibleEmail(email.trim()) &&
                ProseioSignupRules.isValidPassword(password) &&
                password == passwordConfirmation &&
                !handleStatus.isBlocking &&
                isInviteCodeAcceptable &&
                inviteCodeError == null
        }

    override fun toString(): String = "ProseioSignupState(isAccountCreated=$isAccountCreated)"
}

sealed interface ProseioSignupEvent {
    data class UpdateDisplayName(val value: String) : ProseioSignupEvent
    data class UpdateEmail(val value: String) : ProseioSignupEvent {
        override fun toString() = "UpdateEmail"
    }
    data class UpdatePassword(val value: String) : ProseioSignupEvent {
        override fun toString() = "UpdatePassword"
    }
    data class UpdatePasswordConfirmation(val value: String) : ProseioSignupEvent {
        override fun toString() = "UpdatePasswordConfirmation"
    }
    data class UpdateHandle(val value: String) : ProseioSignupEvent
    data class UpdateInviteCode(val value: String) : ProseioSignupEvent
    data object Submit : ProseioSignupEvent
    data object ClearError : ProseioSignupEvent
}
