/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.contentType
import io.element.android.features.home.impl.filters.RoomListFilter
import io.element.android.features.home.impl.filters.RoomListFiltersEmptyStateResources
import io.element.android.features.home.impl.filters.RoomListFiltersEvent
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.filters.aRoomListFiltersState
import io.element.android.features.home.impl.filters.selection.FilterSelectionState
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.features.home.impl.roomlist.RoomListContentStatePreviewParam
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.home.impl.roomlist.SecurityBannerState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.anUnselectedSpaceFiltersState
import io.element.android.libraries.bipe.banner.BipeRemoteBannerShown
import io.element.android.libraries.bipe.ui.BipeHandlePromptRow
import io.element.android.libraries.bipe.ui.BipeInviterRow
import io.element.android.libraries.bipe.ui.BipeRemoteBannerRow
import io.element.android.libraries.bipe.ui.BipeRequestsRow
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.OnVisibleRangeChangeEffect
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import io.element.android.libraries.bipe.R as BipeR

@Composable
fun RoomListContentView(
    contentState: RoomListContentState,
    filtersState: RoomListFiltersState,
    spaceFiltersState: SpaceFiltersState,
    lazyListState: LazyListState,
    hideInvitesAvatars: Boolean,
    eventSink: (RoomListEvent) -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    onCreateRoomClick: () -> Unit,
    onBipeCreateGroupClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    bipeInviterHandle: String? = null,
    onBipeInviterClick: () -> Unit = {},
    bipeHandlePrompt: BipeHandlePromptActions? = null,
    bipeRemoteBanner: BipeRemoteBannerActions? = null,
) {
    when (contentState) {
        is RoomListContentState.Skeleton -> {
            SkeletonView(
                modifier = modifier,
                count = contentState.count,
                contentPadding = contentPadding,
            )
        }
        is RoomListContentState.Empty -> {
            EmptyView(
                modifier = modifier.padding(contentPadding),
                state = contentState,
                eventSink = eventSink,
                onSetUpRecoveryClick = onSetUpRecoveryClick,
                onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                onCreateRoomClick = if (contentState.bipeGroups) onBipeCreateGroupClick else onCreateRoomClick,
                bipeInviterHandle = bipeInviterHandle,
                onBipeInviterClick = onBipeInviterClick,
                bipeHandlePrompt = bipeHandlePrompt,
                bipeRemoteBanner = bipeRemoteBanner,
            )
        }
        is RoomListContentState.Rooms -> {
            RoomsView(
                modifier = modifier,
                state = contentState,
                hideInvitesAvatars = hideInvitesAvatars,
                filtersState = filtersState,
                spaceFiltersState = spaceFiltersState,
                eventSink = eventSink,
                onSetUpRecoveryClick = onSetUpRecoveryClick,
                onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                onRoomClick = onRoomClick,
                lazyListState = lazyListState,
                contentPadding = contentPadding,
                bipeInviterHandle = bipeInviterHandle,
                onBipeInviterClick = onBipeInviterClick,
                bipeHandlePrompt = bipeHandlePrompt,
                bipeRemoteBanner = bipeRemoteBanner,
            )
        }
    }
}

@Composable
private fun SkeletonView(
    count: Int,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        repeat(count) { index ->
            item {
                RoomSummaryPlaceholderRow()
                if (index != count - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Ref: https://www.figma.com/design/pDlJZGBsri47FNTXMnEdXB/Compound-Android-Templates?node-id=8-4656
 */
@Composable
private fun EmptyView(
    state: RoomListContentState.Empty,
    eventSink: (RoomListEvent) -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onCreateRoomClick: () -> Unit,
    bipeInviterHandle: String?,
    onBipeInviterClick: () -> Unit,
    bipeHandlePrompt: BipeHandlePromptActions?,
    bipeRemoteBanner: BipeRemoteBannerActions?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // Bipe: the Grupos tab has its own text and a create group button (ADR 0053).
        val text = buildString {
            append(stringResource(if (state.bipeGroups) BipeR.string.bipe_groups_empty_title else R.string.screen_roomlist_empty_title))
            append("\n")
            append(stringResource(if (state.bipeGroups) BipeR.string.bipe_groups_empty_body else R.string.screen_roomlist_empty_message))
        }
        EmptyScaffold(
            title = null,
            subtitle = text,
            bottomPart = {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    text = stringResource(if (state.bipeGroups) BipeR.string.bipe_groups_empty_action else CommonStrings.action_start_chat),
                    leadingIcon = IconSource.Vector(if (state.bipeGroups) CompoundIcons.Plus() else CompoundIcons.Chat()),
                    onClick = onCreateRoomClick,
                )
            },
            modifier = Modifier.align(Alignment.Center),
        )
        Column {
            // Bipe (ADR 0117, bipe#386): the remote critical banner sits above the account tasks.
            if (bipeRemoteBanner != null) {
                BipeRemoteBannerRow(banner = bipeRemoteBanner.banner, onClick = bipeRemoteBanner.onClick, onDismiss = bipeRemoteBanner.onDismiss)
            }
            when (state.securityBannerState) {
                SecurityBannerState.SetUpRecovery -> {
                    SetUpRecoveryKeyBanner(
                        onContinueClick = onSetUpRecoveryClick,
                        onDismissClick = { eventSink(RoomListEvent.DismissBanner) },
                    )
                }
                SecurityBannerState.RecoveryKeyConfirmation -> {
                    ConfirmRecoveryKeyBanner(
                        onContinueClick = onConfirmRecoveryKeyClick,
                        onDismissClick = { eventSink(RoomListEvent.DismissBanner) },
                    )
                }
                // Bipe (ADR 0117): one account task at a time. The inviter row waits for the recovery key,
                // "Escolha seu @" (bipe-chat/bipe#415) waits for the inviter row.
                SecurityBannerState.None -> if (bipeInviterHandle != null) {
                    BipeInviterRow(inviterHandle = bipeInviterHandle, onClick = onBipeInviterClick)
                } else if (bipeHandlePrompt != null) {
                    BipeHandlePromptRow(onClick = bipeHandlePrompt.onClick, onDismiss = bipeHandlePrompt.onDismiss)
                }
            }
        }
    }
}

@Composable
private fun RoomsView(
    state: RoomListContentState.Rooms,
    hideInvitesAvatars: Boolean,
    filtersState: RoomListFiltersState,
    spaceFiltersState: SpaceFiltersState,
    eventSink: (RoomListEvent) -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    bipeInviterHandle: String?,
    onBipeInviterClick: () -> Unit,
    bipeHandlePrompt: BipeHandlePromptActions?,
    bipeRemoteBanner: BipeRemoteBannerActions?,
    modifier: Modifier = Modifier,
) {
    val isSpaceFilterSelected = spaceFiltersState is SpaceFiltersState.Selected
    val hasAnyFilterSelected = filtersState.hasAnyFilterSelected || isSpaceFilterSelected
    if (state.summaries.isEmpty() && hasAnyFilterSelected) {
        EmptyViewForFilterStates(
            selectedFilters = filtersState.selectedFilters(),
            isSpaceFilterSelected = isSpaceFilterSelected,
            modifier = modifier.fillMaxSize()
        )
    } else {
        RoomsViewList(
            state = state,
            // Bipe: the Pedidos row opens the requests filter. Hidden while any filter is on.
            onRequestsClick = if (state.bipeRequestCount > 0 && !hasAnyFilterSelected) {
                { filtersState.eventSink(RoomListFiltersEvent.ToggleFilter(RoomListFilter.Invites)) }
            } else {
                null
            },
            // Bipe ADR 0034: hidden while any filter is on.
            bipeInviterHandle = bipeInviterHandle.takeUnless { hasAnyFilterSelected },
            onBipeInviterClick = onBipeInviterClick,
            bipeHandlePrompt = bipeHandlePrompt.takeUnless { hasAnyFilterSelected },
            bipeRemoteBanner = bipeRemoteBanner.takeUnless { hasAnyFilterSelected },
            hideInvitesAvatars = hideInvitesAvatars,
            eventSink = eventSink,
            onSetUpRecoveryClick = onSetUpRecoveryClick,
            onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
            onRoomClick = onRoomClick,
            contentPadding = contentPadding,
            lazyListState = lazyListState,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RoomsViewList(
    state: RoomListContentState.Rooms,
    onRequestsClick: (() -> Unit)?,
    bipeInviterHandle: String?,
    onBipeInviterClick: () -> Unit,
    bipeHandlePrompt: BipeHandlePromptActions?,
    bipeRemoteBanner: BipeRemoteBannerActions?,
    hideInvitesAvatars: Boolean,
    eventSink: (RoomListEvent) -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    OnVisibleRangeChangeEffect(lazyListState) { visibleRange ->
        eventSink(RoomListEvent.UpdateVisibleRange(visibleRange))
    }
    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        // Bipe (ADR 0117, bipe#386): the remote critical banner sits above the account tasks.
        if (bipeRemoteBanner != null) {
            item {
                BipeRemoteBannerRow(banner = bipeRemoteBanner.banner, onClick = bipeRemoteBanner.onClick, onDismiss = bipeRemoteBanner.onDismiss)
                HorizontalDivider()
            }
        }
        // Bipe (ADR 0117, bipe#385): the banner slot shows account tasks only, the first one in this order: recovery key,
        // inviter row (ADR 0111), full-screen intent, battery optimization. Upstream's new notification sound banner never shows.
        when {
            state.securityBannerState == SecurityBannerState.SetUpRecovery -> {
                item {
                    SetUpRecoveryKeyBanner(
                        onContinueClick = onSetUpRecoveryClick,
                        onDismissClick = { eventSink(RoomListEvent.DismissBanner) },
                    )
                }
            }
            state.securityBannerState == SecurityBannerState.RecoveryKeyConfirmation -> {
                item {
                    ConfirmRecoveryKeyBanner(
                        onContinueClick = onConfirmRecoveryKeyClick,
                        onDismissClick = { eventSink(RoomListEvent.DismissBanner) },
                    )
                }
            }
            bipeInviterHandle != null -> {
                item {
                    BipeInviterRow(inviterHandle = bipeInviterHandle, onClick = onBipeInviterClick)
                    HorizontalDivider()
                }
            }
            bipeHandlePrompt != null -> {
                item {
                    BipeHandlePromptRow(onClick = bipeHandlePrompt.onClick, onDismiss = bipeHandlePrompt.onDismiss)
                    HorizontalDivider()
                }
            }
            state.fullScreenIntentPermissionsState.shouldDisplayBanner -> {
                item {
                    FullScreenIntentPermissionBanner(state = state.fullScreenIntentPermissionsState)
                }
            }
            state.batteryOptimizationState.shouldDisplayBanner -> {
                item {
                    BatteryOptimizationBanner(state = state.batteryOptimizationState)
                }
            }
        }

        if (onRequestsClick != null) {
            item {
                BipeRequestsRow(count = state.bipeRequestCount, onClick = onRequestsClick)
                HorizontalDivider()
            }
        }

        // Note: do not use a key for the LazyColumn, or the scroll will not behave as expected if a room
        // is moved to the top of the list.
        itemsIndexed(
            items = state.summaries,
            contentType = { _, room -> room.contentType() },
        ) { index, room ->
            RoomSummaryRow(
                room = room,
                hideInviteAvatars = hideInvitesAvatars,
                isInviteSeen = room.displayType == RoomSummaryDisplayType.INVITE &&
                    state.seenRoomInvites.contains(room.roomId),
                showUnreadCount = state.showUnreadCount,
                showAllActivity = state.showAllActivity,
                onClick = onRoomClick,
                eventSink = eventSink,
            )
            if (index != state.summaries.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyViewForFilterStates(
    selectedFilters: ImmutableList<RoomListFilter>,
    isSpaceFilterSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val emptyStateResources = RoomListFiltersEmptyStateResources.fromSelectedFilters(selectedFilters, isSpaceFilterSelected) ?: return
    EmptyScaffold(
        title = stringResource(emptyStateResources.title),
        subtitle = stringResource(emptyStateResources.subtitle),
        modifier = modifier,
        bottomPart = {
            // Keep a spacer so that the content is not too low on the screen
            Spacer(modifier = Modifier.height(32.dp))
        }
    )
}

@Composable
private fun EmptyScaffold(
    title: String?,
    subtitle: String,
    modifier: Modifier = Modifier,
    bottomPart: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        title?.let {
            Text(
                text = title,
                style = ElementTheme.typography.fontHeadingMdBold,
                color = ElementTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = subtitle,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        bottomPart()
    }
}

@PreviewsDayNight
@Composable
internal fun RoomListContentViewPreview(@PreviewParameter(RoomListContentStatePreviewParam::class) state: RoomListContentState) = ElementPreview {
    RoomListContentView(
        contentState = state,
        filtersState = aRoomListFiltersState(
            filterSelectionStates = RoomListFilter.entries.map {
                FilterSelectionState(
                    filter = it,
                    isSelected = true
                )
            }
        ),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        hideInvitesAvatars = false,
        eventSink = {},
        onSetUpRecoveryClick = {},
        onConfirmRecoveryKeyClick = {},
        onRoomClick = {},
        onCreateRoomClick = {},
        onBipeCreateGroupClick = {},
        lazyListState = rememberLazyListState(),
        contentPadding = PaddingValues(0.dp),
    )
}

/** Bipe (bipe-chat/bipe#415): the "Escolha seu @" card's actions. Null when the card does not show. */
data class BipeHandlePromptActions(val onClick: () -> Unit, val onDismiss: () -> Unit)

/** Bipe (ADR 0117, bipe-chat/bipe#386): the remote critical banner and its actions. */
data class BipeRemoteBannerActions(val banner: BipeRemoteBannerShown, val onClick: () -> Unit, val onDismiss: () -> Unit)
