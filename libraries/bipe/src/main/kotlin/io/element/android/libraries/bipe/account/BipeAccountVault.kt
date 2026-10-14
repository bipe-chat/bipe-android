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

@Serializable
internal data class BipeVaultData(
    /** The account whose hidden password is in [password]. */
    @SerialName("user_id") val userId: String? = null,
    /** The hidden MAS password (ADR 0059: this device only). Never shown, never logged. */
    @SerialName("password") val password: String? = null,
    /** The recovery token when no synced store is available (F-Droid build). */
    @SerialName("local_token") val localToken: String? = null,
    @SerialName("claim") val claim: BipePendingClaim? = null,
    /** Matrix ID to the time (ms) this device signed in to it. Older recoveries are not alerts for this device. */
    @SerialName("signed_in_at") val signedInAt: Map<String, Long> = emptyMap(),
    /** Recovery IDs the user answered with "It was me" on this device. */
    @SerialName("dismissed") val dismissedRecoveries: List<String> = emptyList(),
    @SerialName("pending_signup") val pendingSignup: BipePendingSignup? = null,
) {
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
    private suspend fun load(): BipeVaultData? = withContext(Dispatchers.IO) {
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
