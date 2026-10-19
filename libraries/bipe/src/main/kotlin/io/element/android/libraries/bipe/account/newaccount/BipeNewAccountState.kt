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
    val invite: BipeNewAccountInvite,
) {
    /**
     * A company account needs an attested device (ADR 0043, ADR 0087). Android has no Play Integrity yet (bipe-chat/bipe#92),
     * so the company row stays hidden. When #92 lands, this is the one line to change.
     */
    val showsCompanyAccount: Boolean get() = false

    /** Sign-up rows: under the cap, and not while the invite loads or when no invite is left. A failed invite keeps them on. */
    val canSignUp: Boolean
        get() = canAddPersonalAccount == true && invite !is BipeNewAccountInvite.Loading && invite !is BipeNewAccountInvite.NoneLeft

    /** The code for the sign-up, which then starts at the name step. `null` shows the invite step, as before. */
    val inviteCode: String? get() = (invite as? BipeNewAccountInvite.Created)?.code
}

@Immutable
sealed interface BipeNewAccountInvite {
    data object Loading : BipeNewAccountInvite

    data class Created(val code: String) : BipeNewAccountInvite {
        /** Never prints the code. */
        override fun toString(): String = "Created"
    }

    data class NoneLeft(val nextAvailableAt: Instant?) : BipeNewAccountInvite

    /** The request failed, or the phone is at the cap so no invite was made. */
    data object NotCreated : BipeNewAccountInvite
}
