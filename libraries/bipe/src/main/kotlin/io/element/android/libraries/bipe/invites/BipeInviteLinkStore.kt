/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/**
 * Links of personal invites created on this device, per account, by invite ID. Port of the links part of BipeInviteStore on iOS.
 * The server keeps only a hash of each code, so the link exists only where it was created. Links are bearer secrets: one file in
 * `noBackupFilesDir`, encrypted with an Android Keystore key (same scheme as BipeAccountVault, own file and key).
 */
@SingleIn(AppScope::class)
@Inject
class BipeInviteLinkStore(
    @ApplicationContext private val context: Context,
    private val secretKeyRepository: SecretKeyRepository,
    private val encryptionDecryptionService: EncryptionDecryptionService,
) {
    private val mutex = Mutex()
    private val file: File get() = File(context.noBackupFilesDir, FILE_NAME)

    /** Links of invites created on this device by [userId], by invite ID. */
    suspend fun links(userId: String): Map<String, String> = mutex.withLock { load()[userId].orEmpty() }

    suspend fun saveLink(userId: String, inviteId: String, url: String) = update(userId) { it + (inviteId to url) }

    /** Drops links whose invite is no longer pending (used, expired or revoked). */
    suspend fun keepLinks(userId: String, pendingInviteIds: Set<String>) = update(userId) { links ->
        links.filterKeys { it in pendingInviteIds }
    }

    private suspend fun update(userId: String, transform: (Map<String, String>) -> Map<String, String>) = mutex.withLock {
        val all = load()
        val current = all[userId].orEmpty()
        val updated = transform(current)
        if (updated == current) return@withLock
        save(if (updated.isEmpty()) all - userId else all + (userId to updated))
    }

    /** An unreadable file loses only links: the invites still show, as made on another device. */
    private suspend fun load(): Map<String, Map<String, String>> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyMap()
        try {
            val key = secretKeyRepository.getOrCreateKey(KEY_ALIAS, false)
            val bytes = encryptionDecryptionService.decrypt(key, EncryptionResult.fromBase64(file.readText()))
            Json.decodeFromString(serializer, bytes.decodeToString())
        } catch (error: Exception) {
            Timber.e(error, "Bipe invite links unreadable, starting empty")
            emptyMap()
        }
    }

    private suspend fun save(data: Map<String, Map<String, String>>) = withContext(Dispatchers.IO) {
        if (data.isEmpty()) {
            file.delete()
            return@withContext
        }
        val key = secretKeyRepository.getOrCreateKey(KEY_ALIAS, false)
        val encrypted = encryptionDecryptionService.encrypt(key, Json.encodeToString(serializer, data).encodeToByteArray())
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(encrypted.toBase64())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val FILE_NAME = "bipe-invite-links"
        const val KEY_ALIAS = "bipe.SECRET_KEY_ALIAS_INVITE_LINKS"
        val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))
    }
}
