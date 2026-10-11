/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.invites

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.proseio.api.ProseioInvite
import java.time.Instant

/** Port of ProseioInvitesScreenViewState on iOS. */
data class ProseioInvitesState(
    val isLoading: Boolean,
    val quota: Int,
    val available: Int,
    /** Only when [available] is 0: when the next slot comes back. */
    val nextAvailableAt: Instant?,
    val invites: List<ProseioInvite>,
    /** Invite ID to share URL. The API returns the code once, at creation, so only invites made on this screen can be shared. */
    val shareUrls: Map<String, String>,
    /** Matrix ID to "@handle" (or the Matrix ID) for used invites. */
    val joinedUserTitles: Map<String, String>,
    /** Create and revoke requests. Failure carries the localised message. */
    val action: AsyncAction<Unit>,
    val eventSink: (ProseioInvitesEvent) -> Unit,
) {
    val canCreate: Boolean get() = !isLoading && available > 0

    override fun toString(): String = "ProseioInvitesState(available=$available, invites=${invites.size})"
}

sealed interface ProseioInvitesEvent {
    data object Create : ProseioInvitesEvent
    data class Revoke(val inviteId: String) : ProseioInvitesEvent
    data object ClearError : ProseioInvitesEvent
}
