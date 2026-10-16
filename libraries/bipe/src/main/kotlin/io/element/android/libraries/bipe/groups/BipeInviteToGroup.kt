/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.groups

import dev.zacsweers.metro.Inject
import io.element.android.libraries.bipe.selfchat.BipeSelfChat
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import kotlinx.coroutines.flow.first
import timber.log.Timber

/** A group the DM recipient can be invited to. */
data class BipeInviteGroup(
    val roomId: RoomId,
    val name: String,
)

/**
 * "Convidar para um grupo" in a DM. A DM never gets more people: the recipient goes to one of your groups instead.
 * iOS: `presentInviteToGroupPicker` in `RoomDetailsScreenViewModel`.
 */
@Inject
class BipeInviteToGroup(
    private val matrixClient: MatrixClient,
    private val selfChat: BipeSelfChat,
) {
    sealed interface Outcome {
        data object Sent : Outcome
        data object AlreadyMember : Outcome
        data object Failed : Outcome
    }

    /** The groups you are joined to, by name. Not DMs, spaces, replaced rooms or the self-chat. */
    suspend fun groups(): List<BipeInviteGroup> {
        val selfChatId = selfChat.roomId.value
        return matrixClient.roomListService.allRooms.summaries.first()
            .map { it.info }
            .filter {
                it.currentUserMembership == CurrentUserMembership.JOINED &&
                    !it.isDm && !it.isSpace && it.successorRoom == null && it.id != selfChatId
            }
            .map { BipeInviteGroup(roomId = it.id, name = it.name ?: it.id.value) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun invite(userId: UserId, groupId: RoomId): Outcome {
        val group = matrixClient.getJoinedRoom(groupId) ?: return Outcome.Failed
        return group.use { room ->
            val canInvite = room.roomPermissions().getOrNull()?.use { it.canOwnUserInvite() } == true
            when {
                !canInvite -> Outcome.Failed
                room.getUpdatedMember(userId).getOrNull()?.membership?.isActive() == true -> Outcome.AlreadyMember
                else -> room.inviteUserById(userId).fold(
                    onSuccess = { Outcome.Sent },
                    onFailure = {
                        Timber.e(it, "Bipe: inviting the DM recipient to $groupId failed")
                        Outcome.Failed
                    },
                )
            }
        }
    }
}
