/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.biperecovery

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.account.BipePendingClaim
import io.element.android.libraries.bipe.account.ui.BipeNewPinState

enum class BipeRecoveryMode {
    /** The ways back: synced token and PIN, QR code from the old phone, e-mail link. */
    CHOOSE,

    /** E-mail link opened: the PIN. */
    LINK_PIN,

    /** E-mail link opened, PIN forgotten: a new PIN. */
    LINK_NEW_PIN,

    /** The delay of an e-mail recovery runs. The app polls the claim. */
    WAITING,
}

/** Never log this class: it holds the PIN. */
data class BipeRecoveryScreenState(
    val mode: BipeRecoveryMode,
    /** The synced store has a recovery token (ADR 0059). */
    val hasToken: Boolean,
    val pin: String,
    val newPinState: BipeNewPinState,
    val email: String,
    val isEmailOpen: Boolean,
    val isEmailSent: Boolean,
    val pendingClaim: BipePendingClaim?,
    /** A result to show on the choose screen, e.g. "cancelled on one of your devices". */
    val notice: String?,
    /** Under the PIN field: wrong PIN with tries left, blocked PIN. */
    val pinError: String?,
    val action: AsyncAction<Unit>,
    val eventSink: (BipeRecoveryEvent) -> Unit,
) {
    override fun toString(): String = "BipeRecoveryScreenState(mode=$mode, hasToken=$hasToken)"
}

sealed interface BipeRecoveryEvent {
    data class UpdatePin(val value: String) : BipeRecoveryEvent {
        override fun toString() = "UpdatePin"
    }
    data class UpdateEmail(val value: String) : BipeRecoveryEvent {
        override fun toString() = "UpdateEmail"
    }

    /** The synced token and the PIN. */
    data object SubmitTokenPin : BipeRecoveryEvent
    data object OpenEmail : BipeRecoveryEvent
    data object SendEmail : BipeRecoveryEvent

    /** The e-mail link and the PIN. */
    data object SubmitLinkPin : BipeRecoveryEvent

    /** "I forgot my PIN" on the link screen. */
    data object ForgotPin : BipeRecoveryEvent

    /** The e-mail link and a new PIN. */
    data object SubmitNewPin : BipeRecoveryEvent
    data object ClearError : BipeRecoveryEvent
}
