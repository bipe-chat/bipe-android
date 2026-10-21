/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.company

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.components.avatar.BipeCompanyAccounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Account types for one user session (ADR 0132): a company account's avatar is a rounded square, verified or not.
 *
 * Like [BipeCompanySeals], [isCompanyNow] returns the cached answer and fetches `GET /v1/accounts/{userId}/type`
 * when the entry is stale. It fails closed: unknown, 404 or an error give a person (a circle). The iOS store is `BipeCompanySeals.isCompany`.
 */
class BipeAccountTypes(
    private val apiClient: BipeApiClient,
    /** Only Matrix IDs on this server have a Bipe account type. */
    private val serverName: String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : BipeCompanyAccounts {
    private data class Entry(val isCompany: Boolean, val expiryMillis: Long)

    private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    private val lock = Any()
    private val inFlight = mutableSetOf<String>()

    /** The cached type of [userId]. Queues a fetch when the entry is stale. */
    fun isCompanyNow(userId: String): Boolean {
        if (!userId.startsWith("@") || !userId.endsWith(":$serverName")) return false
        val entry = entries.value[userId]
        if (entry == null || entry.expiryMillis <= clock()) fetch(userId)
        return entry?.isCompany ?: false
    }

    @Composable
    override fun isCompany(userId: String): Boolean {
        val current by entries.collectAsState()
        return remember(userId, current) { isCompanyNow(userId) }
    }

    private fun fetch(userId: String) {
        synchronized(lock) {
            if (!inFlight.add(userId)) return
        }
        scope.launch {
            try {
                val entry = runCatchingExceptions { apiClient.accountType(userId) }.fold(
                    onSuccess = { type -> Entry(type == "company", clock() + TTL_MILLIS) },
                    onFailure = { error ->
                        Timber.w(error, "Bipe account type lookup failed for %s", userId)
                        Entry(false, clock() + ERROR_TTL_MILLIS)
                    },
                )
                entries.update { it + (userId to entry) }
            } finally {
                synchronized(lock) { inFlight.remove(userId) }
            }
        }
    }

    private companion object {
        // The type never changes after sign-up; the server caches it for a day.
        val TTL_MILLIS = 24.hours.inWholeMilliseconds
        val ERROR_TTL_MILLIS = 2.minutes.inWholeMilliseconds
    }
}
