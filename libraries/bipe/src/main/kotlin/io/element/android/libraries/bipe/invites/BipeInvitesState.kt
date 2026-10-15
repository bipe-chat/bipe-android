/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import androidx.compose.runtime.Immutable
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.api.BipeInvite
import java.time.Instant

/** Port of BipeInvitesScreenViewState on iOS. */
@Immutable
data class BipeInvitesState(
    val isLoading: Boolean,
    val quota: Int,
    val available: Int,
    /** Only when [available] is 0: when the next slot comes back. */
    val nextAvailableAt: Instant?,
    val invites: List<BipeInvite>,
    /** Invite ID to share URL. The API returns the code once, at creation, so only invites made on this screen can be shared. */
    val shareUrls: Map<String, String>,
    /** Matrix ID to "@handle" (or the Matrix ID) for used invites. */
    val joinedUserTitles: Map<String, String>,
    /** Create and revoke requests. Failure carries the localised message. */
    val action: AsyncAction<Unit>,
    val eventSink: (BipeInvitesEvent) -> Unit,
) {
    val canCreate: Boolean get() = !isLoading && available > 0

    override fun toString(): String = "BipeInvitesState(available=$available, invites=${invites.size})"
}

sealed interface BipeInvitesEvent {
    data object Create : BipeInvitesEvent
    data class Revoke(val inviteId: String) : BipeInvitesEvent
    data object ClearError : BipeInvitesEvent
}
