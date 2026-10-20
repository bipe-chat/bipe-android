/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.onboarding

import androidx.annotation.DrawableRes
import io.element.android.features.login.impl.login.LoginModeState
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.bipe.generated.BipeOnboardingStart
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider

data class OnBoardingState(
    val isAddingAccount: Boolean,
    val showBackButton: Boolean,
    val showDeveloperSettings: Boolean,
    val productionApplicationName: String,
    val defaultAccountProvider: AccountProvider?,
    val mustChooseAccountProvider: Boolean,
    val canLoginWithQrCode: Boolean,
    val canCreateAccount: Boolean,
    val canReportBug: Boolean,
    val version: String,
    @DrawableRes
    val onBoardingLogoResId: Int?,
    val loginModeState: LoginModeState,
    /** Bipe: the start screen variant from bipe-shared `registries/onboarding.json` (bipe-chat/bipe#415). Null outside Bipe sign-up. */
    val bipeStart: BipeOnboardingStart?,
    /** Bipe: the inviter's handle from the invite preview, without @. */
    val bipeInviterHandle: String?,
    /** Bipe: the pending invite, from a link, the Play Install Referrer or the clipboard (bipe-chat/bipe#197). Sign-up skips the code step. */
    val bipeInviteCode: String? = null,
    /** Bipe: the clipboard holds text, so the start screen offers "Colar convite" (bipe-chat/bipe#197). */
    val bipeOffersPasteInvite: Boolean = false,
    /** Bipe: the pasted text holds no invite code. */
    val bipePastedNoInvite: Boolean = false,
    val eventSink: (OnBoardingEvent) -> Unit,
) {
    val submitEnabled: Boolean
        get() = defaultAccountProvider != null &&
            (loginModeState.loginMode is AsyncData.Uninitialized || loginModeState.loginMode is AsyncData.Loading)
}
