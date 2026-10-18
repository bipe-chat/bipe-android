/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipeblockstore

import android.content.Context
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.tasks.Task
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import io.element.android.libraries.bipe.account.BipeCloudTokenStore
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The recovery tokens in Google Block Store, backed up to the Google account (`shouldBackupToCloud`). A new phone signed in
 * to the same Google account gets the tokens back at setup or on reinstall. Google backs the data up end-to-end encrypted
 * only when the phone has a screen lock; without one, Block Store keeps it on the device.
 *
 * One entry per account (ADR 0086): the key holds a hash of the Matrix ID; the value holds the Matrix ID, a line break and
 * the token. The entry [LEGACY_KEY] from before #265 holds a bare token. Only tokens go here. The hidden password stays on
 * the device (ADR 0059).
 */
@ContributesIntoSet(AppScope::class, binding = binding<BipeCloudTokenStore>())
class BlockStoreTokenStore(
    @ApplicationContext context: Context,
) : BipeCloudTokenStore {
    private val client = Blockstore.getClient(context)

    override suspend fun readAll(): Map<String?, String>? = try {
        val request = RetrieveBytesRequest.Builder().setRetrieveAll(true).build()
        val entries = client.retrieveBytes(request).await().blockstoreDataMap
        buildMap<String?, String> {
            entries[LEGACY_KEY]?.bytes?.decodeToString()?.takeIf { it.isNotEmpty() }?.let { put(null, it) }
            entries.filterKeys { it.startsWith(KEY_PREFIX) }.values.forEach { entry ->
                val value = entry.bytes.decodeToString()
                val userId = value.substringBefore(SEPARATOR, missingDelimiterValue = "")
                val token = value.substringAfter(SEPARATOR, missingDelimiterValue = "")
                if (userId.isNotEmpty() && token.isNotEmpty()) put(userId, token)
            }
        }
    } catch (error: Exception) {
        Timber.w(error, "Bipe: Block Store read failed")
        null
    }

    override suspend fun write(userId: String, token: String): Boolean = try {
        val data = StoreBytesData.Builder()
            .setKey(key(userId))
            .setBytes("$userId$SEPARATOR$token".encodeToByteArray())
            .setShouldBackupToCloud(true)
            .build()
        client.storeBytes(data).await()
        true
    } catch (error: Exception) {
        Timber.w(error, "Bipe: Block Store write failed")
        false
    }

    override suspend fun delete(userId: String?) {
        try {
            val key = if (userId == null) LEGACY_KEY else key(userId)
            client.deleteBytes(DeleteBytesRequest.Builder().setKeys(listOf(key)).build()).await()
        } catch (error: Exception) {
            Timber.w(error, "Bipe: Block Store delete failed")
        }
    }

    /** A Matrix ID has characters that a Block Store key may not; a hash has none. */
    private fun key(userId: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(userId.encodeToByteArray())
        return KEY_PREFIX + hash.take(16).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val LEGACY_KEY = "chat.bipe.recovery_token"
        const val KEY_PREFIX = "chat.bipe.recovery_token."

        /** A Matrix ID never holds a line break. */
        const val SEPARATOR = "\n"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
