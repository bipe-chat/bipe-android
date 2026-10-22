/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.preferences.impl.account.PreferencesAccountState
import io.element.android.features.preferences.impl.account.aPreferencesAccountState
import io.element.android.features.preferences.impl.userstatus.UserStatusPickerState
import io.element.android.features.preferences.impl.userstatus.UserStatusState
import io.element.android.features.preferences.impl.userstatus.aUserStatusState
import io.element.android.libraries.bipe.BadgeLevel
import io.element.android.libraries.bipe.api.BipeBusinessClaim
import io.element.android.libraries.bipe.api.BipeWhoCanMessage
import io.element.android.libraries.bipe.govbr.BipeGovbrFlow
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.aMatrixUser
import io.element.android.libraries.matrix.ui.components.aMatrixUserList
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.toImmutableList

open class PreferencesRootStatePreviewParam : PreviewParameterProvider<PreferencesRootState> {
    override val values: Sequence<PreferencesRootState>
        get() = sequenceOf(
            // Nominal state, that a regular user will see
            aPreferencesRootState(
                myUser = aMatrixUser(avatarUrl = "anAvatarUrl"),
                version = "Version 1.1 (1)",
                otherSessions = aMatrixUserList().drop(1).take(1),
                showAnalyticsSettings = true,
                showLabsItem = true,
            ),
            aPreferencesRootState(
                myUser = aMatrixUser(displayName = null),
            ),
            aPreferencesRootState(
                isOtherAccountsSectionExpanded = true,
                otherSessions = aMatrixUserList().drop(1).take(3),
            ),
            aPreferencesRootState(
                showLabsItem = true,
                snackbarMessage = SnackbarMessage(CommonStrings.common_verification_complete),
            ),
            aPreferencesRootState(
                showAnalyticsSettings = true,
                showDeveloperSettings = true,
            ),
            aPreferencesRootState(userStatusState = aUserStatusState(pickerState = UserStatusPickerState.ShowingPicker)),
            // Minimal state
            aPreferencesRootState(userStatusState = null),
        )
}

fun aPreferencesRootState(
    myUser: MatrixUser = aMatrixUser(),
    preferencesAccountState: PreferencesAccountState = aPreferencesAccountState(),
    version: String = "Version 1.1 (1)",
    isOtherAccountsSectionExpanded: Boolean = false,
    theme: ThemeOption = ThemeOption.System,
    availableThemeOptions: List<ThemeOption> = ThemeOption.entries,
    otherSessions: List<MatrixUser> = emptyList(),
    showAnalyticsSettings: Boolean = false,
    showDeveloperSettings: Boolean = false,
    showLabsItem: Boolean = false,
    userStatusState: UserStatusState? = aUserStatusState(),
    snackbarMessage: SnackbarMessage? = null,
    bipeHandle: String? = null,
    bipePublicProfileEnabled: Boolean? = null,
    bipeIsCompany: Boolean = false,
    bipeBusiness: BipeBusinessClaim? = null,
    bipeWhoCanMessage: BipeWhoCanMessage? = BipeWhoCanMessage.EVERYONE,
    bipeWaysBackCount: Int? = 2,
    bipeNotificationsOn: Boolean? = true,
    bipeDeviceId: String = "ABCDEFGHIJ",
    bipeIsDeveloperBuild: Boolean = false,
    bipePersonBadge: BadgeLevel? = null,
    bipeGovbrEnabled: Boolean = false,
    bipeGovbrFlow: BipeGovbrFlow? = null,
    eventSink: (PreferencesRootEvent) -> Unit = {},
) = PreferencesRootState(
    myUser = myUser,
    preferencesAccountState = preferencesAccountState,
    version = version,
    theme = theme,
    availableThemeOptions = availableThemeOptions.toImmutableList(),
    isOtherAccountsSectionExpanded = isOtherAccountsSectionExpanded,
    otherSessions = otherSessions.toImmutableList(),
    showAnalyticsSettings = showAnalyticsSettings,
    showDeveloperSettings = showDeveloperSettings,
    showLabsItem = showLabsItem,
    userStatusState = userStatusState,
    snackbarMessage = snackbarMessage,
    bipeHandle = bipeHandle,
    bipePublicProfileEnabled = bipePublicProfileEnabled,
    bipeIsCompany = bipeIsCompany,
    bipeBusiness = bipeBusiness,
    bipeWhoCanMessage = bipeWhoCanMessage,
    bipeWaysBackCount = bipeWaysBackCount,
    bipeNotificationsOn = bipeNotificationsOn,
    bipeDeviceId = bipeDeviceId,
    bipeIsDeveloperBuild = bipeIsDeveloperBuild,
    bipePersonBadge = bipePersonBadge,
    bipeGovbrEnabled = bipeGovbrEnabled,
    bipeGovbrFlow = bipeGovbrFlow,
    eventSink = eventSink,
)
