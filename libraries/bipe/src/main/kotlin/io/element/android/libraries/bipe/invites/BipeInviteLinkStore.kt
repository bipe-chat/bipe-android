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
 * Links and notes of personal invites created on this device, per account, by invite ID. Port of the links and notes parts of
 * BipeInviteStore on iOS. The server keeps only a hash of each code, so the link exists only where it was created. Links are bearer
 * secrets. A note (ADR 0136) is the first message for the person who joins, personal text the server never receives. Each kind is
 * one file in `noBackupFilesDir`, encrypted with an Android Keystore key (same scheme as BipeAccountVault, own file and key).
 */
@SingleIn(AppScope::class)
@Inject
class BipeInviteLinkStore(
    @ApplicationContext private val context: Context,
    private val secretKeyRepository: SecretKeyRepository,
    private val encryptionDecryptionService: EncryptionDecryptionService,
) {
    private data class Kind(val fileName: String, val keyAlias: String)

    private val mutex = Mutex()

    /** Links of invites created on this device by [userId], by invite ID. */
    suspend fun links(userId: String): Map<String, String> = mutex.withLock { load(LINKS)[userId].orEmpty() }

    suspend fun saveLink(userId: String, inviteId: String, url: String) = update(LINKS, userId) { it + (inviteId to url) }

    /** Drops links whose invite is no longer pending (used, expired or revoked). */
    suspend fun keepLinks(userId: String, pendingInviteIds: Set<String>) = update(LINKS, userId) { links ->
        links.filterKeys { it in pendingInviteIds }
    }

    /** Notes of invites created on this device by [userId], by invite ID. */
    suspend fun notes(userId: String): Map<String, String> = mutex.withLock { load(NOTES)[userId].orEmpty() }

    /** Stores the note for an invite. Text with only spaces is no note. */
    suspend fun saveNote(userId: String, inviteId: String, note: String) {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return
        update(NOTES, userId) { it + (inviteId to trimmed) }
    }

    /** Removes the note of an invite. Returns the note, or `null` when there was none, so one caller at a time gets it. */
    suspend fun takeNote(userId: String, inviteId: String): String? = mutex.withLock {
        val all = load(NOTES)
        val current = all[userId].orEmpty()
        val note = current[inviteId] ?: return@withLock null
        val updated = current - inviteId
        save(NOTES, if (updated.isEmpty()) all - userId else all + (userId to updated))
        note
    }

    private suspend fun update(kind: Kind, userId: String, transform: (Map<String, String>) -> Map<String, String>) = mutex.withLock {
        val all = load(kind)
        val current = all[userId].orEmpty()
        val updated = transform(current)
        if (updated == current) return@withLock
        save(kind, if (updated.isEmpty()) all - userId else all + (userId to updated))
    }

    private fun file(kind: Kind) = File(context.noBackupFilesDir, kind.fileName)

    /** An unreadable file loses only links or notes: the invites still show, as made on another device. */
    private suspend fun load(kind: Kind): Map<String, Map<String, String>> = withContext(Dispatchers.IO) {
        val file = file(kind)
        if (!file.exists()) return@withContext emptyMap()
        try {
            val key = secretKeyRepository.getOrCreateKey(kind.keyAlias, false)
            val bytes = encryptionDecryptionService.decrypt(key, EncryptionResult.fromBase64(file.readText()))
            Json.decodeFromString(serializer, bytes.decodeToString())
        } catch (error: Exception) {
            Timber.e(error, "Bipe invite file ${kind.fileName} unreadable, starting empty")
            emptyMap()
        }
    }

    private suspend fun save(kind: Kind, data: Map<String, Map<String, String>>) = withContext(Dispatchers.IO) {
        val file = file(kind)
        if (data.isEmpty()) {
            file.delete()
            return@withContext
        }
        val key = secretKeyRepository.getOrCreateKey(kind.keyAlias, false)
        val encrypted = encryptionDecryptionService.encrypt(key, Json.encodeToString(serializer, data).encodeToByteArray())
        val tmp = File(file.parentFile, "${kind.fileName}.tmp")
        tmp.writeText(encrypted.toBase64())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        val LINKS = Kind(fileName = "bipe-invite-links", keyAlias = "bipe.SECRET_KEY_ALIAS_INVITE_LINKS")
        val NOTES = Kind(fileName = "bipe-invite-notes", keyAlias = "bipe.SECRET_KEY_ALIAS_INVITE_NOTES")
        val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))
    }
}
