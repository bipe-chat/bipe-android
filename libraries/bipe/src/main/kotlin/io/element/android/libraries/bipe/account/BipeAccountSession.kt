/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account

import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeAccountDeletion
import io.element.android.libraries.bipe.api.BipeAccountStatus
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeRecovery
import io.element.android.libraries.bipe.api.BipeRecoveryMethod
import io.element.android.libraries.bipe.api.BipeRecoveryPurpose
import io.element.android.libraries.bipe.api.BipeRecoveryState
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.libraries.bipe.settings.BipeDataExportFile
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.flow.Flow
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
    private val deletionAlertFlow = MutableStateFlow<BipeAccountDeletion?>(null)

    /** `null` until the first answer of `GET /v1/me/account`. */
    val status: StateFlow<BipeAccountStatus?> = statusFlow.asStateFlow()

    /** Recoveries to show on this device, newest first: waiting ones, and token sign-ins on another device. */
    val alerts: StateFlow<List<BipeRecovery>> = alertsFlow.asStateFlow()

    /** ADR 0140: a waiting deletion that another device asked for and nobody answered here. */
    val deletionAlert: StateFlow<BipeAccountDeletion?> = deletionAlertFlow.asStateFlow()

    suspend fun refresh(): Result<BipeAccountStatus> = runCatchingExceptions {
        deviceAccount.rememberSignedIn(userId)
        val status = BipeApiClient.withTokenRetry(accessToken) { apiClient.account(it) }
        BipeAccountCap.onServerValue(status.maxAccounts)
        statusFlow.value = status
        alertsFlow.value = alertsFor(status)
        deletionAlertFlow.value = status.deletion?.takeIf { it.alertId !in deviceAccount.dismissedRecoveries() }
        status
    }.onFailure { Timber.w(it, "Bipe: account state not loaded") }

    /** The inviter's Matrix ID after a personal-invite sign-up on this device, once (ADR 0034). */
    suspend fun takePendingInviter(): String? = deviceAccount.takePendingInviter(userId)

    /**
     * Sets or changes this account's PIN on the server, then the device PIN when the app lock is on and the device PIN
     * follows the account PIN (ADR 0086). The caller does the biometric check first.
     */
    suspend fun changePin(pin: String): Result<Unit> = runCatchingExceptions<Unit> {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.setPin(pin, it) }
        deviceAccount.adoptAccountPin(pin)
        refresh()
    }

    /**
     * The lock screen set a new device PIN. With one account on the phone the account PIN follows, so both stay one PIN.
     * With more accounts the device PIN belongs to the phone and no account PIN changes (ADR 0086).
     */
    suspend fun onAppLockPinSet(pin: String): Result<Unit> = runCatchingExceptions<Unit> {
        if (!deviceAccount.devicePinFollowsAccountPin()) return@runCatchingExceptions
        BipeApiClient.withTokenRetry(accessToken) { apiClient.setPin(pin, it) }
        refresh()
    }.onFailure { Timber.e(it, "Bipe: app lock PIN not sent to the server") }

    /** `true` when the account PIN is not the PIN that locks the app: the phone holds more than one account (ADR 0086). */
    suspend fun hasSeparateDevicePin(): Boolean = !deviceAccount.devicePinFollowsAccountPin()

    /**
     * A beta account (e-mail and own password) moves to a hidden password: PIN first, then `POST /v1/me/device-first`.
     * The user's own password stops working.
     */
    suspend fun moveToDeviceFirst(pin: String): Result<Unit> = runCatchingExceptions<Unit> {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.setPin(pin, it) }
        val credentials = BipeApiClient.withTokenRetry(accessToken) { apiClient.moveToDeviceFirst(it) }
        deviceAccount.onMovedToDeviceFirst(credentials)
        deviceAccount.adoptAccountPin(pin)
        refresh()
    }

    /** Adds or changes the recovery email. The address stays pending until the user opens the link the server sends. */
    suspend fun setRecoveryEmail(email: String): Result<Unit> = runCatchingExceptions<Unit> {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.setRecoveryEmail(email, it) }
        refresh()
    }.onFailure { Timber.e(it, "Bipe: recovery email not saved") }

    /** Removes the verified and the pending recovery email. */
    suspend fun removeRecoveryEmail(): Result<Unit> = runCatchingExceptions<Unit> {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.removeRecoveryEmail(it) }
        refresh()
    }.onFailure { Timber.e(it, "Bipe: recovery email not removed") }

    /**
     * Records that the user accepted the current legal texts (ADR 0113, `POST /v1/me/terms`). After the refresh the status
     * carries the new version even when the refresh failed, so the re-accept screen closes without waiting for the next poll.
     */
    suspend fun acceptTerms(): Result<Unit> = runCatchingExceptions {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.acceptTerms(BipeRegistries.LEGAL_VERSION, it) }
        refresh()
        statusFlow.value = statusFlow.value?.copy(termsVersion = BipeRegistries.LEGAL_VERSION)
    }.onFailure { Timber.e(it, "Bipe: terms acceptance not saved") }

    /** "Baixar meus dados" (#364): the server data as one JSON file. The caller proves the owner first. */
    suspend fun exportData(): Result<BipeDataExportFile> = runCatchingExceptions {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.export(it) }
    }

    /** "Cancel, it was not me": stops a waiting recovery. */
    suspend fun cancelRecovery(recovery: BipeRecovery): Result<Unit> = runCatchingExceptions<Unit> {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.cancelRecovery(recovery.id, it) }
        deviceAccount.dismissRecovery(recovery.id)
        refresh()
    }

    /** ADR 0140: the server checks the PIN; the caller ran the owner check. This device gets no alert for its own request. */
    suspend fun requestDeletion(pin: String, redactMessages: Boolean): Result<Unit> = runCatchingExceptions<Unit> {
        val deletion = BipeApiClient.withTokenRetry(accessToken) { apiClient.requestDeletion(pin, redactMessages, it) }
        deviceAccount.dismissRecovery(deletion.alertId)
        refresh()
    }

    suspend fun cancelDeletion(): Result<Unit> = runCatchingExceptions<Unit> {
        BipeApiClient.withTokenRetry(accessToken) { apiClient.cancelDeletion(it) }
        refresh()
    }

    /** "It was me" on the deletion alert: it does not show again on this device. */
    suspend fun dismiss(deletion: BipeAccountDeletion) {
        deviceAccount.dismissRecovery(deletion.alertId)
        refresh()
    }

    /** "It was me": the alert does not show again on this device. */
    suspend fun dismiss(recovery: BipeRecovery) {
        deviceAccount.dismissRecovery(recovery.id)
        statusFlow.value?.let { alertsFlow.value = alertsFor(it) }
    }

    /** `true` when this account's recovery token is in the synced store (Block Store). */
    suspend fun hasSyncedRecoveryToken(): Boolean = deviceAccount.hasSyncedRecoveryToken(userId)

    suspend fun hasAppLockPin(): Boolean = deviceAccount.lock.isPinSet()

    /** `true` while the app lock is on. The lock is off by default; "Bloquear o app" in Sua conta turns it on. */
    fun appLockOnFlow(): Flow<Boolean> = deviceAccount.lock.pinSetFlow()

    /** Turns the app lock off: removes the device PIN and the biometric unlock. The caller proves the owner first. */
    suspend fun turnOffAppLock(): Result<Unit> = runCatchingExceptions { deviceAccount.lock.removePin() }
        .onFailure { Timber.e(it, "Bipe: app lock not turned off") }

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
