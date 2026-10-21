/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.clearedchats

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * "Apagar conversa" in a DM (bipe-chat/bipe#464). iOS: `BipeClearedChats`.
 *
 * The user stays in the room. The marker in global account data ([BipeRegistries.CLEARED_CHATS_EVENT_TYPE]) hides the
 * DM from the room list until a newer event arrives, and the timeline and search drop the events up to the marker.
 * Other devices of the account follow, because account data syncs.
 *
 * The SDK observes only the typed account data events (`AccountDataEventType`), not a custom type. So this class reads
 * the event again from the local store when the room list changes (each sync that touches a room), at most once per
 * [REREAD_INTERVAL], and after its own write.
 */
@SingleIn(SessionScope::class)
@Inject
class BipeClearedChats(
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val serverAccountData: BipeServerAccountData,
) {
    private val mutex = Mutex()

    // Markers this device wrote. The SDK stores account data only when the sync brings it back, so until then a read
    // from the store can miss them. The marker never moves back, so the larger value wins.
    private var ownMarkers: Map<String, Long> = emptyMap()
    private val _state = MutableStateFlow(ClearedChats.EMPTY)
    private val _clearedHere = MutableSharedFlow<RoomId>(extraBufferCapacity = 1)

    /** The current markers. Empty until the first read. */
    val state: StateFlow<ClearedChats> = _state.asStateFlow()

    /** Emits a room ID after this device cleared that DM, so an open room screen can close. */
    val clearedHere: SharedFlow<RoomId> = _clearedHere.asSharedFlow()

    init {
        sessionCoroutineScope.launch {
            matrixClient.roomListService.allRooms.summaries
                .conflate()
                .collect {
                    read().onFailure { Timber.w(it, "Bipe cleared chats: reading account data failed") }
                    delay(REREAD_INTERVAL)
                }
        }
        refresh()
    }

    /** Reads the markers again from account data. */
    fun refresh() {
        sessionCoroutineScope.launch {
            read().onFailure { Timber.w(it, "Bipe cleared chats: reading account data failed") }
        }
    }

    /**
     * Clears the DM [roomId]: writes its marker with the time of the latest event of the room, or the current time
     * when the room has no latest event. Keeps the other entries. Sends no read receipt (ADR 0050 allows public receipts only).
     */
    suspend fun clear(roomId: RoomId): Result<Unit> = mutex.withLock {
        runCatchingExceptions {
            val ts = latestEventTimestamp(roomId) ?: System.currentTimeMillis()
            // The server's copy, not the SDK's: that one waits for the next sync and can miss a marker that another device
            // wrote a moment ago (bipe-chat/bipe#479). Fail closed: when it cannot be read, do not write, so no marker is lost.
            val stored = serverAccountData.get(EVENT_TYPE).getOrThrow()
            val content = ownMarkers.entries.fold(stored) { acc, (id, marker) -> ClearedChats.contentClearing(acc, id, marker) }
            val newContent = ClearedChats.contentClearing(content, roomId.value, ts)
            matrixClient.setAccountData(EVENT_TYPE, newContent).getOrThrow()
            val written = ClearedChats.parse(newContent)
            ownMarkers = ownMarkers + (roomId.value to (written.marker(roomId.value) ?: ts))
            _state.value = written
            Timber.i("Bipe cleared chats: cleared ${roomId.value}")
        }
    }.onSuccess {
        _clearedHere.tryEmit(roomId)
    }

    /** The marker of [roomId], then each change of it. */
    fun marker(roomId: RoomId): Flow<Long?> = state.map { it.marker(roomId.value) }.distinctUntilChanged()

    /** True when the room lists leave out [roomId]: a marker and no event after it, by the latest room list summary. */
    fun isHidden(roomId: RoomId, clearedChats: ClearedChats = state.value): Boolean =
        clearedChats.isHidden(roomId.value, latestEventTimestamp(roomId))

    private suspend fun read(): Result<Unit> = mutex.withLock {
        matrixClient.getAccountData(EVENT_TYPE).mapCatchingExceptions { content ->
            _state.value = ClearedChats.parse(content).mergedWith(ownMarkers)
        }
    }

    private fun latestEventTimestamp(roomId: RoomId): Long? {
        val summaries = matrixClient.roomListService.allRooms.summaries.replayCache.lastOrNull()
        return summaries?.firstOrNull { it.roomId == roomId }?.latestEventTimestamp
    }

    private companion object {
        const val EVENT_TYPE = BipeRegistries.CLEARED_CHATS_EVENT_TYPE
        val REREAD_INTERVAL = 1.seconds
    }
}

/**
 * The markers of this client's account, read again from the SDK's copy on each room list change (bipe-chat/bipe#479).
 * For screens outside the session graph, such as the share and forward pickers, which can show another account.
 * Starts with no markers. A failed read keeps the last value.
 */
fun MatrixClient.bipeClearedChatsFlow(): Flow<ClearedChats> = roomListService.allRooms.summaries
    .conflate()
    .mapNotNull { getAccountData(BipeRegistries.CLEARED_CHATS_EVENT_TYPE).map { ClearedChats.parse(it) }.getOrNull() }
    .onStart { emit(ClearedChats.EMPTY) }
    .distinctUntilChanged()

/** True when the room lists leave out this joined DM: a marker and no event after it (bipe-chat/bipe#464). */
fun RoomSummary.bipeIsHiddenDm(clearedChats: ClearedChats): Boolean = isDm && clearedChats.isHidden(roomId.value, latestEventTimestamp)
