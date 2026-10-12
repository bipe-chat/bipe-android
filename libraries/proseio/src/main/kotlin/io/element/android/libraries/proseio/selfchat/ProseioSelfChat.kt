/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.selfchat

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.createroom.CreateRoomParameters
import io.element.android.libraries.matrix.api.createroom.RoomPreset
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.roomdirectory.RoomVisibility
import io.element.android.libraries.matrix.api.roomlist.awaitLoaded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * The self-chat ("Anotações", ADR 0053): an encrypted private room with only the user. iOS: `ProseioSelfChat`.
 *
 * The room ID is in global account data of type [EVENT_TYPE] with content `{"room_id": "<id>"}`. iOS uses the same
 * key and shape, so both apps open the same room. The room does not show in Conversas or Grupos.
 */
@SingleIn(SessionScope::class)
@Inject
class ProseioSelfChat(
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _roomId = MutableStateFlow<RoomId?>(null)

    /** The self-chat room ID, or null when it is not loaded yet or does not exist. The room lists hide this room. */
    val roomId: StateFlow<RoomId?> = _roomId.asStateFlow()

    /** Reads the room ID from account data again, for example after another device created the room. */
    fun refresh() {
        sessionCoroutineScope.launch {
            readRoomId().onFailure { Timber.w(it, "Proseio self-chat: reading account data failed") }
        }
    }

    /**
     * Returns the self-chat room ID. Opens the stored room when the user is joined to it. Otherwise creates a new
     * room named [name] and stores its ID in account data.
     */
    suspend fun openOrCreate(name: String): Result<RoomId> = mutex.withLock {
        runCatchingExceptions {
            // Fail closed: when account data cannot be read, do not create a second room.
            val stored = readRoomId().getOrThrow()
            if (stored != null && isJoined(stored)) {
                stored
            } else {
                val created = matrixClient.createRoom(
                    CreateRoomParameters(
                        name = name,
                        isEncrypted = true,
                        isDirect = false,
                        visibility = RoomVisibility.Private,
                        preset = RoomPreset.PRIVATE_CHAT,
                        invite = null,
                    )
                ).getOrThrow()
                matrixClient.setAccountData(EVENT_TYPE, json.encodeToString(SelfChatContent.serializer(), SelfChatContent(created.value)))
                    .getOrThrow()
                Timber.i("Proseio self-chat: created ${created.value}")
                _roomId.value = created
                created
            }
        }
    }

    private suspend fun readRoomId(): Result<RoomId?> {
        return matrixClient.getAccountData(EVENT_TYPE).mapCatching { content ->
            val value = content?.let { json.decodeFromString(SelfChatContent.serializer(), it).roomId }
            value?.takeIf { MatrixPatterns.isRoomId(it) }?.let(::RoomId)
        }.onSuccess { roomId ->
            if (roomId != null) _roomId.value = roomId
        }
    }

    private suspend fun isJoined(roomId: RoomId): Boolean {
        // Right after login the room may not be in the local store yet. Wait for the room list before deciding,
        // so a known room is not replaced by a new one.
        if (matrixClient.getRoomInfo(roomId).getOrNull() == null) {
            matrixClient.roomListService.allRooms.awaitLoaded(ROOM_LIST_TIMEOUT)
        }
        val membership = matrixClient.getRoomInfo(roomId).getOrNull()?.currentUserMembership
        return membership == CurrentUserMembership.JOINED
    }

    @Serializable
    private data class SelfChatContent(
        @SerialName("room_id") val roomId: String,
    )

    companion object {
        /** Global account data type. Shared with iOS: do not change. */
        const val EVENT_TYPE = "br.com.proseio.self_chat"

        private val ROOM_LIST_TIMEOUT = 10.seconds
        private val json = Json { ignoreUnknownKeys = true }
    }
}
