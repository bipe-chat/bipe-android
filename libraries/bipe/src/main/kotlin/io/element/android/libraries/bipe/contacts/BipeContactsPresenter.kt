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
        var phoneLinked by remember { mutableStateOf<Boolean?>(null) }
        var discoverableByPhone by remember { mutableStateOf(true) }
        var invitesAvailable by remember { mutableStateOf<Int?>(null) }
        var nextInviteAt by remember { mutableStateOf<Instant?>(null) }
        var joinedUserIds by remember { mutableStateOf<List<String>>(emptyList()) }
        var pendingInviteCount by remember { mutableStateOf(0) }
        var action by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        var shareSheetUrl by remember { mutableStateOf<String?>(null) }
        var qrCodeUrl by remember { mutableStateOf<String?>(null) }

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

        suspend fun loadPhoneStatus() {
            try {
                val status = withTokenRetry { token -> bipeSession.apiClient.myPhone(token) }
                phoneLinked = status.linked
                discoverableByPhone = status.discoverableByPhone
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe phone status failed")
            }
        }

        LaunchedEffect(Unit) {
            launch { loadPhoneStatus() }
            if (access == BipeContactsAccess.Granted) search()
        }

        /** Creates an invite and returns its link, so the caller can open the share sheet or the QR code at once. */
        suspend fun createInvite(): String? {
            if (invitesAvailable.let { it == null || it <= 0 } || action.isLoading()) return null
            action = AsyncAction.Loading
            return try {
                val created = inviteCreator.create()
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
                is BipeContactsEvent.SetDiscoverable -> {
                    val previous = discoverableByPhone
                    if (event.value == previous) return
                    discoverableByPhone = event.value
                    coroutineScope.launch {
                        try {
                            withTokenRetry { token -> bipeSession.apiClient.setDiscoverableByPhone(event.value, token) }
                        } catch (error: BipeApiException) {
                            Timber.e(error, "Bipe discovery setting failed")
                            discoverableByPhone = previous
                            show(error)
                        }
                    }
                }
                BipeContactsEvent.InviteByLink -> coroutineScope.launch {
                    createInvite()?.let { shareSheetUrl = it }
                }
                BipeContactsEvent.InviteInPerson -> coroutineScope.launch {
                    createInvite()?.let { qrCodeUrl = it }
                }
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
            phoneLinked = phoneLinked,
            discoverableByPhone = discoverableByPhone,
            invitesAvailable = invitesAvailable,
            nextInviteAt = nextInviteAt,
            joinedUserIds = joinedUserIds,
            pendingInviteCount = pendingInviteCount,
            displayTexts = displayTexts,
            action = action,
            shareSheetUrl = shareSheetUrl,
            qrCodeUrl = qrCodeUrl,
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
