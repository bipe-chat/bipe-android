/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.leaveroom.impl

import io.element.android.features.leaveroom.api.LeaveRoomEvent
import io.element.android.libraries.matrix.api.core.RoomId

sealed interface InternalLeaveRoomEvent : LeaveRoomEvent {
    data object ResetState : InternalLeaveRoomEvent

    /** Bipe (bipe#464): "Apagar conversa" in a DM. The user stays in the room. */
    data class BipeClearDm(val roomId: RoomId) : InternalLeaveRoomEvent
}
