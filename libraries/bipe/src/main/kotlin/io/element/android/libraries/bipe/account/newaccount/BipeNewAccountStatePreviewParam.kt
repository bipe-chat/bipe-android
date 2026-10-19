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
            aBipeNewAccountState(quota = BipeNewAccountQuota.Available),
            aBipeNewAccountState(quota = BipeNewAccountQuota.Loading),
            aBipeNewAccountState(quota = BipeNewAccountQuota.NoneLeft(nextAvailableAt = null)),
            aBipeNewAccountState(canAddPersonalAccount = false, quota = BipeNewAccountQuota.Unknown),
            aBipeNewAccountState(quota = BipeNewAccountQuota.Unknown),
            aBipeNewAccountState(quota = BipeNewAccountQuota.Available, creatingFor = BipeNewAccountKind.PERSONAL),
        )
}

internal fun aBipeNewAccountState(
    canAddPersonalAccount: Boolean? = true,
    quota: BipeNewAccountQuota = BipeNewAccountQuota.Loading,
    creatingFor: BipeNewAccountKind? = null,
) = BipeNewAccountState(
    accountName = "Ana Souza",
    maxPersonalAccounts = 2,
    canAddPersonalAccount = canAddPersonalAccount,
    quota = quota,
    creatingFor = creatingFor,
    signup = null,
    eventSink = {},
)
