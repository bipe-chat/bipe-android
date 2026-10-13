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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The recovery token in Google Block Store, backed up to the Google account (`shouldBackupToCloud`). A new phone signed in
 * to the same Google account gets the token back at setup or on reinstall. Google backs the data up end-to-end encrypted
 * only when the phone has a screen lock; without one, Block Store keeps it on the device.
 *
 * Only the token goes here. The hidden password stays on the device (ADR 0059).
 */
@ContributesIntoSet(AppScope::class, binding = binding<BipeCloudTokenStore>())
class BlockStoreTokenStore(
    @ApplicationContext context: Context,
) : BipeCloudTokenStore {
    private val client = Blockstore.getClient(context)

    override suspend fun read(): String? = try {
        val request = RetrieveBytesRequest.Builder().setKeys(listOf(KEY)).build()
        client.retrieveBytes(request).await().blockstoreDataMap[KEY]?.bytes?.decodeToString()?.takeIf { it.isNotEmpty() }
    } catch (error: Exception) {
        Timber.w(error, "Bipe: Block Store read failed")
        null
    }

    override suspend fun write(token: String): Boolean = try {
        val data = StoreBytesData.Builder()
            .setKey(KEY)
            .setBytes(token.encodeToByteArray())
            .setShouldBackupToCloud(true)
            .build()
        client.storeBytes(data).await()
        true
    } catch (error: Exception) {
        Timber.w(error, "Bipe: Block Store write failed")
        false
    }

    override suspend fun delete() {
        try {
            client.deleteBytes(DeleteBytesRequest.Builder().setKeys(listOf(KEY)).build()).await()
        } catch (error: Exception) {
            Timber.w(error, "Bipe: Block Store delete failed")
        }
    }

    private companion object {
        const val KEY = "chat.bipe.recovery_token"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
