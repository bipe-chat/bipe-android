/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.announcement.api.Announcement
import io.element.android.features.announcement.api.AnnouncementService
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.spaces.HomeSpacesState
import io.element.android.features.invite.api.SeenInvitesStore
import io.element.android.features.rageshake.api.RageshakeFeatureAvailability
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.account.BipeAccountCap
import io.element.android.libraries.bipe.selfchat.BipeSelfChat
import io.element.android.libraries.bipe.tabs.BipeTabBadges
import io.element.android.libraries.bipe.tabs.BipeYouTabWarning
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.sync.SyncService
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Inject
class HomePresenter(
    private val client: MatrixClient,
    private val syncService: SyncService,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val indicatorService: IndicatorService,
    private val roomListPresenter: Presenter<RoomListState>,
    private val homeSpacesPresenter: Presenter<HomeSpacesState>,
    private val rageshakeFeatureAvailability: RageshakeFeatureAvailability,
    private val sessionStore: SessionStore,
    private val announcementService: AnnouncementService,
    private val featureFlagService: FeatureFlagService,
    private val bipeSelfChat: BipeSelfChat,
    private val seenInvitesStore: SeenInvitesStore,
    private val bipeYouTabWarning: BipeYouTabWarning,
) : Presenter<HomeState> {
    private val currentUserWithNeighborsBuilder = CurrentUserWithNeighborsBuilder()

    @Composable
    override fun present(): HomeState {
        val coroutineState = rememberCoroutineScope()
        val matrixUser by client.userProfile.collectAsState()
        val currentUserAndNeighbors by remember {
            combine(
                client.userProfile,
                sessionStore.sessionsFlow(),
                currentUserWithNeighborsBuilder::build,
            )
        }.collectAsState(initial = persistentListOf(matrixUser))
        // Bipe: the avatar menu lists the accounts, the current one first (ADR 0086, #267). The neighbours hold every account
        // up to three, which covers the cap; no second flow, so the presenter emits no extra state.
        val bipeAccounts by remember {
            derivedStateOf {
                val current = currentUserAndNeighbors.getOrNull(1) ?: currentUserAndNeighbors.first()
                (listOf(current) + currentUserAndNeighbors).distinctBy { it.userId }.toImmutableList()
            }
        }
        val bipeMaxAccounts by BipeAccountCap.maxPersonalAccounts.collectAsState()
        val isOnline by syncService.isOnline.collectAsState()
        val canReportBug by remember { rageshakeFeatureAvailability.isAvailable() }.collectAsState(false)
        val showMultiAccountAnnouncement by remember {
            combine(
                announcementService.announcementsToShowFlow(),
                featureFlagService.isFeatureEnabledFlow(FeatureFlags.MultiAccount),
                sessionStore.sessionsFlow(),
            ) { announcements, isMultiAccountEnabled, sessions ->
                // Do not announce the feature to users who already have several accounts
                isMultiAccountEnabled && sessions.size == 1 && announcements.contains(Announcement.MultiAccount)
            }
        }.collectAsState(false)
        LaunchedEffect(Unit) {
            // The user already uses several accounts, so the feature does not need to be announced anymore,
            // even if they later remove accounts.
            combine(
                announcementService.announcementsToShowFlow(),
                sessionStore.sessionsFlow(),
            ) { announcements, sessions ->
                announcements.contains(Announcement.MultiAccount) && sessions.size > 1
            }
                .filter { it }
                .collect {
                    announcementService.onAnnouncementDismissed(Announcement.MultiAccount)
                }
        }
        val roomListState = roomListPresenter.present()
        val homeSpacesState = homeSpacesPresenter.present()
        var currentHomeNavigationBarItemOrdinal by rememberSaveable { mutableIntStateOf(HomeNavigationBarItem.Chats.ordinal) }
        val currentHomeNavigationBarItem by remember {
            derivedStateOf {
                HomeNavigationBarItem.from(currentHomeNavigationBarItemOrdinal)
            }
        }
        LaunchedEffect(Unit) {
            // Force a refresh of the profile
            client.getUserProfile()
        }
        // Bipe: Conversas lists direct chats and Grupos lists groups (ADR 0053). Você keeps the last category.
        LaunchedEffect(currentHomeNavigationBarItem) {
            when (currentHomeNavigationBarItem) {
                HomeNavigationBarItem.Chats -> roomListState.eventSink(RoomListEvent.BipeShowGroups(false))
                HomeNavigationBarItem.Groups -> roomListState.eventSink(RoomListEvent.BipeShowGroups(true))
                HomeNavigationBarItem.Spaces,
                HomeNavigationBarItem.You -> Unit
            }
        }
        // Bipe: the tab badges count all rooms, not only the rooms of the filtered list. The self-chat ID comes
        // from account data, so the room lists and the badges can leave it out.
        LaunchedEffect(Unit) { bipeSelfChat.refresh() }
        val bipeBadges by remember {
            combine(
                client.roomListService.allRooms.summaries,
                bipeSelfChat.roomId,
                seenInvitesStore.seenRoomIds(),
            ) { summaries, selfChatRoomId, seenInviteRoomIds ->
                BipeTabBadges.from(summaries, selfChatRoomId, seenInviteRoomIds)
            }.distinctUntilChanged()
        }.collectAsState(BipeTabBadges.NONE)
        val bipeYouWarning by remember { bipeYouTabWarning.warning() }.collectAsState(false)
        // Avatar indicator
        val showAvatarIndicator by indicatorService.showRoomListTopBarIndicator()

        fun handleEvent(event: HomeEvent) {
            when (event) {
                is HomeEvent.SelectHomeNavigationBarItem -> {
                    currentHomeNavigationBarItemOrdinal = event.item.ordinal
                }
                is HomeEvent.SwitchToAccount -> coroutineState.launch {
                    sessionStore.setLatestSession(event.sessionId.value)
                }
                HomeEvent.DismissMultiAccountAnnouncement -> coroutineState.launch {
                    announcementService.onAnnouncementDismissed(Announcement.MultiAccount)
                }
            }
        }

        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
        return HomeState(
            currentUserAndNeighbors = currentUserAndNeighbors,
            showAvatarIndicator = showAvatarIndicator,
            hasNetworkConnection = isOnline,
            currentHomeNavigationBarItem = currentHomeNavigationBarItem,
            roomListState = roomListState,
            homeSpacesState = homeSpacesState,
            snackbarMessage = snackbarMessage,
            canReportBug = canReportBug,
            showMultiAccountAnnouncement = showMultiAccountAnnouncement,
            eventSink = ::handleEvent,
            bipeBadges = bipeBadges.copy(youWarning = bipeYouWarning),
            bipeAccounts = bipeAccounts,
            bipeMaxAccounts = bipeMaxAccounts,
        )
    }
}
