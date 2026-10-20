/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.bipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.element.android.features.preferences.impl.notifications.SystemNotificationsEnabledProvider
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeBusinessClaim
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.bipe.settings.BipeWayBack
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.pushstore.api.UserPushStoreFactory
import timber.log.Timber

/**
 * Values that the Você root and the group screens both show (ADR 0112), so they read the same source.
 */
object BipeSettingsValues {
    /** The ways back of the account, or `null` while the account state is not loaded. */
    @Composable
    fun waysBack(bipeSession: BipeSession, matrixClient: MatrixClient): List<BipeWayBack>? {
        val status by bipeSession.account.status.collectAsState()
        LaunchedEffect(Unit) { bipeSession.account.refresh() }
        val recoveryState by matrixClient.encryptionService.recoveryStateStateFlow.collectAsState()
        val syncedToken by produceState(initialValue = false, status?.recoveryTokenSet) {
            value = status?.recoveryTokenSet == true && bipeSession.account.hasSyncedRecoveryToken()
        }
        val current = status ?: return null
        return BipeWayBack.of(
            status = current,
            recoveryKeyEnabled = recoveryState == RecoveryState.ENABLED,
            syncedTokenPresent = syncedToken,
        )
    }

    /** `true` when `GET /v1/me/account` says `company` (ADR 0087). The ways-back read above loads it. */
    @Composable
    fun isCompany(bipeSession: BipeSession): Boolean {
        val status by bipeSession.account.status.collectAsState()
        return status?.accountType == "company"
    }

    /** `GET /v1/me/business` for a company account (ADR 0042). `null` without a claim, for a person, or while it loads. */
    @Composable
    fun business(bipeSession: BipeSession, isCompany: Boolean): BipeBusinessClaim? {
        val business by produceState<BipeBusinessClaim?>(initialValue = null, isCompany) {
            if (!isCompany) return@produceState
            value = runCatchingExceptions { BipeApiClient.withTokenRetry(bipeSession.accessToken) { bipeSession.apiClient.myBusiness(it) } }
                .onFailure { Timber.w(it, "Bipe: business claim not loaded") }
                .getOrNull()
        }
        return business
    }

    /** `true` when notifications are on for this device in the app and in the system settings. */
    @Composable
    fun notificationsOn(
        userPushStoreFactory: UserPushStoreFactory,
        systemNotificationsEnabledProvider: SystemNotificationsEnabledProvider,
        matrixClient: MatrixClient,
    ): Boolean {
        val userPushStore = remember { userPushStoreFactory.getOrCreate(matrixClient.sessionId) }
        val enabledForDevice by remember { userPushStore.getNotificationEnabledForDevice() }.collectAsState(initial = true)
        return enabledForDevice && systemNotificationsEnabledProvider.notificationsEnabled()
    }
}
