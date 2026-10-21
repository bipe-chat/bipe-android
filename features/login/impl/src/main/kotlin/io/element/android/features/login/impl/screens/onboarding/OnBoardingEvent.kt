/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.onboarding

import io.element.android.libraries.matrix.api.accountprovider.AccountProvider

sealed interface OnBoardingEvent {
    data class OnSignIn(
        val defaultAccountProvider: AccountProvider
    ) : OnBoardingEvent

    data object OnVersionClick : OnBoardingEvent
    data object ClearError : OnBoardingEvent

    /** Bipe (bipe-chat/bipe#197): the window has focus, so the clipboard description is readable. */
    data object BipeCheckClipboard : OnBoardingEvent

    /** Bipe: the user tapped "Colar convite". */
    data object BipePasteInvite : OnBoardingEvent
}
