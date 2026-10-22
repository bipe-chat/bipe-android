/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import io.element.android.libraries.matrix.api.core.SessionId

sealed interface PreferencesRootEvent {
    data object OnVersionInfoClick : PreferencesRootEvent
    data class SwitchToSession(val sessionId: SessionId) : PreferencesRootEvent
    data object ToggleOtherAccountsExpanded : PreferencesRootEvent
    data class SetTheme(val theme: ThemeOption) : PreferencesRootEvent

    // Bipe: open and close the "Verificar com gov.br" sheet (bipe#545).
    data object BipeOpenGovbr : PreferencesRootEvent
    data object BipeCloseGovbr : PreferencesRootEvent
}
