/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom

interface HomeEntryPoint : FeatureEntryPoint {
    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun navigateToRoom(roomId: RoomId, eventId: EventId?, joinedRoom: JoinedRoom?)
        fun navigateToCreateRoom()

        /** Bipe: opens the create room flow from the Grupos empty state (ADR 0053). */
        fun bipeNavigateToCreateGroup() = navigateToCreateRoom()
        fun navigateToCreateSpace()
        fun navigateToSettings()
        fun navigateToSetUpRecovery()
        fun navigateToEnterRecoveryKey()
        fun navigateToRoomSettings(roomId: RoomId)
        fun navigateToBugReport()
        fun navigateToAddAccount()

        /** Bipe: "Nova conta" (bipe#445): the sign-up, with an invite code from the account on screen, or `null` for the invite step. */
        fun bipeNavigateToSignup(inviteCode: String?)

        /** Bipe: opens a settings screen from a row of the Você tab (ADR 0053). */
        fun bipeNavigateToSettings(initialTarget: PreferencesEntryPoint.InitialTarget) = navigateToSettings()

        /** Bipe: the "link new device" row of the Você tab (ADR 0053). */
        fun bipeNavigateToLinkNewDevice() {}
    }
}
