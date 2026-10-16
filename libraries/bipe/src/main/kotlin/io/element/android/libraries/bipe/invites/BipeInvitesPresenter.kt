/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

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
import io.element.android.libraries.bipe.api.BipeInvite
import io.element.android.libraries.bipe.api.BipeInviteStatus
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

/** Port of BipeInvitesScreenViewModel on iOS. */
@Inject
class BipeInvitesPresenter(
    private val bipeSession: BipeSession,
    private val linkStore: BipeInviteLinkStore,
    private val inviteCreator: BipeInviteCreator,
    private val stringProvider: StringProvider,
) : Presenter<BipeInvitesState> {
    @Composable
    override fun present(): BipeInvitesState {
        val coroutineScope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(true) }
        var quota by remember { mutableStateOf(0) }
        var available by remember { mutableStateOf(0) }
        var nextAvailableAt by remember { mutableStateOf<Instant?>(null) }
        var invites by remember { mutableStateOf<List<BipeInvite>>(emptyList()) }
        var shareUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var action by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        var shareSheetUrl by remember { mutableStateOf<String?>(null) }
        var qrCodeUrl by remember { mutableStateOf<String?>(null) }

        val resolver = bipeSession.handleResolver
        val handles by resolver.handles.collectAsState()
        val joinedUserTitles = remember(invites, handles) {
            invites.filter { it.status == BipeInviteStatus.USED }
                .mapNotNull { it.joinedUserId }
                .associateWith { resolver.displayText(it) }
        }

        fun show(error: BipeApiException) {
            action = AsyncAction.Failure(Exception(error.localizedMessage(stringProvider)))
        }

        suspend fun load() {
            try {
                val myInvites = inviteCreator.myInvites()
                quota = myInvites.quota
                available = myInvites.available
                nextAvailableAt = myInvites.nextAvailableAt
                invites = myInvites.invites
                // Forget links of invites that can no longer be used; the list itself shows who joined.
                val pendingIds = myInvites.invites.filter { it.status == BipeInviteStatus.PENDING }.map { it.id }.toSet()
                shareUrls = shareUrls.filterKeys { it in pendingIds }
                linkStore.keepLinks(bipeSession.userId, pendingIds)
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe invites load failed")
                show(error)
            } finally {
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            shareUrls = linkStore.links(bipeSession.userId)
            load()
        }

        /** Creates an invite and returns its link, so the caller can open the share sheet or the QR code at once. */
        suspend fun create(): String? {
            if (isLoading || available <= 0) return null
            action = AsyncAction.Loading
            return try {
                val created = inviteCreator.create()
                shareUrls = shareUrls + (created.id to created.url)
                load()
                action = AsyncAction.Uninitialized
                created.url
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe invite creation failed")
                show(error)
                null
            }
        }

        fun handleEvent(event: BipeInvitesEvent) {
            when (event) {
                BipeInvitesEvent.InviteByLink -> coroutineScope.launch {
                    create()?.let { shareSheetUrl = it }
                }
                BipeInvitesEvent.InviteInPerson -> coroutineScope.launch {
                    create()?.let { qrCodeUrl = it }
                }
                is BipeInvitesEvent.Share -> shareSheetUrl = shareUrls[event.inviteId]
                is BipeInvitesEvent.ShowQrCode -> qrCodeUrl = shareUrls[event.inviteId]
                BipeInvitesEvent.ShareSheetOpened -> shareSheetUrl = null
                BipeInvitesEvent.DismissQrCode -> qrCodeUrl = null
                is BipeInvitesEvent.Revoke -> coroutineScope.launch {
                    action = AsyncAction.Loading
                    try {
                        withTokenRetry { token -> bipeSession.apiClient.revokeInvite(event.inviteId, token) }
                        shareUrls = shareUrls - event.inviteId
                        load()
                        action = AsyncAction.Uninitialized
                    } catch (error: BipeApiException) {
                        Timber.e(error, "Bipe invite revoke failed")
                        show(error)
                    }
                }
                BipeInvitesEvent.ClearError -> action = AsyncAction.Uninitialized
            }
        }

        return BipeInvitesState(
            isLoading = isLoading,
            quota = quota,
            available = available,
            nextAvailableAt = nextAvailableAt,
            invites = invites,
            shareUrls = shareUrls,
            joinedUserTitles = joinedUserTitles,
            action = action,
            shareSheetUrl = shareSheetUrl,
            qrCodeUrl = qrCodeUrl,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun <T> withTokenRetry(request: suspend (String) -> T): T =
        BipeApiClient.withTokenRetry(bipeSession.accessToken, request)
}
