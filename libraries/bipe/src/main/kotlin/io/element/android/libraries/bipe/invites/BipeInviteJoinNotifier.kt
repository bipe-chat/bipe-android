/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import android.content.Context
import android.widget.Toast
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeInvite
import io.element.android.libraries.bipe.api.BipeInviteStatus
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Tells the inviter, once per join, that someone joined with their invite: a toast when the session starts. iOS: `BipeInviteJoinNotifier`.
 * The first check on a device only records existing joins, so old joins are not announced. Then sends the notes of joined invites (ADR 0136).
 */
@SingleIn(SessionScope::class)
@Inject
class BipeInviteJoinNotifier(
    private val bipeSession: BipeSession,
    private val noteSender: BipeInviteNoteSender,
    @ApplicationContext private val context: Context,
) {
    suspend fun run() {
        val myInvites = try {
            BipeApiClient.withTokenRetry(bipeSession.accessToken) { token -> bipeSession.apiClient.myInvites(token) }
        } catch (error: BipeApiException) {
            Timber.e(error, "Bipe invite join check failed")
            return
        }
        announceJoins(myInvites.invites)
        noteSender.send(myInvites.invites)
    }

    private suspend fun announceJoins(invites: List<BipeInvite>) {
        val joined = invites.filter { it.status == BipeInviteStatus.USED && it.joinedUserId != null }
        val joinedIds = joined.map { it.id }.toSet()
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val key = "seen_${bipeSession.userId}"
        val seen = preferences.getStringSet(key, null)?.toSet()
        preferences.edit().putStringSet(key, seen.orEmpty() + joinedIds).apply()
        if (seen == null) return

        val newUserIds = joined.filter { it.id !in seen }.mapNotNull { it.joinedUserId }
        for (userId in newUserIds) {
            bipeSession.handleResolver.resolve(userId)
            val name = bipeSession.handleResolver.displayText(userId)
            // A toast, not a snackbar: this runs at session start from LoggedInFlowNode, with no screen in reach (bipe-chat/bipe#450).
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.bipe_invites_joined, name), Toast.LENGTH_LONG).show()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "bipe_invite_joins"
    }
}

/**
 * Bipe ADR 0136: sends the inviter's note as the first message in the chat with the person who joined. iOS: `BipeInviteNoteSender`.
 * Only into an existing, joined DM: the invitee's app makes the chat, so a second one is never created here.
 * Without that chat the note stays for the next run. At most once per invite: the note is taken out of the store before the send
 * and put back only when the send fails.
 */
@SingleIn(SessionScope::class)
@Inject
class BipeInviteNoteSender(
    private val matrixClient: MatrixClient,
    private val bipeSession: BipeSession,
    private val linkStore: BipeInviteLinkStore,
) {
    /**
     * Reads the invites only when this device holds a note. For the retry after an invite-pair chat is accepted:
     * [joinedRoomId] is that chat, because the SDK can list it as a DM only after the next sync.
     */
    suspend fun run(joinedRoomId: RoomId? = null) {
        if (linkStore.notes(bipeSession.userId).isEmpty()) return
        val myInvites = try {
            BipeApiClient.withTokenRetry(bipeSession.accessToken) { token -> bipeSession.apiClient.myInvites(token) }
        } catch (error: BipeApiException) {
            Timber.e(error, "Bipe invite notes: invites read failed")
            return
        }
        send(myInvites.invites, joinedRoomId)
    }

    suspend fun send(invites: List<BipeInvite>, joinedRoomId: RoomId? = null) {
        val notes = linkStore.notes(bipeSession.userId)
        if (notes.isEmpty()) return
        val recipients = invites
            .filter { it.status == BipeInviteStatus.USED && it.id in notes }
            .mapNotNull { invite -> invite.joinedUserId?.let { runCatchingExceptions { UserId(it) }.getOrNull() }?.let { invite.id to it } }
            .filter { (_, userId) -> userId != matrixClient.sessionId }
        for ((inviteId, userId) in recipients) {
            val room = directRoom(userId, joinedRoomId)
            if (room == null) {
                Timber.i("Bipe invite note for $inviteId waits for the chat with $userId")
            } else {
                room.use { send(inviteId, it) }
            }
        }
    }

    private suspend fun send(inviteId: String, room: JoinedRoom) {
        val note = linkStore.takeNote(bipeSession.userId, inviteId) ?: return
        room.liveTimeline.sendMessage(body = note, htmlBody = null, intentionalMentions = emptyList())
            .onSuccess { Timber.i("Bipe invite note for $inviteId sent") }
            .onFailure {
                Timber.e(it, "Bipe invite note for $inviteId not sent")
                linkStore.saveNote(bipeSession.userId, inviteId, note)
            }
    }

    private suspend fun directRoom(userId: UserId, joinedRoomId: RoomId?): JoinedRoom? {
        matrixClient.findDM(userId).getOrNull()?.let { roomId -> matrixClient.getJoinedRoom(roomId)?.let { return it } }
        val room = joinedRoomId?.let { matrixClient.getJoinedRoom(it) } ?: return null
        val info = room.info()
        if (info.isDirect && info.heroes.any { it.userId == userId }) return room
        room.close()
        return null
    }
}
