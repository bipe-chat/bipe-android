/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.proseio.ProseioHandleResolver
import io.element.android.libraries.proseio.api.ProseioApiClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CoroutineScope

/** Proseio services for one signed-in session. The iOS counterpart lives on `UserSession`. */
@SingleIn(SessionScope::class)
@Inject
class ProseioSession(
    matrixClient: MatrixClient,
    sessionStore: SessionStore,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
    val apiClient: ProseioApiClient,
) {
    private val sessionId = matrixClient.sessionId

    /** The current Matrix access token. The SDK refreshes it and the session store keeps the latest one. */
    val accessToken: suspend () -> String? = { sessionStore.getSession(sessionId.value)?.accessToken }

    val handleResolver = ProseioHandleResolver(
        apiClient = apiClient,
        serverName = matrixClient.userIdServerName(),
        scope = sessionCoroutineScope,
        accessToken = accessToken,
    )
}
