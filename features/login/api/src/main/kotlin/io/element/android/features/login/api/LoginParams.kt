/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Parameters to start the login flow, when the application is opened
 * from a mobile.element.io link.
 */
@Parcelize
data class LoginParams(
    val accountProvider: String,
    val loginHint: String?,
    // Bipe: normalised code from a bipe.chat/convite/ link. Opens the sign-up screen.
    val bipeInviteCode: String? = null,
    // Bipe: the token from a bipe.chat/recuperar/#<token> e-mail link (ADR 0057). Opens the recovery screen.
    val bipeRecoveryToken: String? = null,
    // Bipe: "Nova conta" (bipe#445): open the sign-up at once, with [bipeInviteCode] from the account on screen when it has one.
    val bipeStartsSignup: Boolean = false,
) : Parcelable {
    // Bipe: the recovery token is a secret; routes and intents get logged.
    override fun toString(): String = "LoginParams(accountProvider=$accountProvider, hasInviteCode=${bipeInviteCode != null}, " +
        "hasRecoveryToken=${bipeRecoveryToken != null}, bipeStartsSignup=$bipeStartsSignup)"
}
