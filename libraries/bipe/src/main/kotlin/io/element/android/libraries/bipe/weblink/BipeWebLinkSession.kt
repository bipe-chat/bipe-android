/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.weblink

import io.element.android.libraries.bipe.account.BipeDeviceAccount
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeWebLinkPlace
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.time.Instant

/** Web sign-in by QR code and the linked devices of one session (ADR 0116, ADR 0118). */
class BipeWebLinkSession(
    val userId: String,
    private val deviceId: String,
    private val homeserverUrl: suspend () -> String?,
    private val accessToken: suspend () -> String?,
    private val apiClient: BipeApiClient,
    private val matrixAccountApi: BipeMatrixAccountApi,
    private val deviceAccount: BipeDeviceAccount,
) {
    private val _changes = MutableStateFlow(0)

    /** Goes up when the list of devices may have changed: a computer linked, a device signed out. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun notifyDevicesChanged() {
        _changes.value += 1
    }

    /**
     * The Bipe check in place of the SDK's: the SDK says yes only for an OAuth session, and Bipe signs in with
     * `m.login.password`. QR linking works when the server advertises MSC4108 and this device holds the hidden password.
     */
    suspend fun isSupported(): Boolean {
        if (deviceAccount.hiddenPassword(userId) == null) {
            Timber.i("Bipe web link: no hidden password on this device")
            return false
        }
        val homeserver = homeserverUrl() ?: return false
        return matrixAccountApi.supportsQrLogin(homeserver)
    }

    /** The other devices of the account, newest use first. This phone is not in it. */
    suspend fun otherDevices(): Result<List<BipeMatrixDevice>> = runCatchingExceptions {
        val homeserver = homeserverUrl() ?: error("No homeserver")
        val token = accessToken() ?: error("No access token")
        matrixAccountApi.devices(homeserver, token)
            .filter { it.deviceId != deviceId }
            .sortedByDescending { it.lastSeenTs ?: 0 }
    }

    suspend fun authMetadata(): Result<BipeAuthMetadata> = runCatchingExceptions {
        matrixAccountApi.authMetadata(homeserverUrl() ?: error("No homeserver"))
    }

    /** Read it only after the biometric check (ADR 0118). */
    suspend fun hiddenPassword(): String? = deviceAccount.hiddenPassword(userId)

    suspend fun place(ip: String): BipeWebLinkPlace? = runCatchingExceptions {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.webLinkPlace(ip, it) }
    }.getOrNull()

    /** Ends the MAS browser sessions that the private web view made since [since]. */
    suspend fun finish(since: Instant) {
        runCatchingExceptions { BipeApiClient.withTokenRetry(accessToken) { apiClient.finishWebLink(since, it) } }
            .onSuccess { Timber.i("Bipe web link: ended $it MAS browser sessions") }
            .onFailure { Timber.e(it, "Bipe web link: finish failed") }
        notifyDevicesChanged()
    }
}
