/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.Inject
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.appconfig.BipeConfig
import io.element.android.features.announcement.api.Announcement
import io.element.android.features.announcement.api.AnnouncementService
import io.element.android.features.home.impl.datasource.RoomListDataSource
import io.element.android.features.home.impl.filters.RoomListFiltersEvent
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.filters.into
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.search.GlobalSearchState
import io.element.android.features.home.impl.search.RoomListSearchEvent
import io.element.android.features.home.impl.search.RoomListSearchState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.into
import io.element.android.features.home.impl.spacefilters.selectedFilter
import io.element.android.features.invite.api.SeenInvitesStore
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteEvent.AcceptInvite
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteEvent.DeclineInvite
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteState
import io.element.android.features.leaveroom.api.LeaveRoomEvent
import io.element.android.features.leaveroom.api.LeaveRoomState
import io.element.android.features.preferences.impl.tasks.MarkRoomAsRead
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.banner.BipeRemoteBannerStore
import io.element.android.libraries.bipe.clearedchats.BipeClearedChats
import io.element.android.libraries.bipe.clearedchats.ClearedChats
import io.element.android.libraries.bipe.invites.BipeInvitePairs
import io.element.android.libraries.bipe.invites.BipeInviterGreeting
import io.element.android.libraries.bipe.onboarding.BipeHandlePrompt
import io.element.android.libraries.bipe.selfchat.BipeSelfChat
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.api.ShowAllActivityInRoomListFeature
import io.element.android.libraries.fullscreenintent.api.FullScreenIntentPermissionsState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.ui.safety.rememberHideInvitesAvatar
import io.element.android.libraries.push.api.battery.BatteryOptimizationState
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.analytics.api.watchers.AnalyticsColdStartWatcher
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber
import io.element.android.features.home.impl.filters.RoomListFilter as HomeRoomListFilter

@Inject
class RoomListPresenter(
    private val client: MatrixClient,
    private val leaveRoomPresenter: Presenter<LeaveRoomState>,
    private val roomListDataSource: RoomListDataSource,
    private val filtersPresenter: Presenter<RoomListFiltersState>,
    private val searchPresenter: Presenter<RoomListSearchState>,
    private val analyticsService: AnalyticsService,
    private val acceptDeclineInvitePresenter: Presenter<AcceptDeclineInviteState>,
    private val fullScreenIntentPermissionsPresenter: Presenter<FullScreenIntentPermissionsState>,
    private val batteryOptimizationPresenter: Presenter<BatteryOptimizationState>,
    private val markRoomAsRead: MarkRoomAsRead,
    private val seenInvitesStore: SeenInvitesStore,
    private val announcementService: AnnouncementService,
    private val coldStartWatcher: AnalyticsColdStartWatcher,
    private val spaceFiltersPresenter: Presenter<SpaceFiltersState>,
    private val globalSearchPresenter: Presenter<GlobalSearchState>,
    private val featureFlagService: FeatureFlagService,
    private val bipeSelfChat: BipeSelfChat,
    private val bipeInviterGreeting: BipeInviterGreeting,
    private val bipeInvitePairs: BipeInvitePairs,
    private val bipeHandlePrompt: BipeHandlePrompt,
    private val bipeRemoteBannerStore: BipeRemoteBannerStore,
    private val bipeClearedChats: BipeClearedChats,
) : Presenter<RoomListState> {
    private val encryptionService = client.encryptionService
    private val isBipeAccount = client.sessionId.value.endsWith(":${BipeConfig.SERVER_NAME}") // Bipe: bipe#201

    @Composable
    override fun present(): RoomListState {
        val coroutineScope = rememberCoroutineScope()
        val leaveRoomState = leaveRoomPresenter.present()
        val filtersState = filtersPresenter.present()
        val searchState = searchPresenter.present()
        val spaceFiltersState = spaceFiltersPresenter.present()
        val acceptDeclineInviteState = acceptDeclineInvitePresenter.present()
        val globalSearchState = globalSearchPresenter.present()

        LaunchedEffect(Unit) {
            roomListDataSource.launchIn(this)
        }

        var securityBannerDismissed by rememberSaveable { mutableStateOf(false) }
        val showNewNotificationSoundBanner by remember {
            announcementService.announcementsToShowFlow().map { announcements ->
                announcements.contains(Announcement.NewNotificationSound)
            }
        }.collectAsState(false)

        // Avatar indicator
        val hideInvitesAvatar by client.rememberHideInvitesAvatar()

        // Bipe: the fixed category of the list. Conversas (direct chats) is the first tab (ADR 0053).
        var bipeShowsGroups by rememberSaveable { mutableStateOf(false) }
        // Bipe (bipe#435): the list shows the archived chats of the tab.
        var bipeShowsArchived by rememberSaveable { mutableStateOf(false) }
        val bipeSelfChatRoomId by bipeSelfChat.roomId.collectAsState()
        val bipeInviter by bipeInviterGreeting.inviter.collectAsState()
        val bipeInvitePairIds by bipeInvitePairs.userIds.collectAsState()
        val bipeShowsHandlePrompt by bipeHandlePrompt.shows.collectAsState()
        val bipeRemoteBanner by bipeRemoteBannerStore.shown.collectAsState()
        val bipeClearedChatsState by bipeClearedChats.state.collectAsState()

        val contextMenu = remember { mutableStateOf<RoomListState.ContextMenu>(RoomListState.ContextMenu.Hidden) }
        val declineInviteMenu = remember { mutableStateOf<RoomListState.DeclineInviteMenu>(RoomListState.DeclineInviteMenu.Hidden) }

        val showAllActivity by produceState(initialValue = false) {
            featureFlagService.isFeatureEnabledFlow(ShowAllActivityInRoomListFeature).collect { value = it }
        }

        fun handleEvent(event: RoomListEvent) {
            when (event) {
                is RoomListEvent.UpdateVisibleRange -> coroutineScope.launch {
                    roomListDataSource.updateVisibleRange(event.range)
                }
                RoomListEvent.DismissRequestVerificationPrompt -> securityBannerDismissed = true
                RoomListEvent.DismissBanner -> securityBannerDismissed = true
                RoomListEvent.DismissNewNotificationSoundBanner -> coroutineScope.launch {
                    announcementService.onAnnouncementDismissed(Announcement.NewNotificationSound)
                }
                RoomListEvent.ToggleSearchResults -> searchState.eventSink(RoomListSearchEvent.ToggleSearchVisibility)
                is RoomListEvent.ShowContextMenu -> {
                    coroutineScope.showContextMenu(event, contextMenu)
                }
                is RoomListEvent.HideContextMenu -> {
                    contextMenu.value = RoomListState.ContextMenu.Hidden
                }
                is RoomListEvent.LeaveRoom -> {
                    leaveRoomState.eventSink(LeaveRoomEvent.LeaveRoom(event.roomId, needsConfirmation = event.needsConfirmation))
                }
                is RoomListEvent.SetRoomIsFavorite -> coroutineScope.setRoomIsFavorite(event.roomId, event.isFavorite)
                is RoomListEvent.SetRoomIsMuted -> coroutineScope.setRoomIsMuted(event.roomId, event.isMuted)
                is RoomListEvent.BipeSetRoomIsArchived -> coroutineScope.setRoomIsArchived(event.roomId, event.isArchived)
                is RoomListEvent.BipeShowArchived -> bipeShowsArchived = event.showArchived
                is RoomListEvent.MarkAsRead -> coroutineScope.markAsRead(event.roomId)
                is RoomListEvent.MarkAsUnread -> coroutineScope.markAsUnread(event.roomId)
                is RoomListEvent.AcceptInvite -> {
                    acceptDeclineInviteState.eventSink(
                        AcceptInvite(event.roomSummary.toInviteData())
                    )
                }
                is RoomListEvent.DeclineInvite -> {
                    acceptDeclineInviteState.eventSink(
                        DeclineInvite(event.roomSummary.toInviteData(), blockUser = event.blockUser, shouldConfirm = false)
                    )
                }
                is RoomListEvent.ShowDeclineInviteMenu -> declineInviteMenu.value = RoomListState.DeclineInviteMenu.Shown(event.roomSummary)
                RoomListEvent.HideDeclineInviteMenu -> declineInviteMenu.value = RoomListState.DeclineInviteMenu.Hidden
                RoomListEvent.BipeDismissHandlePrompt -> bipeHandlePrompt.dismiss()
                RoomListEvent.BipeDismissRemoteBanner -> bipeRemoteBannerStore.dismiss()
                RoomListEvent.BipeRefreshRemoteBanner -> bipeRemoteBannerStore.refresh()
                is RoomListEvent.BipeShowGroups -> {
                    // Pedidos (the invites filter) belongs to Conversas only.
                    if (event.showGroups && HomeRoomListFilter.Invites in filtersState.selectedFilters()) {
                        filtersState.eventSink(RoomListFiltersEvent.ToggleFilter(HomeRoomListFilter.Invites))
                    }
                    bipeShowsGroups = event.showGroups
                    bipeShowsArchived = false
                }
            }
        }

        LaunchedEffect(filtersState.filterSelectionStates, spaceFiltersState.selectedFilter(), bipeShowsGroups) {
            val selectedFilters = filtersState.selectedFilters().map { filter -> filter.into() }
            val selectedSpaceFilter = spaceFiltersState.selectedFilter().into()
            // Bipe: the tab fixes the category. The SDK uses the DM flag for it (ADR 0053).
            val bipeCategory = if (bipeShowsGroups) RoomListFilter.Category.Group else RoomListFilter.Category.People
            val allFilters = RoomListFilter.All(selectedFilters + listOfNotNull(selectedSpaceFilter) + bipeCategory)
            roomListDataSource.updateFilter(allFilters)
        }

        val canReportRoom by produceState(false) { value = client.canReportRoom() }
        val showUnreadCount by produceState(false) {
            featureFlagService.isFeatureEnabledFlow(FeatureFlags.UnreadIndicatorCount).collect { value = it }
        }

        val contentState = roomListContentState(
            securityBannerDismissed,
            showNewNotificationSoundBanner,
            showUnreadCount,
            showAllActivity,
            showsRequests = io.element.android.features.home.impl.filters.RoomListFilter.Invites in filtersState.selectedFilters(),
            bipeSelfChatRoomId = bipeSelfChatRoomId,
            bipeShowsGroups = bipeShowsGroups,
            bipeShowsArchived = bipeShowsArchived,
            bipeHasAnyFilterSelected = filtersState.hasAnyFilterSelected || spaceFiltersState is SpaceFiltersState.Selected,
            bipeInvitePairIds = bipeInvitePairIds.toImmutableSet(),
            bipeClearedChats = bipeClearedChatsState,
        )

        // Bipe: the tab sets the category, so the People and Rooms chips are gone. Pedidos shows on Conversas only.
        val bipeFiltersState = filtersState.copy(
            filterSelectionStates = filtersState.filterSelectionStates
                .filterNot { it.filter in bipeHiddenFilters(bipeShowsGroups) }
                .toImmutableList(),
        )

        return RoomListState(
            contextMenu = contextMenu.value,
            declineInviteMenu = declineInviteMenu.value,
            leaveRoomState = leaveRoomState,
            filtersState = bipeFiltersState,
            searchState = searchState,
            globalSearchState = globalSearchState,
            spaceFiltersState = spaceFiltersState,
            contentState = contentState,
            acceptDeclineInviteState = acceptDeclineInviteState,
            hideInvitesAvatars = hideInvitesAvatar,
            canReportRoom = canReportRoom,
            bipeInviter = bipeInviter,
            bipeOpenInviterChat = bipeInviterGreeting::openChat,
            eventSink = ::handleEvent,
            bipeShowsHandlePrompt = bipeShowsHandlePrompt,
            bipeRemoteBanner = bipeRemoteBanner,
        )
    }

    // Bipe: the filter chips that the current tab hides.
    private fun bipeHiddenFilters(showsGroups: Boolean): Set<HomeRoomListFilter> {
        val alwaysHidden = setOf(
            HomeRoomListFilter.People,
            HomeRoomListFilter.Rooms,
        )
        return if (showsGroups) alwaysHidden + HomeRoomListFilter.Invites else alwaysHidden
    }

    @Composable
    private fun rememberSecurityBannerState(
        securityBannerDismissed: Boolean,
    ): State<SecurityBannerState> {
        val currentSecurityBannerDismissed by rememberUpdatedState(securityBannerDismissed)
        val recoveryState by encryptionService.recoveryStateStateFlow.collectAsState()
        return remember {
            derivedStateOf {
                calculateBannerState(
                    securityBannerDismissed = currentSecurityBannerDismissed,
                    recoveryState = recoveryState,
                )
            }
        }
    }

    private fun calculateBannerState(
        securityBannerDismissed: Boolean,
        recoveryState: RecoveryState,
    ): SecurityBannerState {
        if (securityBannerDismissed) {
            return SecurityBannerState.None
        }

        when (recoveryState) {
            // Bipe: no "set up recovery" banner for Bipe accounts. They recover with the PIN and the
            // recovery token (ADR 0057, ADR 0073); the Bipe fallback prompt is bipe#202.
            RecoveryState.DISABLED -> if (!isBipeAccount) return SecurityBannerState.SetUpRecovery
            RecoveryState.INCOMPLETE -> return SecurityBannerState.RecoveryKeyConfirmation
            RecoveryState.UNKNOWN,
            RecoveryState.WAITING_FOR_SYNC,
            RecoveryState.ENABLED -> Unit
        }

        return SecurityBannerState.None
    }

    @Composable
    private fun roomListContentState(
        securityBannerDismissed: Boolean,
        showNewNotificationSoundBanner: Boolean,
        showUnreadCount: Boolean,
        showAllActivity: Boolean,
        showsRequests: Boolean,
        bipeSelfChatRoomId: RoomId?,
        bipeShowsGroups: Boolean,
        bipeShowsArchived: Boolean,
        bipeHasAnyFilterSelected: Boolean,
        bipeInvitePairIds: ImmutableSet<String>,
        bipeClearedChats: ClearedChats,
    ): RoomListContentState {
        val roomSummaries by produceState(initialValue = AsyncData.Loading()) {
            roomListDataSource.roomSummariesFlow.collect { value = AsyncData.Success(it) }
        }
        // Bipe ADR 0034: a DM invite from an invite pair skips Pedidos and is accepted without a tap (iOS parity, bipe#413).
        // An invite from anyone else reloads the pairs once, because a new invitee's first chat can arrive before the pairs know them.
        LaunchedEffect(roomSummaries, bipeInvitePairIds) {
            roomSummaries.dataOrNull().orEmpty()
                .filter { it.displayType == RoomSummaryDisplayType.INVITE && it.isDm }
                .forEach { summary ->
                    if (summary.isFromInvitePair(bipeInvitePairIds)) {
                        bipeInvitePairs.autoAccept(summary.roomId)
                    } else {
                        bipeInvitePairs.recheck(summary.roomId)
                    }
                }
        }
        val loadingState by roomListDataSource.loadingState.collectAsState()
        val showEmpty by remember {
            derivedStateOf {
                (loadingState as? RoomList.LoadingState.Loaded)?.numberOfRooms == 0
            }
        }
        val showSkeleton by remember {
            derivedStateOf {
                loadingState == RoomList.LoadingState.NotLoaded || roomSummaries is AsyncData.Loading
            }
        }
        val seenRoomInvites by remember { seenInvitesStore.seenRoomIds() }.collectAsState(emptySet())
        val securityBannerState by rememberSecurityBannerState(securityBannerDismissed)
        return when {
            showEmpty -> RoomListContentState.Empty(
                securityBannerState = securityBannerState,
                bipeGroups = bipeShowsGroups,
            )
            showSkeleton -> RoomListContentState.Skeleton(count = 16)
            else -> {
                coldStartWatcher.onRoomListVisible()

                // Bipe: DM invites are message requests (usernames.md "First contact"). They only show under the
                // Pedidos filter. The SDK has no cheap "shares a joined room" check, so every DM invite counts,
                // except one from an invite pair (ADR 0034), which is listed and accepted.
                // Bipe: the self-chat shows in the Você tab only (ADR 0053).
                // Bipe (bipe#464): a cleared DM leaves every list, the archive too, until a newer event arrives.
                val allSummaries = roomSummaries.dataOrNull().orEmpty().filter {
                    it.roomId != bipeSelfChatRoomId && !it.bipeIsClearedDm(bipeClearedChats)
                }
                // Bipe (bipe#435): archived chats (m.lowpriority) leave the tab and sit behind the Arquivadas row.
                val (archived, active) = allSummaries.partition { it.isLowPriority && it.displayType != RoomSummaryDisplayType.INVITE }
                val tabSummaries = if (bipeShowsArchived) archived else active
                val (requests, listed) = if (showsRequests) {
                    emptyList<RoomListRoomSummary>() to tabSummaries
                } else {
                    tabSummaries.partition {
                        it.displayType == RoomSummaryDisplayType.INVITE && it.isDm && !it.isFromInvitePair(bipeInvitePairIds)
                    }
                }

                // Bipe: numberOfRooms counts every room, not the rooms of the tab, and cleared DMs too (bipe#479).
                // A tab with no rows (no rooms, no Pedidos, no Arquivadas) shows its own empty state.
                // With a filter on, the filter empty state shows instead.
                val bipeTabIsEmpty = listed.isEmpty() && archived.isEmpty() && requests.isEmpty()
                if (bipeTabIsEmpty && !bipeHasAnyFilterSelected) {
                    return RoomListContentState.Empty(
                        securityBannerState = securityBannerState,
                        bipeGroups = bipeShowsGroups,
                    )
                }

                RoomListContentState.Rooms(
                    securityBannerState = securityBannerState,
                    showNewNotificationSoundBanner = showNewNotificationSoundBanner,
                    showUnreadCount = showUnreadCount,
                    fullScreenIntentPermissionsState = fullScreenIntentPermissionsPresenter.present(),
                    batteryOptimizationState = batteryOptimizationPresenter.present(),
                    summaries = listed.toImmutableList(),
                    showAllActivity = showAllActivity,
                    seenRoomInvites = seenRoomInvites.toImmutableSet(),
                    bipeRequestCount = requests.size,
                    bipeArchivedCount = archived.size,
                    bipeShowsArchived = bipeShowsArchived,
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.showContextMenu(event: RoomListEvent.ShowContextMenu, contextMenuState: MutableState<RoomListState.ContextMenu>) = launch {
        val initialState = RoomListState.ContextMenu.Shown(
            roomId = event.roomSummary.roomId,
            roomName = event.roomSummary.name,
            isDm = event.roomSummary.isDm,
            isFavorite = event.roomSummary.isFavorite,
            isArchived = event.roomSummary.isLowPriority,
            hasNewContent = event.roomSummary.hasNewContent,
            isMuted = event.roomSummary.userDefinedNotificationMode == RoomNotificationMode.MUTE,
        )
        contextMenuState.value = initialState

        client.getRoom(event.roomSummary.roomId)?.use { room ->

            val isShowingContextMenuFlow = snapshotFlow { contextMenuState.value is RoomListState.ContextMenu.Shown }
                .distinctUntilChanged()

            val isFavoriteFlow = room.roomInfoFlow
                .map { it.isFavorite }
                .distinctUntilChanged()

            isFavoriteFlow
                .onEach { isFavorite ->
                    contextMenuState.value = initialState.copy(isFavorite = isFavorite)
                }
                .flatMapLatest { isShowingContextMenuFlow }
                .takeWhile { isShowingContextMenu -> isShowingContextMenu }
                .collect()
        }
    }

    private fun CoroutineScope.setRoomIsFavorite(roomId: RoomId, isFavorite: Boolean) = launch {
        client.getRoom(roomId)?.use { room ->
            room.setIsFavorite(isFavorite)
                .onSuccess {
                    analyticsService.captureInteraction(name = Interaction.Name.MobileRoomListRoomContextMenuFavouriteToggle)
                }
        }
    }

    // Bipe (bipe#435): archive is the m.lowpriority tag, so other clients agree.
    private fun CoroutineScope.setRoomIsArchived(roomId: RoomId, isArchived: Boolean) = launch {
        client.getRoom(roomId)?.use { room ->
            room.setIsLowPriority(isArchived)
                .onFailure { Timber.e(it, "Bipe: could not set room $roomId archived: $isArchived") }
        }
    }

    // Bipe: one-tap mute from the room list. No duration, the room stays muted until unmuted.
    private fun CoroutineScope.setRoomIsMuted(roomId: RoomId, isMuted: Boolean) = launch {
        val notificationSettingsService = client.notificationSettingsService
        if (isMuted) {
            notificationSettingsService.muteRoom(roomId)
        } else {
            client.getRoom(roomId)?.use { room ->
                notificationSettingsService.unmuteRoom(roomId, room.roomInfoFlow.value.isEncrypted == true, room.isDm())
            }
        }
    }

    private fun CoroutineScope.markAsRead(roomId: RoomId) = launch {
        markRoomAsRead(roomId)
            .onSuccess {
                analyticsService.captureInteraction(name = Interaction.Name.MobileRoomListRoomContextMenuUnreadToggle)
            }
    }

    private fun CoroutineScope.markAsUnread(roomId: RoomId) = launch {
        client.getRoom(roomId)?.use { room ->
            room.setUnreadFlag(isUnread = true)
                .onSuccess {
                    analyticsService.captureInteraction(name = Interaction.Name.MobileRoomListRoomContextMenuUnreadToggle)
                }
        }
    }
}

/** Bipe (bipe#464): a joined DM that the user cleared and that has no event after the marker. */
internal fun RoomListRoomSummary.bipeIsClearedDm(clearedChats: ClearedChats) =
    isDm && displayType == RoomSummaryDisplayType.ROOM && clearedChats.isHidden(roomId.value, bipeLatestEventTimestamp)

/** Bipe ADR 0034: the invite was sent by one of the account's invite pairs. */
private fun RoomListRoomSummary.isFromInvitePair(invitePairIds: Set<String>) = inviteSender?.userId?.value in invitePairIds
