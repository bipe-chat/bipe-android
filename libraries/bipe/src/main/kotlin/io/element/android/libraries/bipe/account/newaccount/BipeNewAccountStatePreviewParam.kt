/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.newaccount

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

open class BipeNewAccountStatePreviewParam : PreviewParameterProvider<BipeNewAccountState> {
    override val values: Sequence<BipeNewAccountState>
        get() = sequenceOf(
            aBipeNewAccountState(invite = BipeNewAccountInvite.Created(code = "ABCD1234")),
            aBipeNewAccountState(invite = BipeNewAccountInvite.Loading),
            aBipeNewAccountState(invite = BipeNewAccountInvite.NoneLeft(nextAvailableAt = null)),
            aBipeNewAccountState(canAddPersonalAccount = false, invite = BipeNewAccountInvite.NotCreated),
            aBipeNewAccountState(invite = BipeNewAccountInvite.NotCreated),
        )
}

internal fun aBipeNewAccountState(
    canAddPersonalAccount: Boolean? = true,
    invite: BipeNewAccountInvite = BipeNewAccountInvite.Loading,
) = BipeNewAccountState(
    accountName = "Ana Souza",
    maxPersonalAccounts = 2,
    canAddPersonalAccount = canAddPersonalAccount,
    invite = invite,
)
