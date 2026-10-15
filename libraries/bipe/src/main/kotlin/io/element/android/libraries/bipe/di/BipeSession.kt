/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.bipe.BipeHandleChanges
import io.element.android.libraries.bipe.BipeHandleResolver
import io.element.android.libraries.bipe.BipePublicProfiles
import io.element.android.libraries.bipe.account.BipeAccountSession
import io.element.android.libraries.bipe.account.BipeDeviceAccount
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope

/** Bipe services for one signed-in session. The iOS counterpart lives on `UserSession`. */
@SingleIn(SessionScope::class)
@Inject
class BipeSession(
    matrixClient: MatrixClient,
    sessionStore: SessionStore,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
    val apiClient: BipeApiClient,
    deviceAccount: BipeDeviceAccount,
    stringProvider: StringProvider,
) {
    private val sessionId = matrixClient.sessionId

    /** The current Matrix access token. The SDK refreshes it and the session store keeps the latest one. */
    val accessToken: suspend () -> String? = { sessionStore.getSession(sessionId.value)?.accessToken }

    /** Handle change history for the timeline lines (ADR 0066). */
    val handleChanges = BipeHandleChanges(
        apiClient = apiClient,
        serverName = matrixClient.userIdServerName(),
        scope = sessionCoroutineScope,
        accessToken = accessToken,
    )

    val handleResolver = BipeHandleResolver(
        apiClient = apiClient,
        serverName = matrixClient.userIdServerName(),
        scope = sessionCoroutineScope,
        accessToken = accessToken,
        onBatchResolved = handleChanges::refresh,
    )

    /** Device-first account state, PIN and recovery alerts (ADR 0057). */
    val account = BipeAccountSession(
        userId = sessionId.value,
        apiClient = apiClient,
        deviceAccount = deviceAccount,
        accessToken = accessToken,
        stringProvider = stringProvider,
    )

    val publicProfiles = BipePublicProfiles(
        apiClient = apiClient,
        scope = sessionCoroutineScope,
        accessToken = accessToken,
    )
}
