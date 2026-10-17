/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.tabs

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.bipe.settings.BipeWayBack
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/**
 * The warning badge on the Você tab (ADR 0112): `true` when the account has one way back or none.
 * It counts the same ways back as the "Sua conta" row.
 */
fun interface BipeYouTabWarning {
    fun warning(): Flow<Boolean>
}

@ContributesBinding(SessionScope::class)
class DefaultBipeYouTabWarning(
    private val bipeSession: BipeSession,
    private val matrixClient: MatrixClient,
) : BipeYouTabWarning {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun warning(): Flow<Boolean> {
        val account = bipeSession.account
        return combine(account.status, matrixClient.encryptionService.recoveryStateStateFlow) { status, recoveryState ->
            status to recoveryState
        }
            .mapLatest { (status, recoveryState) ->
                // Without the account state the email is unknown: no warning.
                status ?: return@mapLatest false
                val waysBack = BipeWayBack.of(
                    status = status,
                    recoveryKeyEnabled = recoveryState == RecoveryState.ENABLED,
                    syncedTokenPresent = status.recoveryTokenSet && account.hasSyncedRecoveryToken(),
                )
                BipeWayBack.needsWarning(waysBack.size)
            }
            .onStart { account.refresh() }
            .distinctUntilChanged()
    }
}
