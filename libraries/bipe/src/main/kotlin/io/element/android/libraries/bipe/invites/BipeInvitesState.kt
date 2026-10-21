/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import androidx.compose.runtime.Immutable
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.api.BipeInvite
import io.element.android.libraries.bipe.api.BipeInviteStatus
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
    /** Set to open the system share sheet for this invite link once. */
    val shareSheetUrl: String?,
    /** Set to show this invite link as a QR code. */
    val qrCodeUrl: String?,
    /** Invites made on this screen. The view plays the invite_created haptic when it goes up (ADR 0134). */
    val createdCount: Int,
    /** Set while the note dialog for a new invite shows (ADR 0136). */
    val noteDestination: BipeInviteDestination?,
    val eventSink: (BipeInvitesEvent) -> Unit,
) {
    val canCreate: Boolean get() = !isLoading && available > 0

    val joinedInvites: List<BipeInvite> get() = invites.filter { it.status == BipeInviteStatus.USED && it.joinedUserId != null }

    /** Expired and cancelled invites are left out: they have no action and only push the useful rows down. */
    val pendingInvites: List<BipeInvite> get() = invites.filter { it.status == BipeInviteStatus.PENDING }

    override fun toString(): String = "BipeInvitesState(available=$available, invites=${invites.size})"
}

/** Where a new invite opens after its note dialog (ADR 0136). */
enum class BipeInviteDestination { SHARE_SHEET, QR_CODE }

sealed interface BipeInvitesEvent {
    /** Asks for the note, then creates an invite and opens the share sheet. */
    data object InviteByLink : BipeInvitesEvent

    /** Asks for the note, then creates an invite and shows its QR code. */
    data object InviteInPerson : BipeInvitesEvent

    /** Creates the invite with this note (empty means no note). */
    data class CreateInvite(val note: String) : BipeInvitesEvent
    data object DismissNote : BipeInvitesEvent
    data class Share(val inviteId: String) : BipeInvitesEvent
    data class ShowQrCode(val inviteId: String) : BipeInvitesEvent
    data class Revoke(val inviteId: String) : BipeInvitesEvent
    data object ShareSheetOpened : BipeInvitesEvent
    data object DismissQrCode : BipeInvitesEvent
    data object ClearError : BipeInvitesEvent
}
