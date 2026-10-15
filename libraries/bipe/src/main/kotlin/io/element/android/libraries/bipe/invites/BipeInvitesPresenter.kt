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
    private val stringProvider: StringProvider,
) : Presenter<BipeInvitesState> {
    @Composable
    override fun present(): BipeInvitesState {
        val coroutineScope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(true) }
        var quota by remember { mutableIntStateOf(0) }
        var available by remember { mutableIntStateOf(0) }
        var nextAvailableAt by remember { mutableStateOf<Instant?>(null) }
        var invites by remember { mutableStateOf<List<BipeInvite>>(emptyList()) }
        var shareUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var action by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

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
                val myInvites = withTokenRetry { token -> bipeSession.apiClient.myInvites(token) }
                quota = myInvites.quota
                available = myInvites.available
                nextAvailableAt = myInvites.nextAvailableAt
                invites = myInvites.invites
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe invites load failed")
                show(error)
            } finally {
                isLoading = false
            }
        }

        LaunchedEffect(Unit) { load() }

        fun handleEvent(event: BipeInvitesEvent) {
            when (event) {
                BipeInvitesEvent.Create -> coroutineScope.launch {
                    action = AsyncAction.Loading
                    try {
                        val created = withTokenRetry { token -> bipeSession.apiClient.createInvite(token) }
                        Timber.i("Bipe invite created $created")
                        shareUrls = shareUrls + (created.id to created.url)
                        load()
                        action = AsyncAction.Uninitialized
                    } catch (error: BipeApiException) {
                        Timber.e(error, "Bipe invite creation failed")
                        show(error)
                    }
                }
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
            eventSink = ::handleEvent,
        )
    }

    private suspend fun <T> withTokenRetry(request: suspend (String) -> T): T =
        BipeApiClient.withTokenRetry(bipeSession.accessToken, request)
}
