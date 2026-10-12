/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.stickers

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Sticker packs for the picker (ADR 0033). */
fun interface StickerPackRepository {
    /** The user's own pack, the packs of [roomId], and the room packs the user enabled. */
    suspend fun packs(roomId: String): Result<List<StickerPack>>
}

/**
 * Reads sticker packs from the homeserver's client-server API.
 * The SDK cannot read custom room state under sliding sync, so this goes over HTTP.
 * Own OkHttp client without a logging interceptor: requests carry the Matrix access token.
 */
@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
@Inject
class HttpStickerPackRepository(
    matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    userAgentInterceptor: UserAgentInterceptor,
) : StickerPackRepository {
    private val userId = matrixClient.sessionId.value
    private val okHttpClient = OkHttpClient.Builder().addInterceptor(userAgentInterceptor).build()

    private suspend fun homeserverUrl(): String? = sessionStore.getSession(userId)?.homeserverUrl

    private suspend fun accessToken(): String? = sessionStore.getSession(userId)?.accessToken

    override suspend fun packs(roomId: String): Result<List<StickerPack>> = runCatchingExceptions {
        withTokenRetry { token -> loadPacks(roomId, token) }
    }

    private suspend fun loadPacks(roomId: String, token: String): List<StickerPack> {
        val packs = mutableListOf<StickerPack>()

        get(listOf("user", userId, "account_data", StickerPackEventType.USER_PACK), token)
            ?.let { StickerPack.parse(it, StickerPack.Source.User) }
            ?.let { packs += it }

        packs += roomPacks(roomId, token)

        get(listOf("user", userId, "account_data", StickerPackEventType.ENABLED_ROOMS), token)?.let { content ->
            for ((enabledRoomId, stateKey) in StickerPack.parseEnabledRooms(content)) {
                if (enabledRoomId == roomId) continue
                // A pack in a room the user left is unreadable. Skip it, keep the others.
                val pack = runCatching {
                    get(listOf("rooms", enabledRoomId, "state", StickerPackEventType.ROOM_PACK, stateKey), token)
                }.getOrNull()?.let { StickerPack.parse(it, StickerPack.Source.Room(enabledRoomId, stateKey)) }
                if (pack != null) packs += pack
            }
        }
        return packs
    }

    private suspend fun roomPacks(roomId: String, token: String): List<StickerPack> {
        val body = get(listOf("rooms", roomId, "state"), token) ?: return emptyList()
        val events = runCatching { Json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
        return events.mapNotNull { element ->
            val event = element as? JsonObject ?: return@mapNotNull null
            if ((event["type"] as? JsonPrimitive)?.content != StickerPackEventType.ROOM_PACK) return@mapNotNull null
            val stateKey = (event["state_key"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val content = event["content"] as? JsonObject ?: return@mapNotNull null
            StickerPack.parse(content, StickerPack.Source.Room(roomId, stateKey))
        }.sortedBy { it.id }
    }

    /** Runs [request] with the current token. On HTTP 401 it reads the token again and retries once. */
    private suspend fun <T> withTokenRetry(request: suspend (String) -> T): T {
        val token = accessToken() ?: throw UnauthorizedException()
        return try {
            request(token)
        } catch (e: UnauthorizedException) {
            val freshToken = accessToken() ?: throw e
            request(freshToken)
        }
    }

    /** GET under `/_matrix/client/v3`. Returns null on 404 (no such account data or state). */
    private suspend fun get(path: List<String>, token: String): String? = withContext(Dispatchers.IO) {
        val base = homeserverUrl()?.toHttpUrl() ?: throw IOException("No homeserver URL")
        val url = path.fold(base.newBuilder().addPathSegments("_matrix/client/v3")) { builder, segment ->
            builder.addPathSegment(segment)
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> response.body.string()
                404 -> null
                401 -> throw UnauthorizedException()
                else -> throw IOException("HTTP ${response.code}")
            }
        }
    }
}

private class UnauthorizedException : IOException("HTTP 401")
