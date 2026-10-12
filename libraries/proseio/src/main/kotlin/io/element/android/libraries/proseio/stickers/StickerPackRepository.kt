/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Sticker packs for the picker (ADR 0033). */
interface StickerPackRepository {
    /** The user's own pack, the packs of [roomId], and the room packs the user enabled. */
    suspend fun packs(roomId: String): Result<List<StickerPack>>

    /**
     * Adds the stickers in [uris] (a WhatsApp chat export ZIP, or WebP and PNG files) to the user's pack.
     * Returns how many stickers it added; 0 when none were new.
     */
    suspend fun importStickers(uris: List<Uri>): Result<Int>

    /** "Save to my stickers": adds a received sticker's file. Returns 0 when it was already in the pack. */
    suspend fun addSticker(bytes: ByteArray): Result<Int>

    /** "Make from a photo": turns the photo at [uri] into a sticker and adds it. */
    suspend fun makeSticker(uri: Uri): Result<Int>
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
    @ApplicationContext private val context: Context,
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

    override suspend fun importStickers(uris: List<Uri>): Result<Int> = runCatchingExceptions {
        val candidates = withContext(Dispatchers.IO) {
            uris.flatMap { uri ->
                StickerImporter.candidates(displayName(uri)) { context.contentResolver.openInputStream(uri) }
            }.distinctBy { it.shortcode }
        }
        if (candidates.isEmpty()) return@runCatchingExceptions 0
        withTokenRetry { token -> addToUserPack(candidates, token) }
    }

    override suspend fun addSticker(bytes: ByteArray): Result<Int> = runCatchingExceptions {
        val candidate = StickerImporter.candidate(bytes) ?: throw IOException("Not a usable sticker")
        withTokenRetry { token -> addToUserPack(listOf(candidate), token) }
    }

    override suspend fun makeSticker(uri: Uri): Result<Int> = runCatchingExceptions {
        val candidate = withContext(Dispatchers.Default) {
            decodePhoto(uri)?.let(StickerImporter::fromPhoto)
        } ?: throw IOException("The photo did not decode")
        withTokenRetry { token -> addToUserPack(listOf(candidate), token) }
    }

    /** Decodes the photo at most 2048 px on the long side. ImageDecoder (Android 9+) applies the EXIF orientation. */
    private fun decodePhoto(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longSide = maxOf(info.size.width, info.size.height)
                if (longSide > 2048) {
                    val scale = 2048f / longSide
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= 2048) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** Uploads the candidates that are not yet in the pack. Keeps every other field of the pack. */
    private suspend fun addToUserPack(candidates: List<StickerImportCandidate>, token: String): Int {
        val path = listOf("user", userId, "account_data", StickerPackEventType.USER_PACK)
        val content = get(path, token)
            ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: JsonObject(emptyMap())
        val images = (content["images"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()

        var added = 0
        var uploadError: Exception? = null
        for (candidate in candidates) {
            if (images.containsKey(candidate.shortcode)) continue
            try {
                val extension = if (candidate.mimeType == "image/png") "png" else "webp"
                val url = upload(candidate.bytes, candidate.mimeType, "${candidate.shortcode}.$extension", token)
                images[candidate.shortcode] = buildJsonObject {
                    put("url", url)
                    put("body", "Sticker")
                    putJsonArray("usage") { add("sticker") }
                    putJsonObject("info") {
                        put("w", candidate.width)
                        put("h", candidate.height)
                        put("mimetype", candidate.mimeType)
                        put("size", candidate.bytes.size)
                    }
                }
                added++
            } catch (e: IOException) {
                // Keep what is already uploaded: save the pack, then report the error.
                uploadError = e
                break
            }
        }
        if (added > 0) {
            val updated = JsonObject(content + ("images" to JsonObject(images)))
            put(path, updated.toString(), token)
        }
        uploadError?.let { throw it }
        return added
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
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
    private suspend fun get(path: List<String>, token: String): String? {
        val request = Request.Builder().url(clientUrl(path)).header("Authorization", "Bearer $token").build()
        return perform(request)
    }

    /** PUT a JSON body under `/_matrix/client/v3`. */
    private suspend fun put(path: List<String>, json: String, token: String) {
        val request = Request.Builder()
            .url(clientUrl(path))
            .header("Authorization", "Bearer $token")
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()
        perform(request)
    }

    /** `POST /_matrix/media/v3/upload`. Returns the `mxc://` URI. */
    private suspend fun upload(bytes: ByteArray, mimeType: String, fileName: String, token: String): String {
        val url = baseUrl().newBuilder()
            .addPathSegments("_matrix/media/v3/upload")
            .addQueryParameter("filename", fileName)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val body = perform(request) ?: throw IOException("Upload returned 404")
        val contentUri = (Json.parseToJsonElement(body) as? JsonObject)?.get("content_uri") as? JsonPrimitive
        return contentUri?.content?.takeIf { it.startsWith("mxc://") } ?: throw IOException("Upload returned no content_uri")
    }

    private suspend fun baseUrl() = homeserverUrl()?.toHttpUrl() ?: throw IOException("No homeserver URL")

    private suspend fun clientUrl(path: List<String>) =
        path.fold(baseUrl().newBuilder().addPathSegments("_matrix/client/v3")) { builder, segment ->
            builder.addPathSegment(segment)
        }.build()

    private suspend fun perform(request: Request): String? = withContext(Dispatchers.IO) {
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
