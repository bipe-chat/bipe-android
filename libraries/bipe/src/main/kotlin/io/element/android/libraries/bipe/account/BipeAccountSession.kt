/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account

import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeAccountStatus
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeRecovery
import io.element.android.libraries.bipe.api.BipeRecoveryMethod
import io.element.android.libraries.bipe.api.BipeRecoveryPurpose
import io.element.android.libraries.bipe.api.BipeRecoveryState
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * The device-first account state of one signed-in session: PIN state, the move of a beta account, and the recovery
 * alerts for this device (ADR 0057). The app reads it when it comes to the foreground.
 */
class BipeAccountSession(
    private val userId: String,
    private val apiClient: BipeApiClient,
    private val deviceAccount: BipeDeviceAccount,
    private val accessToken: suspend () -> String?,
    private val stringProvider: StringProvider,
) {
    private val statusFlow = MutableStateFlow<BipeAccountStatus?>(null)
    private val alertsFlow = MutableStateFlow<List<BipeRecovery>>(emptyList())

    /** `null` until the first answer of `GET /v1/me/account`. */
    val status: StateFlow<BipeAccountStatus?> = statusFlow.asStateFlow()

    /** Recoveries to show on this device, newest first: waiting ones, and token sign-ins on another device. */
    val alerts: StateFlow<List<BipeRecovery>> = alertsFlow.asStateFlow()

    suspend fun refresh(): Result<BipeAccountStatus> = runCatchingExceptions {
        deviceAccount.rememberSignedIn(userId)
        val status = BipeApiClient.withTokenRetry(accessToken) { apiClient.account(it) }
        statusFlow.value = status
        alertsFlow.value = alertsFor(status)
        status
    }.onFailure { Timber.w(it, "Bipe: account state not loaded") }

    /** The inviter's Matrix ID after a personal-invite sign-up on this device, once (ADR 0034). */
    suspend fun takePendingInviter(): String? = deviceAccount.takePendingInviter(userId)

    /** Sets or changes the PIN on the server, then the app lock PIN. The caller does the biometric check first. */
    suspend fun changePin(pin: String): Result<Unit> = runCatchingExceptions {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.setPin(pin, it) }
        deviceAccount.lock.setPin(pin)
        refresh()
        Unit
    }

    /** The lock screen set a new app lock PIN: send it to the server too, so both stay the same PIN. */
    suspend fun onAppLockPinSet(pin: String): Result<Unit> = runCatchingExceptions {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.setPin(pin, it) }
        refresh()
        Unit
    }.onFailure { Timber.e(it, "Bipe: app lock PIN not sent to the server") }

    /**
     * A beta account (e-mail and own password) moves to a hidden password: PIN first, then `POST /v1/me/device-first`.
     * The user's own password stops working.
     */
    suspend fun moveToDeviceFirst(pin: String): Result<Unit> = runCatchingExceptions {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.setPin(pin, it) }
        val credentials = BipeApiClient.withTokenRetry(accessToken) { apiClient.moveToDeviceFirst(it) }
        deviceAccount.onMovedToDeviceFirst(credentials)
        deviceAccount.lock.setPin(pin)
        refresh()
        Unit
    }

    /** Adds or changes the recovery email. The address stays pending until the user opens the link the server sends. */
    suspend fun setRecoveryEmail(email: String): Result<Unit> = runCatchingExceptions {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.setRecoveryEmail(email, it) }
        refresh()
        Unit
    }.onFailure { Timber.e(it, "Bipe: recovery email not saved") }

    /** Removes the verified and the pending recovery email. */
    suspend fun removeRecoveryEmail(): Result<Unit> = runCatchingExceptions {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.removeRecoveryEmail(it) }
        refresh()
        Unit
    }.onFailure { Timber.e(it, "Bipe: recovery email not removed") }

    /** "Cancel, it was not me": stops a waiting recovery. */
    suspend fun cancelRecovery(recovery: BipeRecovery): Result<Unit> = runCatchingExceptions {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.cancelRecovery(recovery.id, it) }
        deviceAccount.dismissRecovery(recovery.id)
        refresh()
        Unit
    }

    /** "It was me": the alert does not show again on this device. */
    suspend fun dismiss(recovery: BipeRecovery) {
        deviceAccount.dismissRecovery(recovery.id)
        statusFlow.value?.let { alertsFlow.value = alertsFor(it) }
    }

    suspend fun hasAppLockPin(): Boolean = deviceAccount.lock.isPinSet()

    suspend fun verifyAppLockPin(pin: String): Boolean = deviceAccount.lock.verifyPin(pin)

    /** User-facing message for a failure of this class. */
    fun messageOf(error: Throwable): String =
        (error as? BipeApiException)?.localizedMessage(stringProvider) ?: stringProvider.getString(R.string.bipe_error_generic)

    private suspend fun alertsFor(status: BipeAccountStatus): List<BipeRecovery> {
        val dismissed = deviceAccount.dismissedRecoveries()
        // A token sign-in older than this device's own sign-in is this device's own recovery, or happened before it existed.
        val since = deviceAccount.signedInAt(userId) ?: Long.MAX_VALUE
        return status.recoveries.filter { recovery ->
            recovery.id !in dismissed && when (recovery.state) {
                BipeRecoveryState.WAITING -> true
                BipeRecoveryState.COMPLETED ->
                    recovery.method == BipeRecoveryMethod.TOKEN &&
                        recovery.purpose == BipeRecoveryPurpose.ACCESS &&
                        recovery.createdAt.toEpochMilli() > since
                else -> false
            }
        }
    }
}
