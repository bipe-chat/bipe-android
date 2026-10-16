/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.contacts

import io.element.android.libraries.architecture.AsyncAction
import java.time.Instant

enum class BipeContactsAccess {
    /** Not asked yet, or refused once: the app can ask again. */
    NotDetermined,
    /** Refused for good: only the system settings can change it. */
    Denied,
    Granted,
}

data class BipeContactsMatch(
    val userId: String,
    /** The name in the user's own address book. */
    val contactName: String,
)

/** A person on Bipe: an address-book match, someone who joined with the user's invite, or both. */
data class BipeContactsRow(
    val userId: String,
    /** `null` when the person is not in the address book. */
    val contactName: String?,
    val joinedWithInvite: Boolean,
)

/** Port of BipeContactsScreenViewState on iOS. */
data class BipeContactsState(
    val access: BipeContactsAccess,
    val isLoading: Boolean,
    val hasSearched: Boolean,
    val matches: List<BipeContactsMatch>,
    /** `null` until the status loads. */
    val phoneLinked: Boolean?,
    val discoverableByPhone: Boolean,
    /** `null` until the invites load. */
    val invitesAvailable: Int?,
    val nextInviteAt: Instant?,
    val joinedUserIds: List<String>,
    val pendingInviteCount: Int,
    /** Matrix ID to "@handle" (or the Matrix ID). */
    val displayTexts: Map<String, String>,
    /** Invite creation, lookup and discovery requests. Failure carries the localised message. */
    val action: AsyncAction<Unit>,
    /** Set to open the system share sheet for this invite link once. */
    val shareSheetUrl: String?,
    /** Set to show this invite link as a QR code. */
    val qrCodeUrl: String?,
    val eventSink: (BipeContactsEvent) -> Unit,
) {
    val canCreateInvite: Boolean get() = (invitesAvailable ?: 0) > 0

    /** People who joined with the user's invite come first, so the invites the user sent stay in view. */
    val rows: List<BipeContactsRow>
        get() {
            val joined = joinedUserIds.toSet()
            val namesByUserId = matches.associate { it.userId to it.contactName }
            val joinedRows = joinedUserIds.map { BipeContactsRow(it, namesByUserId[it], joinedWithInvite = true) }
            val otherRows = matches.filter { it.userId !in joined }.map { BipeContactsRow(it.userId, it.contactName, joinedWithInvite = false) }
            return joinedRows + otherRows
        }

    /** Never prints names or numbers. */
    override fun toString(): String = "BipeContactsState(access=$access, matches=${matches.size}, joined=${joinedUserIds.size})"
}

sealed interface BipeContactsEvent {
    /** The screen is visible again: check the permission and reload the invites (the invites screen can change them). */
    data object Resumed : BipeContactsEvent
    /** The answer to the permission request. [canAskAgain] is `false` when Android will not show the request again. */
    data class AccessResult(val granted: Boolean, val canAskAgain: Boolean) : BipeContactsEvent
    data class SetDiscoverable(val value: Boolean) : BipeContactsEvent
    /** Creates an invite and opens the share sheet. */
    data object InviteByLink : BipeContactsEvent
    /** Creates an invite and shows its QR code. */
    data object InviteInPerson : BipeContactsEvent
    data object ShareSheetOpened : BipeContactsEvent
    data object DismissQrCode : BipeContactsEvent
    data object ClearError : BipeContactsEvent
}
