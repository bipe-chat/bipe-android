/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.push.api.notifications

import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow

/**
 * Bipe: the sessions on this phone with a notification in the shade. The Você tab shows a red dot while another account
 * has one. The notification group is the session ID, so the signal is per account.
 */
fun interface BipeNotifiedSessions {
    fun sessionIds(): Flow<Set<SessionId>>
}
