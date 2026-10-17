/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.api

import android.os.Parcelable
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.parcelize.Parcelize

interface PreferencesEntryPoint : FeatureEntryPoint {
    sealed interface InitialTarget : Parcelable {
        @Parcelize
        data object Root : InitialTarget

        @Parcelize
        data object NotificationSettings : InitialTarget

        @Parcelize
        data object NotificationTroubleshoot : InitialTarget

        @Parcelize
        data object DeveloperSettings : InitialTarget

        /** Bipe: a settings screen opened from a row of the Você tab (ADR 0053). */
        @Parcelize
        data class BipeScreen(val screen: BipeSettingsScreen) : InitialTarget

        /** Bipe: the edit profile screen opened from the Você tab (ADR 0053). */
        @Parcelize
        data class BipeEditProfile(val matrixUser: MatrixUser) : InitialTarget
    }

    /** Bipe: the settings screens that the Você tab rows open (ADR 0053). Old values map to the ADR 0112 screens. */
    enum class BipeSettingsScreen {
        // ADR 0112: Seu perfil and the groups under "Configurações".
        Profile,
        Privacy,
        Account,
        Chats,
        Help,

        Analytics,
        About,
        DeveloperSettings,
        LockScreen,
        Media,
        Location,
        Labs,
        ModerationAndSafety,
        Notifications,
        SignOut,
        AccountDeactivation,
        Handle,
        Invites,
        PublicProfile,
    }

    data class Params(val initialElement: InitialTarget) : NodeInputs

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun navigateToAddAccount()
        fun navigateToLinkNewDevice()
        fun navigateToBugReport()
        fun navigateToSecureBackup()
        fun navigateToRoomNotificationSettings(roomId: RoomId)
        fun navigateToEvent(roomId: RoomId, eventId: EventId)

        /** Bipe: another user's profile, e.g. someone who joined through the user's invite. */
        fun navigateToUserProfile(userId: UserId) {}
    }

    fun createAppDeveloperSettingsNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: DeveloperSettingsCallback,
    ): Node

    interface DeveloperSettingsCallback : Plugin {
        fun onDone()
    }
}
