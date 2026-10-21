/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.push.impl.notifications

import androidx.core.app.NotificationManagerCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.push.api.notifications.BipeNotifiedSessions
import io.element.android.services.appnavstate.api.AppForegroundStateService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * Bipe: reads the shade again each time this app shows or removes a notification, and each time the app comes to the
 * foreground. A notification that the user swipes away while the app is open is seen at the next foreground.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultBipeNotifiedSessions(
    private val notificationManager: NotificationManagerCompat,
    private val appForegroundStateService: AppForegroundStateService,
) : BipeNotifiedSessions {
    private val changes = MutableStateFlow(0)

    /** The [NotificationDisplayer] calls this after each notify and cancel. */
    fun onNotificationsChanged() {
        changes.update { it + 1 }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun sessionIds(): Flow<Set<SessionId>> {
        return combine(appForegroundStateService.isInForeground, changes) { _, _ -> }
            .mapLatest {
                // The system posts and cancels notifications asynchronously, so the shade is read a moment later.
                delay(SHADE_SETTLE_MILLIS)
                readSessionIds()
            }
            .distinctUntilChanged()
    }

    private fun readSessionIds(): Set<SessionId> {
        return runCatchingExceptions { notificationManager.activeNotifications }
            .onFailure { Timber.e(it, "Failed to get active notifications") }
            .getOrElse { emptyList() }
            .mapNotNull { notification -> notification.notification.group?.let { tryOrNull { SessionId(it) } } }
            .toSet()
    }

    private companion object {
        const val SHADE_SETTLE_MILLIS = 300L
    }
}
