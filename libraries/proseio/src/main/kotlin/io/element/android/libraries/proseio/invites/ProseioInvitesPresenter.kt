/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.invites

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
import io.element.android.libraries.proseio.api.ProseioApiClient
import io.element.android.libraries.proseio.api.ProseioApiException
import io.element.android.libraries.proseio.api.ProseioInvite
import io.element.android.libraries.proseio.api.ProseioInviteStatus
import io.element.android.libraries.proseio.di.ProseioSession
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

/** Port of ProseioInvitesScreenViewModel on iOS. */
@Inject
class ProseioInvitesPresenter(
    private val proseioSession: ProseioSession,
    private val stringProvider: StringProvider,
) : Presenter<ProseioInvitesState> {
    @Composable
    override fun present(): ProseioInvitesState {
        val coroutineScope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(true) }
        var quota by remember { mutableStateOf(0) }
        var available by remember { mutableStateOf(0) }
        var nextAvailableAt by remember { mutableStateOf<Instant?>(null) }
        var invites by remember { mutableStateOf<List<ProseioInvite>>(emptyList()) }
        var shareUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var action by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        val resolver = proseioSession.handleResolver
        val handles by resolver.handles.collectAsState()
        val joinedUserTitles = remember(invites, handles) {
            invites.filter { it.status == ProseioInviteStatus.USED }
                .mapNotNull { it.joinedUserId }
                .associateWith { resolver.displayText(it) }
        }

        fun show(error: ProseioApiException) {
            action = AsyncAction.Failure(Exception(error.localizedMessage(stringProvider)))
        }

        suspend fun load() {
            try {
                val myInvites = withTokenRetry { token -> proseioSession.apiClient.myInvites(token) }
                quota = myInvites.quota
                available = myInvites.available
                nextAvailableAt = myInvites.nextAvailableAt
                invites = myInvites.invites
            } catch (error: ProseioApiException) {
                Timber.e(error, "Proseio invites load failed")
                show(error)
            } finally {
                isLoading = false
            }
        }

        LaunchedEffect(Unit) { load() }

        fun handleEvent(event: ProseioInvitesEvent) {
            when (event) {
                ProseioInvitesEvent.Create -> coroutineScope.launch {
                    action = AsyncAction.Loading
                    try {
                        val created = withTokenRetry { token -> proseioSession.apiClient.createInvite(token) }
                        Timber.i("Proseio invite created $created")
                        shareUrls = shareUrls + (created.id to created.url)
                        load()
                        action = AsyncAction.Uninitialized
                    } catch (error: ProseioApiException) {
                        Timber.e(error, "Proseio invite creation failed")
                        show(error)
                    }
                }
                is ProseioInvitesEvent.Revoke -> coroutineScope.launch {
                    action = AsyncAction.Loading
                    try {
                        withTokenRetry { token -> proseioSession.apiClient.revokeInvite(event.inviteId, token) }
                        shareUrls = shareUrls - event.inviteId
                        load()
                        action = AsyncAction.Uninitialized
                    } catch (error: ProseioApiException) {
                        Timber.e(error, "Proseio invite revoke failed")
                        show(error)
                    }
                }
                ProseioInvitesEvent.ClearError -> action = AsyncAction.Uninitialized
            }
        }

        return ProseioInvitesState(
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
        ProseioApiClient.withTokenRetry(proseioSession.accessToken, request)
}
