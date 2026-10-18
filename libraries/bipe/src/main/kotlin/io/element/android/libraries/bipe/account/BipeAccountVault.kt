/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.cryptography.api.EncryptionDecryptionService
import io.element.android.libraries.cryptography.api.EncryptionResult
import io.element.android.libraries.cryptography.api.SecretKeyRepository
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/** A recovery by email that waits for its delay on this device (ADR 0057). The claim secret proves this device. */
@Serializable
data class BipePendingClaim(
    @SerialName("recovery_id") val recoveryId: String,
    @SerialName("claim_secret") val claimSecret: String,
    @SerialName("purpose") val purposeId: String,
    @SerialName("ready_at") val readyAtMillis: Long,
) {
    override fun toString(): String = "BipePendingClaim($recoveryId)"
}

/**
 * A sign-up whose credentials are not stored yet (ADR 0094). The key makes a retry return the same account. The PIN is the
 * one the first try sent, so the app lock gets the PIN the account really has.
 */
@Serializable
internal data class BipePendingSignup(
    @SerialName("key") val key: String,
    @SerialName("pin") val pin: String? = null,
) {
    override fun toString(): String = "BipePendingSignup"
}

/** The device-only secrets of one account. Never shown, never logged. */
@Serializable
internal data class BipeVaultAccount(
    /** The hidden MAS password (ADR 0059: this device only). */
    @SerialName("password") val password: String? = null,
    /** The recovery token when no synced store is available (F-Droid build). */
    @SerialName("local_token") val localToken: String? = null,
) {
    val isEmpty: Boolean get() = password == null && localToken == null

    override fun toString(): String = "BipeVaultAccount"
}

@Serializable
internal data class BipeVaultData(
    /**
     * Matrix ID to that account's secrets (ADR 0086: a second account never replaces the first). The order is the order
     * of the last stored password: the last entry is the newest.
     */
    @SerialName("accounts") val accounts: Map<String, BipeVaultAccount> = emptyMap(),
    /** Before #265: the one account. Read once by [migrated], never written. */
    @SerialName("user_id") val legacyUserId: String? = null,
    @SerialName("password") val legacyPassword: String? = null,
    /** A recovery token from before #265 whose account is unknown (F-Droid build): the account had signed out. */
    @SerialName("local_token") val legacyLocalToken: String? = null,
    /** The account of the synced token from before #265, until [BipeRecoveryTokenStore] moves that token to its own key. */
    @SerialName("legacy_token_owner") val legacyTokenOwner: String? = null,
    @SerialName("claim") val claim: BipePendingClaim? = null,
    /** Matrix ID to the time (ms) this device signed in to it. Older recoveries are not alerts for this device. */
    @SerialName("signed_in_at") val signedInAt: Map<String, Long> = emptyMap(),
    /** Recovery IDs the user answered with "It was me" on this device. */
    @SerialName("dismissed") val dismissedRecoveries: List<String> = emptyList(),
    @SerialName("pending_signup") val pendingSignup: BipePendingSignup? = null,
    /** New account's Matrix ID to its inviter's Matrix ID, until the first session opens the chat (ADR 0034). */
    @SerialName("pending_inviters") val pendingInviters: Map<String, String> = emptyMap(),
    /** Account's Matrix ID to its inviter's Matrix ID, until the user sends the inviter a first message (ADR 0034). */
    @SerialName("inviter_greetings") val inviterGreetings: Map<String, String> = emptyMap(),
) {
    /** Changes the secrets of [userId] in place. An account with no secret left is removed. */
    fun updatingAccount(userId: String, transform: (BipeVaultAccount) -> BipeVaultAccount): BipeVaultData {
        val account = transform(accounts[userId] ?: BipeVaultAccount())
        return copy(accounts = if (account.isEmpty) accounts - userId else accounts + (userId to account))
    }

    /** Stores the password of [userId] and makes it the newest account. */
    fun withPassword(userId: String, password: String): BipeVaultData {
        val account = (accounts[userId] ?: BipeVaultAccount()).copy(password = password)
        return copy(accounts = accounts - userId + (userId to account))
    }

    /** The data of a vault written before #265 (one account) in the per-account shape. `this` when nothing is old. */
    fun migrated(): BipeVaultData {
        // Before #265 a sign-out cleared the user ID and the password together, so a password never lacks its user ID.
        val userId = legacyUserId ?: return this
        val account = BipeVaultAccount(password = legacyPassword, localToken = legacyLocalToken)
        return copy(
            accounts = if (account.isEmpty) accounts else accounts + (userId to account),
            legacyUserId = null,
            legacyPassword = null,
            legacyLocalToken = null,
            legacyTokenOwner = userId,
        )
    }

    override fun toString(): String = "BipeVaultData"
}

/**
 * Device-only secrets of device-first accounts: one file in `noBackupFilesDir`, encrypted with an Android Keystore key.
 * Android excludes that folder from cloud backup and from device-to-device transfer, and the key cannot leave the device.
 */
@SingleIn(AppScope::class)
@Inject
class BipeAccountVault(
    @ApplicationContext private val context: Context,
    private val secretKeyRepository: SecretKeyRepository,
    private val encryptionDecryptionService: EncryptionDecryptionService,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val file: File get() = File(context.noBackupFilesDir, FILE_NAME)

    internal suspend fun read(): BipeVaultData = mutex.withLock { load() ?: BipeVaultData() }

    /** Skips the write when the file cannot be read now: writing over it would erase the hidden password. */
    internal suspend fun update(transform: (BipeVaultData) -> BipeVaultData) = mutex.withLock {
        val current = load()
        if (current == null) {
            Timber.e("Bipe vault: not written, the file cannot be read now")
            return@withLock
        }
        save(transform(current))
    }

    /** `null` when the file exists but cannot be read now (a Keystore error that may pass). */
    private suspend fun load(): BipeVaultData? {
        val data = decode() ?: return null
        val migrated = data.migrated()
        if (migrated != data) {
            // A failed write keeps the old file; the next read migrates it again.
            try {
                save(migrated)
                Timber.i("Bipe vault: moved the account secrets to per-account entries")
            } catch (error: Exception) {
                Timber.e(error, "Bipe vault: migration not written")
            }
        }
        return migrated
    }

    private suspend fun decode(): BipeVaultData? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext BipeVaultData()
        try {
            val key = secretKeyRepository.getOrCreateKey(KEY_ALIAS, false)
            val bytes = encryptionDecryptionService.decrypt(key, EncryptionResult.fromBase64(file.readText()))
            json.decodeFromString(BipeVaultData.serializer(), bytes.decodeToString())
        } catch (error: Exception) {
            if (error.isKeyLost()) {
                // A lost Keystore key (e.g. after a device reset) makes the file unreadable for good. Start empty.
                Timber.e(error, "Bipe vault key lost, starting empty")
                file.delete()
                BipeVaultData()
            } else {
                Timber.e(error, "Bipe vault unreadable now; kept")
                null
            }
        }
    }

    /** Only these mean the file can never be read again. Other Keystore errors happen on some phones and pass. */
    private fun Throwable.isKeyLost(): Boolean = generateSequence(this) { it.cause }.any {
        it is javax.crypto.AEADBadTagException ||
            it is android.security.keystore.KeyPermanentlyInvalidatedException ||
            it is java.security.UnrecoverableKeyException ||
            it is kotlinx.serialization.SerializationException ||
            it is IllegalArgumentException
    }

    private suspend fun save(data: BipeVaultData) = withContext(Dispatchers.IO) {
        if (data == BipeVaultData()) {
            file.delete()
            return@withContext
        }
        val key = secretKeyRepository.getOrCreateKey(KEY_ALIAS, false)
        val encrypted = encryptionDecryptionService.encrypt(key, json.encodeToString(BipeVaultData.serializer(), data).encodeToByteArray())
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(encrypted.toBase64())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val FILE_NAME = "bipe-account"
        const val KEY_ALIAS = "bipe.SECRET_KEY_ALIAS_ACCOUNT"
    }
}
