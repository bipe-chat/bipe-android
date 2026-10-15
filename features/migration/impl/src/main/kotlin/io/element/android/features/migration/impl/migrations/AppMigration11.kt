/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.migration.impl.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.element.android.libraries.bipe.threads.BipeThreadsCacheRebuild
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.sessionstorage.api.SessionStore

/**
 * Bipe: threads are on by default. Mark the existing sessions so each one clears its cache once when it starts.
 */
@ContributesIntoSet(AppScope::class)
class AppMigration11(
    @ApplicationContext private val context: Context,
    private val sessionStore: SessionStore,
) : AppMigration {
    override val order: Int = 11

    override suspend fun migrate(isFreshInstall: Boolean) {
        if (isFreshInstall) return

        BipeThreadsCacheRebuild.markPending(context, sessionStore.getAllSessions().map { it.userId })
    }
}
