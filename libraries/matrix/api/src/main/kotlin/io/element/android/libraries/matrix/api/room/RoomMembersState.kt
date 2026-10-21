/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.room

import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList

@Immutable
sealed interface RoomMembersState {
    data object Unknown : RoomMembersState
    data class Pending(val prevRoomMembers: ImmutableList<RoomMember>? = null) : RoomMembersState
    data class Error(val failure: Throwable, val prevRoomMembers: ImmutableList<RoomMember>? = null) : RoomMembersState
    data class Ready(val roomMembers: ImmutableList<RoomMember>) : RoomMembersState
}

fun RoomMembersState.roomMembers(): List<RoomMember>? {
    return when (this) {
        is RoomMembersState.Ready -> roomMembers
        is RoomMembersState.Pending -> prevRoomMembers
        is RoomMembersState.Error -> prevRoomMembers
        else -> null
    }
}

fun RoomMembersState.joinedRoomMembers(): List<RoomMember> {
    return roomMembers().orEmpty().filter { it.membership == RoomMembershipState.JOIN }
}

fun RoomMembersState.activeRoomMembers(): List<RoomMember> {
    return roomMembers().orEmpty().filter { it.membership.isActive() }
}

fun RoomMembersState.getDirectRoomMember(roomInfo: RoomInfo, sessionId: SessionId): RoomMember? {
    return roomMembers()
        ?.takeIf { roomInfo.isDm }
        ?.find { !it.isServiceMember && it.userId != sessionId && it.membership.isActive() }
}

/**
 * Bipe (bipe#524): the other person of a DM, also after they left. The SDK `isDm` decides if the room is a DM.
 * An active member comes first, then the last known member (left or banned), then a hero of the room summary.
 * Returns null when the room is not a DM, or when nothing about the other person is known yet.
 */
fun RoomMembersState.bipeDmOtherMember(roomInfo: RoomInfo, sessionId: SessionId): RoomMember? {
    if (!roomInfo.isDm) return null
    val others = roomMembers().orEmpty().filter { !it.isServiceMember && it.userId != sessionId }
    return others.firstOrNull { it.membership.isActive() }
        ?: others.firstOrNull()
        ?: roomInfo.heroes.firstOrNull { it.userId != sessionId }?.bipeToLeftRoomMember()
}

/** Bipe (bipe#524): a hero with no loaded member event. The membership is not known, so it is LEAVE, the case this fallback is for. */
private fun MatrixUser.bipeToLeftRoomMember() = RoomMember(
    userId = userId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    membership = RoomMembershipState.LEAVE,
    isNameAmbiguous = false,
    powerLevel = 0L,
    isIgnored = false,
    role = RoomMember.Role.User,
    membershipChangeReason = null,
    isServiceMember = false,
    displayedStatus = displayedStatus,
)
