/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.support

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The support DM with the Bipe Support account (ADR 0114). The API makes the room and invites the user;
 * this class joins it when needed. Same flow as iOS `openSupportChat`.
 */
@SingleIn(SessionScope::class)
@Inject
class BipeSupportChat(
    private val matrixClient: MatrixClient,
    private val bipeSession: BipeSession,
) {
    private val mutex = Mutex()

    suspend fun open(): Result<RoomId> = mutex.withLock {
        runCatchingExceptions {
            val roomId = RoomId(
                BipeApiClient.withTokenRetry(bipeSession.accessToken) { token -> bipeSession.apiClient.supportRoomId(token) }
            )
            val membership = matrixClient.getRoomInfo(roomId).getOrNull()?.currentUserMembership
            if (membership != CurrentUserMembership.JOINED) matrixClient.joinRoom(roomId).getOrThrow()
            roomId
        }
    }
}
