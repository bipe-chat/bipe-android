/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.fixtures

import io.element.android.libraries.bipe.BipeHandleChanges
import io.element.android.libraries.bipe.BipeHandleResolver
import io.element.android.libraries.bipe.clearedchats.BipeClearedChats
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Bipe services with no API: the server name matches no test user and there is no access token,
 * so no request leaves the test. Handles and handle changes stay empty.
 */
internal fun aBipeSession(scope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)): BipeSession {
    val handleChanges = BipeHandleChanges(apiClient = mockk(), serverName = "bipe.invalid", scope = scope, accessToken = { null })
    val handleResolver = BipeHandleResolver(apiClient = mockk(), serverName = "bipe.invalid", scope = scope, accessToken = { null })
    return mockk {
        every { this@mockk.handleChanges } returns handleChanges
        every { this@mockk.handleResolver } returns handleResolver
    }
}

/** No cleared DMs. Account data reads fail in [FakeMatrixClient] by default, so the errors are dropped. */
internal fun aBipeClearedChats() = BipeClearedChats(
    matrixClient = FakeMatrixClient(),
    sessionCoroutineScope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> }),
)
