/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.threads

import android.content.Context

/**
 * Threads left Labs and are on by default (15 Oct 2026). A session cache built with threads off is cleared once,
 * the same way the old Labs toggle did. iOS: `AppCoordinator.rebuildEventCacheForThreadsIfNeeded`.
 */
object BipeThreadsCacheRebuild {
    private const val FILE_NAME = "bipe_threads_cache_rebuild"
    private const val KEY_PENDING_SESSION_IDS = "pending_session_ids"

    /** On an existing install, every stored session has a cache built without threads. */
    fun markPending(context: Context, sessionIds: Collection<String>) {
        preferences(context).edit().putStringSet(KEY_PENDING_SESSION_IDS, sessionIds.toSet()).apply()
    }

    /** True once for each pending session. */
    fun takePending(context: Context, sessionId: String): Boolean {
        val preferences = preferences(context)
        val pending = preferences.getStringSet(KEY_PENDING_SESSION_IDS, null).orEmpty()
        if (sessionId !in pending) return false
        preferences.edit().putStringSet(KEY_PENDING_SESSION_IDS, pending - sessionId).apply()
        return true
    }

    private fun preferences(context: Context) = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
