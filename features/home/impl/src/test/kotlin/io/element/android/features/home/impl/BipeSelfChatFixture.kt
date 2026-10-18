/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl

import io.element.android.libraries.bipe.selfchat.BipeSelfChat
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** A self-chat that never finds a room. Account data reads fail in [FakeMatrixClient] by default, so errors are dropped. */
internal fun aBipeSelfChat(client: MatrixClient = FakeMatrixClient()) = BipeSelfChat(
    matrixClient = client,
    sessionCoroutineScope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> }),
)
