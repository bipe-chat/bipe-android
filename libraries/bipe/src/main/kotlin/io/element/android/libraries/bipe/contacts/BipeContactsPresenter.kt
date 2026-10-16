/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.contacts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeInviteStatus
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.bipe.invites.BipeInviteCreator
import io.element.android.libraries.bipe.invites.BipeInviteDestination
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

/** Port of BipeContactsScreenViewModel on iOS. */
@Inject
class BipeContactsPresenter(
    private val bipeSession: BipeSession,
    private val inviteCreator: BipeInviteCreator,
    private val addressBook: BipeAddressBook,
    private val stringProvider: StringProvider,
) : Presenter<BipeContactsState> {
    @Composable
    override fun present(): BipeContactsState {
        val coroutineScope = rememberCoroutineScope()
        var access by remember { mutableStateOf(if (addressBook.hasAccess()) BipeContactsAccess.Granted else BipeContactsAccess.NotDetermined) }
        var isLoading by remember { mutableStateOf(false) }
        var hasSearched by remember { mutableStateOf(false) }
        var matches by remember { mutableStateOf<List<BipeContactsMatch>>(emptyList()) }
        // The same values as the Privacidade group, so a change here shows there at once.
        val privacy = bipeSession.privacy
        val phone by privacy.phone.collectAsState()
        val discoverableByPhone by privacy.discoverableByPhone.collectAsState()
        var invitesAvailable by remember { mutableStateOf<Int?>(null) }
        var nextInviteAt by remember { mutableStateOf<Instant?>(null) }
        var joinedUserIds by remember { mutableStateOf<List<String>>(emptyList()) }
        var pendingInviteCount by remember { mutableStateOf(0) }
        var action by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        var shareSheetUrl by remember { mutableStateOf<String?>(null) }
        var qrCodeUrl by remember { mutableStateOf<String?>(null) }
        var createdCount by remember { mutableIntStateOf(0) }
        var noteDestination by remember { mutableStateOf<BipeInviteDestination?>(null) }

        val resolver = bipeSession.handleResolver
        val handles by resolver.handles.collectAsState()
        val displayTexts = remember(matches, joinedUserIds, handles) {
            (joinedUserIds + matches.map { it.userId }).distinct().associateWith { resolver.displayText(it) }
        }

        fun show(error: BipeApiException) {
            action = AsyncAction.Failure(Exception(error.localizedMessage(stringProvider)))
        }

        suspend fun search() {
            if (isLoading) return
            isLoading = true
            try {
                val namesByPhone = addressBook.namesByPhone()
                val found = mutableMapOf<String, BipeContactsMatch>()
                for (batch in namesByPhone.keys.chunked(LOOKUP_BATCH_SIZE)) {
                    try {
                        val batchMatches = withTokenRetry { token -> bipeSession.apiClient.lookupContacts(batch, token) }
                        for (match in batchMatches) {
                            if (match.userId == bipeSession.userId) continue
                            found[match.userId] = BipeContactsMatch(match.userId, namesByPhone[match.phone] ?: match.phone)
                        }
                    } catch (error: BipeApiException) {
                        Timber.e(error, "Bipe contact lookup failed")
                        show(error)
                        break
                    }
                }
                matches = found.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.contactName })
            } finally {
                isLoading = false
                hasSearched = true
            }
        }

        suspend fun loadInvites() {
            try {
                val myInvites = inviteCreator.myInvites()
                invitesAvailable = myInvites.available
                nextInviteAt = myInvites.nextAvailableAt
                joinedUserIds = myInvites.invites
                    .filter { it.status == BipeInviteStatus.USED }
                    .mapNotNull { it.joinedUserId }
                    .filter { it != bipeSession.userId }
                pendingInviteCount = myInvites.invites.count { it.status == BipeInviteStatus.PENDING }
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe invites load failed")
            }
        }

        LaunchedEffect(Unit) {
            launch { privacy.refreshDiscovery() }
            if (access == BipeContactsAccess.Granted) search()
        }

        /** Creates an invite and returns its link, so the caller can open the share sheet or the QR code at once. */
        suspend fun createInvite(note: String): String? {
            if (invitesAvailable.let { it == null || it <= 0 } || action.isLoading()) return null
            action = AsyncAction.Loading
            return try {
                val created = inviteCreator.create(note)
                createdCount++
                loadInvites()
                action = AsyncAction.Uninitialized
                created.url
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe invite creation failed")
                show(error)
                null
            }
        }

        fun handleEvent(event: BipeContactsEvent) {
            when (event) {
                BipeContactsEvent.Resumed -> {
                    // Back from the system settings, the permission can have changed either way.
                    val granted = addressBook.hasAccess()
                    if (granted && access != BipeContactsAccess.Granted) {
                        access = BipeContactsAccess.Granted
                        coroutineScope.launch { search() }
                    } else if (!granted && access == BipeContactsAccess.Granted) {
                        access = BipeContactsAccess.NotDetermined
                        matches = emptyList()
                    }
                    coroutineScope.launch { loadInvites() }
                }
                is BipeContactsEvent.AccessResult -> {
                    access = when {
                        event.granted -> BipeContactsAccess.Granted
                        event.canAskAgain -> BipeContactsAccess.NotDetermined
                        else -> BipeContactsAccess.Denied
                    }
                    if (event.granted) coroutineScope.launch { search() }
                }
                is BipeContactsEvent.SetDiscoverable -> coroutineScope.launch {
                    privacy.setDiscoverableByPhone(event.value).onFailure { error ->
                        action = AsyncAction.Failure(if (error is BipeApiException) Exception(error.localizedMessage(stringProvider)) else error)
                    }
                }
                BipeContactsEvent.InviteByLink -> if ((invitesAvailable ?: 0) > 0) noteDestination = BipeInviteDestination.SHARE_SHEET
                BipeContactsEvent.InviteInPerson -> if ((invitesAvailable ?: 0) > 0) noteDestination = BipeInviteDestination.QR_CODE
                is BipeContactsEvent.CreateInvite -> {
                    val destination = noteDestination ?: return
                    noteDestination = null
                    coroutineScope.launch {
                        val url = createInvite(event.note) ?: return@launch
                        when (destination) {
                            BipeInviteDestination.SHARE_SHEET -> shareSheetUrl = url
                            BipeInviteDestination.QR_CODE -> qrCodeUrl = url
                        }
                    }
                }
                BipeContactsEvent.DismissNote -> noteDestination = null
                BipeContactsEvent.ShareSheetOpened -> shareSheetUrl = null
                BipeContactsEvent.DismissQrCode -> qrCodeUrl = null
                BipeContactsEvent.ClearError -> action = AsyncAction.Uninitialized
            }
        }

        return BipeContactsState(
            access = access,
            isLoading = isLoading,
            hasSearched = hasSearched,
            matches = matches,
            phoneLinked = phone?.linked,
            // Default on (ADR 0063) until the first answer.
            discoverableByPhone = discoverableByPhone ?: true,
            invitesAvailable = invitesAvailable,
            nextInviteAt = nextInviteAt,
            joinedUserIds = joinedUserIds,
            pendingInviteCount = pendingInviteCount,
            displayTexts = displayTexts,
            action = action,
            shareSheetUrl = shareSheetUrl,
            qrCodeUrl = qrCodeUrl,
            createdCount = createdCount,
            noteDestination = noteDestination,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun <T> withTokenRetry(request: suspend (String) -> T): T =
        BipeApiClient.withTokenRetry(bipeSession.accessToken, request)

    private companion object {
        /** The lookup API takes at most 500 numbers per request. */
        const val LOOKUP_BATCH_SIZE = 500
    }
}
