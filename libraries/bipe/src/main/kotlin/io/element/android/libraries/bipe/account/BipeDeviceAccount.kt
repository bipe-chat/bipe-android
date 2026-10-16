/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.BipeConfig
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiErrorCode
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeCredentials
import io.element.android.libraries.bipe.api.BipeRecoveryClaim
import io.element.android.libraries.bipe.api.BipeRecoveryState
import io.element.android.libraries.bipe.api.BipeSignupRequest
import io.element.android.libraries.bipe.api.BipeSignupResponse
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.observer.SessionListener
import io.element.android.libraries.sessionstorage.api.observer.SessionObserver
import android.util.Base64
import kotlinx.coroutines.delay
import timber.log.Timber
import java.security.SecureRandom

/**
 * Device-first accounts on this device (ADR 0057, ADR 0059): sign-up, sign-in with the hidden password, and the recovery
 * paths that end in a sign-in. The iOS counterpart is `BipeDeviceAccount` in BipeKit.
 *
 * Sign-in uses `m.login.password` (user ID and hidden password) through the Rust SDK. MAS answers it through its
 * compatibility layer (`passwords.enabled: true`), so no browser opens. The SDK picks OAuth only when the app asks
 * for an OAuth URL; [MatrixAuthenticationService.login] never does.
 */
@SingleIn(AppScope::class)
@Inject
class BipeDeviceAccount(
    private val apiClient: BipeApiClient,
    private val authenticationService: MatrixAuthenticationService,
    private val vault: BipeAccountVault,
    private val tokenStore: BipeRecoveryTokenStore,
    private val appLock: BipeAppLock,
    sessionObserver: SessionObserver,
) {
    init {
        // A sign-out (by the user, by the lock screen after wrong PINs, or by the server) removes the hidden password.
        // The way back is then the synced token and the PIN, which the server counts (ADR 0059).
        sessionObserver.addListener(object : SessionListener {
            override suspend fun onSessionDeleted(userId: String, wasLastSession: Boolean) {
                vault.update { data ->
                    if (data.userId == userId) data.copy(userId = null, password = null, signedInAt = data.signedInAt - userId) else data
                }
            }
        })
    }

    /** `false` in the F-Droid build: the recovery token does not sync. */
    val isTokenSynced: Boolean get() = tokenStore.isSynced

    /**
     * Creates a device-first account and signs in. The request has no password; the server makes one.
     * The password stays on this device; the token goes to the synced store.
     */
    suspend fun signUp(request: BipeSignupRequest, pin: String): Result<BipeSignupResponse> = runCatchingExceptions {
        require(request.password == null) { "Device-first sign-up sends no password" }
        // Stored before the first request goes out, so a lost answer can be fetched again (ADR 0094).
        val pending = pendingSignup(pin)
        val accountPin = pending.pin ?: pin
        val response = try {
            signupRetryingLostAnswers(request.copy(pin = accountPin, lang = BipeApiClient.emailLanguage(), signupKey = pending.key))
        } catch (error: BipeApiException.Server) {
            if (error.code != BipeApiErrorCode.RATE_LIMITED && error.code != BipeApiErrorCode.INTERNAL) {
                // A field error: the server made nothing, so the next try takes the PIN from the form again.
                // A limit or a server failure proves nothing, so the PIN of the first try stays.
                vault.update { data -> data.copy(pendingSignup = data.pendingSignup?.copy(pin = null)) }
            }
            throw error
        }
        val password = response.password ?: throw BipeApiException.UnexpectedResponse(statusCode = 201)
        val token = response.recoveryToken ?: throw BipeApiException.UnexpectedResponse(statusCode = 201)
        Timber.i("Bipe device-first account created: ${response.userId}")
        storeCredentials(BipeCredentials(response.userId, password, token))
        rememberInviter(response)
        signIn(response.userId, password, accountPin).getOrThrow()
        vault.update { it.copy(pendingSignup = null) }
        response
    }

    /**
     * Finishes a sign-up whose answer never reached this device (ADR 0094). `null` when there is nothing to finish.
     * The account PIN of the first try becomes the app lock PIN.
     */
    suspend fun resumePendingSignup(): Result<SessionId>? {
        val pending = vault.read().pendingSignup ?: return null
        val response = try {
            apiClient.resumeSignup(pending.key)
        } catch (error: BipeApiException) {
            if ((error as? BipeApiException.Server)?.code == BipeApiErrorCode.NOT_FOUND) vault.update { it.copy(pendingSignup = null) }
            Timber.w("Bipe: could not resume a pending sign-up: $error")
            return null
        }
        val password = response.password ?: return null
        val token = response.recoveryToken ?: return null
        Timber.i("Bipe: finishing the sign-up of ${response.userId}")
        storeCredentials(BipeCredentials(response.userId, password, token))
        rememberInviter(response)
        return signIn(response.userId, password, pending.pin).onSuccess { vault.update { it.copy(pendingSignup = null) } }
    }

    private suspend fun pendingSignup(pin: String): BipePendingSignup {
        vault.read().pendingSignup?.let { pending ->
            if (pending.pin != null) return pending
            return pending.copy(pin = pin).also { updated -> vault.update { it.copy(pendingSignup = updated) } }
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pending = BipePendingSignup(Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), pin)
        vault.update { it.copy(pendingSignup = pending) }
        return pending
    }

    /**
     * Mobile data drops often. A try without an answer may still have made the account, and the same key returns it, so a
     * few quiet retries are safe (ADR 0094).
     */
    private suspend fun signupRetryingLostAnswers(request: BipeSignupRequest): BipeSignupResponse {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return apiClient.signup(request)
            } catch (error: BipeApiException) {
                val retryable = when (error) {
                    is BipeApiException.Network, is BipeApiException.UnexpectedResponse -> true
                    is BipeApiException.Server -> error.isSignupInProgress || error.code == BipeApiErrorCode.INTERNAL
                }
                if (!retryable || attempt >= 4) throw error
                Timber.i("Bipe sign-up try $attempt got no answer, trying again")
                delay(2_000L * attempt)
            }
        }
    }

    private val BipeApiException.Server.isSignupInProgress: Boolean
        get() = code == BipeApiErrorCode.RATE_LIMITED && reason == "signup_in_progress"

    /** Kept before the sign-in, so the first session finds it (ADR 0034). */
    private suspend fun rememberInviter(response: BipeSignupResponse) {
        val inviterUserId = response.inviterUserId ?: return
        vault.update { it.copy(pendingInviters = it.pendingInviters + (response.userId to inviterUserId)) }
    }

    /** The inviter of [userId] after a personal-invite sign-up, once: the stored value is removed (ADR 0034). */
    internal suspend fun takePendingInviter(userId: String): String? {
        val inviterUserId = vault.read().pendingInviters[userId] ?: return null
        vault.update { it.copy(pendingInviters = it.pendingInviters - userId) }
        return inviterUserId
    }

    /** Keeps new credentials: the password on this device, the token in the synced store. */
    suspend fun storeCredentials(credentials: BipeCredentials) {
        vault.update { it.copy(userId = credentials.userId, password = credentials.password) }
        tokenStore.write(credentials.recoveryToken)
    }

    /**
     * Signs in with `m.login.password` (the Matrix user ID and the hidden password). [pin], when the user just typed it,
     * becomes the app lock PIN.
     */
    suspend fun signIn(userId: String, password: String, pin: String?): Result<SessionId> = runCatchingExceptions {
        authenticationService.setHomeserver(BipeConfig.HOMESERVER_BASE_URL).getOrThrow()
        val sessionId = authenticationService.login(userId, password).getOrThrow()
        vault.update { it.copy(signedInAt = it.signedInAt + (userId to System.currentTimeMillis())) }
        if (pin != null) {
            runCatchingExceptions { appLock.setPin(pin) }.onFailure { Timber.e(it, "Bipe: app lock PIN not set") }
        }
        sessionId
    }

    /** App start without a session: signs in with the password this device keeps, if any. `null` when it keeps none. */
    suspend fun signInSilently(): Result<SessionId>? {
        val data = vault.read()
        val userId = data.userId ?: return null
        val password = data.password ?: return null
        Timber.i("Bipe: silent sign-in for $userId")
        // The app closed between sign-up and sign-in: the pending sign-up still holds the PIN for the app lock.
        return signIn(userId, password, pin = data.pendingSignup?.pin).onSuccess {
            if (data.pendingSignup != null) vault.update { it.copy(pendingSignup = null) }
        }
    }

    /** `true` when the synced store has a recovery token (e.g. a new phone with the same Google account). */
    suspend fun hasRecoveryToken(): Boolean = tokenStore.read() != null

    /** Forgets the synced token, e.g. when the user wants a new account on this phone. */
    suspend fun forgetRecoveryToken() = tokenStore.delete()

    /**
     * The synced token, then the PIN (ADR 0059). On success the server sets a new password and token, and this device
     * signs in. An unknown token is deleted from the store.
     */
    suspend fun recoverWithToken(pin: String): Result<SessionId> = runCatchingExceptions {
        val token = tokenStore.read() ?: throw BipeApiException.Server(BipeApiErrorCode.RECOVERY_INVALID, reason = null, statusCode = 403)
        val credentials = try {
            apiClient.recoverWithToken(token, pin)
        } catch (error: BipeApiException.Server) {
            if (error.code == BipeApiErrorCode.RECOVERY_INVALID) tokenStore.delete()
            throw error
        }
        storeCredentials(credentials)
        signIn(credentials.userId, credentials.password, pin).getOrThrow()
    }

    /** Asks the server to send a recovery link. The answer never tells whether the address is known. */
    suspend fun requestEmailRecovery(email: String): Result<Unit> = runCatchingExceptions { apiClient.requestEmailRecovery(email.trim()) }

    /**
     * The link from the email, then the PIN ([pin]) or a new PIN ([newPin]). Starts the delay and keeps the claim secret
     * on this device.
     */
    suspend fun confirmEmailRecovery(linkToken: String, pin: String?, newPin: String?): Result<BipePendingClaim> = runCatchingExceptions {
        val started = apiClient.confirmEmailRecovery(linkToken, pin, newPin)
        val claim = BipePendingClaim(
            recoveryId = started.recoveryId,
            claimSecret = started.claimSecret,
            purposeId = started.purposeId,
            readyAtMillis = started.readyAt.toEpochMilli(),
        )
        vault.update { it.copy(claim = claim) }
        claim
    }

    suspend fun pendingClaim(): BipePendingClaim? = vault.read().claim

    suspend fun forgetPendingClaim() = vault.update { it.copy(claim = null) }

    /**
     * Polls the waiting recovery. After the delay, an access recovery returns credentials once: this device keeps them
     * and signs in. A recovery that ended without access (cancelled, expired, PIN reset done) is forgotten.
     */
    suspend fun claim(): Result<BipeRecoveryClaim?> = runCatchingExceptions {
        val pending = vault.read().claim ?: return@runCatchingExceptions null
        val result = try {
            apiClient.claimRecovery(pending.recoveryId, pending.claimSecret)
        } catch (error: BipeApiException.Server) {
            if (error.code == BipeApiErrorCode.RECOVERY_INVALID) forgetPendingClaim()
            throw error
        }
        val credentials = result.credentials
        if (credentials != null) {
            storeCredentials(credentials)
            forgetPendingClaim()
            signIn(credentials.userId, credentials.password, pin = null).getOrThrow()
        } else if (result.state != BipeRecoveryState.WAITING) {
            forgetPendingClaim()
        }
        result
    }

    /** The credentials of a beta account that just moved to device-first ([BipeAccountSession.moveToDeviceFirst]). */
    internal suspend fun onMovedToDeviceFirst(credentials: BipeCredentials) {
        storeCredentials(credentials)
        vault.update { it.copy(signedInAt = it.signedInAt + (credentials.userId to (it.signedInAt[credentials.userId] ?: System.currentTimeMillis()))) }
    }

    internal suspend fun signedInAt(userId: String): Long? = vault.read().signedInAt[userId]

    /** Marks the time when this device signed in by another way (QR code, beta e-mail login), the first time it sees the account. */
    internal suspend fun rememberSignedIn(userId: String) {
        vault.update { if (userId in it.signedInAt) it else it.copy(signedInAt = it.signedInAt + (userId to System.currentTimeMillis())) }
    }

    internal suspend fun dismissedRecoveries(): Set<String> = vault.read().dismissedRecoveries.toSet()

    internal suspend fun dismissRecovery(id: String) = vault.update {
        it.copy(dismissedRecoveries = (it.dismissedRecoveries + id).takeLast(MAX_DISMISSED))
    }

    internal val lock: BipeAppLock get() = appLock

    private companion object {
        const val MAX_DISMISSED = 50
    }
}
