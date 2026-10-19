/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.newaccount

import androidx.compose.runtime.Immutable
import java.time.Instant

/** The "Nova conta" window (bipe#445, bipe#446): a personal account, a company account or an account that already exists. */
@Immutable
data class BipeNewAccountState(
    /** The display name of the account on screen. The invite comes from this account. */
    val accountName: String,
    /** The cap of personal accounts on this phone (ADR 0086). */
    val maxPersonalAccounts: Int,
    /** `null` while the window counts the accounts on this phone. */
    val canAddPersonalAccount: Boolean?,
    /** The invite quota of the account on screen, read when the window opens. Nothing is created before a tap. */
    val quota: BipeNewAccountQuota,
    /** The sign-up row whose invite is being created after a tap. */
    val creatingFor: BipeNewAccountKind?,
    /** Set once the invite request ended: the window closes and opens this sign-up. */
    val signup: BipeNewAccountSignup?,
    val eventSink: (BipeNewAccountEvent) -> Unit,
) {
    /**
     * A company account needs an attested device (ADR 0043, ADR 0087). Android has no Play Integrity yet (bipe-chat/bipe#92),
     * so the company row stays hidden. When #92 lands, this is the one line to change.
     */
    val showsCompanyAccount: Boolean get() = false

    /**
     * Sign-up rows: under the cap, and not while the quota loads, when no invite is left or while an invite is being created.
     * A failed quota read keeps them on.
     */
    val canSignUp: Boolean
        get() = canAddPersonalAccount == true &&
            (quota is BipeNewAccountQuota.Available || quota is BipeNewAccountQuota.Unknown) &&
            creatingFor == null

    /** The note "O convite sai da conta …" shows only when the account has an invite to give. */
    val showsInviteNote: Boolean get() = canAddPersonalAccount == true && quota is BipeNewAccountQuota.Available
}

@Immutable
sealed interface BipeNewAccountQuota {
    data object Loading : BipeNewAccountQuota

    data object Available : BipeNewAccountQuota

    data class NoneLeft(val nextAvailableAt: Instant?) : BipeNewAccountQuota

    /** The quota read failed, or the phone is at the cap so it was not read. */
    data object Unknown : BipeNewAccountQuota
}

enum class BipeNewAccountKind {
    PERSONAL,
    COMPANY,
}

/** [inviteCode] `null`: the sign-up shows its invite step, as before. */
data class BipeNewAccountSignup(val kind: BipeNewAccountKind, val inviteCode: String?) {
    /** Never prints the code. */
    override fun toString(): String = "BipeNewAccountSignup($kind, hasInviteCode=${inviteCode != null})"
}

sealed interface BipeNewAccountEvent {
    /** A tap on a sign-up row: creates one invite, then sets [BipeNewAccountState.signup]. */
    data class SignUp(val kind: BipeNewAccountKind) : BipeNewAccountEvent
}
