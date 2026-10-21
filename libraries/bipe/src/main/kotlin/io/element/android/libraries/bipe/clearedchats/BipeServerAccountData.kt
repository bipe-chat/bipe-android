/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.clearedchats

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Global account data as the server has it now (bipe-chat/bipe#479). The SDK's copy waits for the next sync, so a
 * read-merge-write that uses it can drop what another device wrote a moment ago. iOS: `StickerPackClient.accountData`.
 */
fun interface BipeServerAccountData {
    /** The content JSON of [eventType], null when the event does not exist (404). Any other answer is a failure. */
    suspend fun get(eventType: String): Result<String?>
}

/**
 * `GET /_matrix/client/v3/user/{userId}/account_data/{type}` over HTTP, as [io.element.android.libraries.bipe.stickers.StickerPackRepository].
 * Own OkHttp client without a logging interceptor: requests carry the Matrix access token.
 */
@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultBipeServerAccountData(
    matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    userAgentInterceptor: UserAgentInterceptor,
) : BipeServerAccountData {
    private val userId = matrixClient.sessionId.value
    private val okHttpClient = OkHttpClient.Builder().addInterceptor(userAgentInterceptor).build()

    override suspend fun get(eventType: String): Result<String?> = runCatchingExceptions {
        val token = sessionStore.getSession(userId)?.accessToken ?: throw IOException("No access token")
        try {
            request(eventType, token)
        } catch (e: UnauthorizedException) {
            // The token can refresh between the read and the request: read it again once.
            val freshToken = sessionStore.getSession(userId)?.accessToken ?: throw e
            request(eventType, freshToken)
        }
    }

    private suspend fun request(eventType: String, token: String): String? {
        val baseUrl = sessionStore.getSession(userId)?.homeserverUrl?.toHttpUrl() ?: throw IOException("No homeserver URL")
        val url = baseUrl.newBuilder()
            .addPathSegments("_matrix/client/v3/user")
            .addPathSegment(userId)
            .addPathSegment("account_data")
            .addPathSegment(eventType)
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        return withContext(Dispatchers.IO) {
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
}

private class UnauthorizedException : IOException("HTTP 401")
