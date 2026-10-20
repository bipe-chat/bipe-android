/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import android.content.Context
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections

/**
 * Invite pairs (ADR 0034): the people this account invited who joined, and its own inviter.
 * A direct-chat invite from one of them skips Pedidos and is accepted without a tap. iOS: `BipeInvitePairs`.
 * The pairs come from `GET /v1/me/invite-connections`; the last list is kept per account on this device.
 */
@SingleIn(SessionScope::class)
@Inject
class BipeInvitePairs(
    private val matrixClient: MatrixClient,
    private val bipeSession: BipeSession,
    @ApplicationContext private val context: Context,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) {
    private val _userIds = MutableStateFlow(stored())

    /** The Matrix user IDs of the invite pairs. */
    val userIds: StateFlow<Set<String>> = _userIds.asStateFlow()

    /** Rooms already tried, so a failing join is not retried on every room list update. */
    private val attemptedRoomIds = Collections.synchronizedSet(mutableSetOf<RoomId>())

    /** Request rooms that already caused a refresh: a new invitee's first chat can arrive before the pairs know them. */
    private val recheckedRoomIds = Collections.synchronizedSet(mutableSetOf<RoomId>())

    init {
        sessionCoroutineScope.launch { refresh() }
    }

    /** Reloads the pairs from the Bipe API. A failed read keeps the last list. */
    suspend fun refresh() {
        val fresh = try {
            BipeApiClient.withTokenRetry(bipeSession.accessToken) { token -> bipeSession.apiClient.inviteConnections(token) }.toSet()
        } catch (error: BipeApiException) {
            Timber.w(error, "Bipe invite pairs: refresh failed")
            return
        }
        if (fresh == _userIds.value) return
        _userIds.value = fresh
        preferences().edit().putStringSet(key(), fresh).apply()
    }

    /** Reloads the pairs once for a direct-chat invite from an unknown sender. */
    fun recheck(roomId: RoomId) {
        if (!recheckedRoomIds.add(roomId)) return
        sessionCoroutineScope.launch { refresh() }
    }

    /** Joins a direct-chat invite from an invite pair, once per room. */
    fun autoAccept(roomId: RoomId) {
        if (!attemptedRoomIds.add(roomId)) return
        sessionCoroutineScope.launch {
            matrixClient.joinRoom(roomId).onFailure { Timber.e(it, "Bipe: auto-accept of invite-pair chat $roomId failed") }
        }
    }

    private fun stored(): Set<String> = preferences().getStringSet(key(), null)?.toSet().orEmpty()

    private fun key() = "pairs_${bipeSession.userId}"

    private fun preferences() = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val FILE_NAME = "bipe_invite_pairs"
    }
}
