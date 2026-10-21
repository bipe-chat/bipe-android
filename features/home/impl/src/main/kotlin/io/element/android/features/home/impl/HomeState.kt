/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spaces.HomeSpacesState
import io.element.android.libraries.bipe.tabs.BipeTabBadges
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class HomeState(
    /**
     * The current user of this session, in case of multiple accounts, will contains 3 items, with the
     * current user in the middle.
     */
    val currentUserAndNeighbors: ImmutableList<MatrixUser>,
    val showAvatarIndicator: Boolean,
    val hasNetworkConnection: Boolean,
    val currentHomeNavigationBarItem: HomeNavigationBarItem,
    val roomListState: RoomListState,
    val homeSpacesState: HomeSpacesState,
    val snackbarMessage: SnackbarMessage?,
    val canReportBug: Boolean,
    val eventSink: (HomeEvent) -> Unit,
    // Bipe: tab bar badges (ADR 0053)
    val bipeBadges: BipeTabBadges,
    /** Bipe: every account on the phone, current first, for the Você tab menu (ADR 0086, ADR 0135). */
    val bipeAccounts: ImmutableList<MatrixUser>,
    /** Bipe: the Você tab menu shows "Nova conta" while the cap allows one more account (ADR 0086). */
    val bipeCanAddAccount: Boolean,
    /** Bipe: the other accounts with a notification in the shade. Never the current account. */
    val bipeNotifiedAccounts: ImmutableSet<UserId>,
) {
    val isBackHandlerEnabled = currentHomeNavigationBarItem != HomeNavigationBarItem.Chats || roomListState.spaceFiltersState is SpaceFiltersState.Selected
    val displayRoomListFilters = currentHomeNavigationBarItem.isBipeRoomList && roomListState.displayFilters // Bipe: Conversas and Grupos
}
