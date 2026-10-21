/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.leaveroom.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Inject
import io.element.android.features.leaveroom.api.LeaveRoomEvent
import io.element.android.features.leaveroom.api.LeaveRoomState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.bipe.clearedchats.BipeClearedChats
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.powerlevels.usersWithRole
import io.element.android.libraries.push.api.notifications.NotificationCleaner
import io.element.android.libraries.push.api.notifications.conversations.NotificationConversationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@Inject
class LeaveRoomPresenter(
    private val client: MatrixClient,
    private val dispatchers: CoroutineDispatchers,
    private val notificationConversationService: NotificationConversationService,
    private val bipeClearedChats: BipeClearedChats,
    private val notificationCleaner: NotificationCleaner,
) : Presenter<LeaveRoomState> {
    @Composable
    override fun present(): LeaveRoomState {
        val scope = rememberCoroutineScope()
        val leaveAction = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        val bipeClearAction = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        return InternalLeaveRoomState(
            leaveAction = leaveAction.value,
            bipeClearAction = bipeClearAction.value,
        ) { event ->
            when (event) {
                is LeaveRoomEvent.LeaveRoom ->
                    if (event.needsConfirmation) {
                        scope.showLeaveRoomAlert(roomId = event.roomId, leaveAction = leaveAction)
                    } else {
                        scope.leaveRoom(roomId = event.roomId, leaveAction = leaveAction)
                    }
                InternalLeaveRoomEvent.ResetState -> {
                    leaveAction.value = AsyncAction.Uninitialized
                    bipeClearAction.value = AsyncAction.Uninitialized
                }
                is InternalLeaveRoomEvent.BipeClearDm -> {
                    leaveAction.value = AsyncAction.Uninitialized
                    scope.bipeClearDm(roomId = event.roomId, clearAction = bipeClearAction)
                }
            }
        }
    }

    private fun CoroutineScope.showLeaveRoomAlert(
        roomId: RoomId,
        leaveAction: MutableState<AsyncAction<Unit>>,
    ) = launch(dispatchers.io) {
        val room = client.getRoom(roomId)
        if (room == null) {
            Timber.e("Unable to find room $roomId to leave it")
            leaveAction.value = AsyncAction.Failure(RoomNotFoundException(roomId))
            return@launch
        }
        room.use {
            val roomInfo = room.roomInfoFlow.first()
            leaveAction.value = when {
                roomInfo.isDm -> Confirmation.Dm(roomId)
                room.isLastOwner() && roomInfo.joinedMembersCount > 1L -> Confirmation.LastOwnerInRoom(roomId)
                // If unknown, assume the room is private
                roomInfo.isPublic == null || roomInfo.isPublic == false -> Confirmation.PrivateRoom(roomId)
                roomInfo.joinedMembersCount == 1L -> Confirmation.LastUserInRoom(roomId)
                else -> Confirmation.Generic(roomId)
            }
        }
    }

    private fun CoroutineScope.leaveRoom(
        roomId: RoomId,
        leaveAction: MutableState<AsyncAction<Unit>>,
    ) = launch(dispatchers.io) {
        leaveAction.runCatchingUpdatingState {
            val room = client.getRoom(roomId) ?: throw RoomNotFoundException(roomId)
            room.use {
                room
                    .leave()
                    .onSuccess { notificationConversationService.onLeftRoom(client.sessionId, roomId) }
                    .onFailure { Timber.e(it, "Error while leaving room ${room.roomId}") }
                    .getOrThrow()
            }
        }
    }

    // Bipe (bipe#464): a DM is not left. The marker hides it until a newer event arrives.
    private fun CoroutineScope.bipeClearDm(
        roomId: RoomId,
        clearAction: MutableState<AsyncAction<Unit>>,
    ) = launch(dispatchers.io) {
        clearAction.runCatchingUpdatingState {
            bipeClearedChats.clear(roomId)
                .onFailure { Timber.e(it, "Bipe: could not clear the DM $roomId") }
                .getOrThrow()
            // The tray and the share sheet drop the DM too (bipe-chat/bipe#479). iOS: removeDeliveredMessageNotifications.
            notificationCleaner.clearMessagesForRoom(client.sessionId, roomId)
            notificationConversationService.onClearedRoom(client.sessionId, roomId)
        }
    }

    private suspend fun BaseRoom.isLastOwner(): Boolean {
        if (roomInfoFlow.value.isDm) {
            // DMs are not owned by the user, so we can return false
            return false
        } else {
            val hasPrivilegedCreatorRole = roomInfoFlow.value.privilegedCreatorRole
            if (!hasPrivilegedCreatorRole) return false
            val owners = usersWithRole { role -> role is RoomMember.Role.Owner }.first()
            return owners.size == 1 && owners.first().userId == sessionId
        }
    }
}

internal class RoomNotFoundException(roomId: RoomId) : IllegalStateException("Room $roomId not found")
