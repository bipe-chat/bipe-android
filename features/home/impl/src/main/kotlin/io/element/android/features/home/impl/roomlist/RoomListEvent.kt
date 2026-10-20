/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.matrix.api.core.RoomId

sealed interface RoomListEvent {
    data class UpdateVisibleRange(val range: IntRange) : RoomListEvent
    data object DismissRequestVerificationPrompt : RoomListEvent
    data object DismissBanner : RoomListEvent

    /** Bipe (bipe-chat/bipe#415): the "Escolha seu @" card was used or dismissed. */
    data object BipeDismissHandlePrompt : RoomListEvent

    /** Bipe (ADR 0117, bipe-chat/bipe#386): the remote banner notice was closed. */
    data object BipeDismissRemoteBanner : RoomListEvent

    /** Bipe (ADR 0117): read the remote banner again, on launch and on each return to the foreground. */
    data object BipeRefreshRemoteBanner : RoomListEvent
    data object DismissNewNotificationSoundBanner : RoomListEvent
    data object ToggleSearchResults : RoomListEvent
    data class ShowContextMenu(val roomSummary: RoomListRoomSummary) : RoomListEvent

    data class AcceptInvite(val roomSummary: RoomListRoomSummary) : RoomListEvent
    data class DeclineInvite(val roomSummary: RoomListRoomSummary, val blockUser: Boolean) : RoomListEvent
    data class ShowDeclineInviteMenu(val roomSummary: RoomListRoomSummary) : RoomListEvent
    data object HideDeclineInviteMenu : RoomListEvent

    /** Bipe: the Grupos tab shows groups, the Conversas tab shows direct chats (ADR 0053). */
    data class BipeShowGroups(val showGroups: Boolean) : RoomListEvent

    /** Bipe (bipe#435): the list shows the archived chats of the tab, or leaves them. */
    data class BipeShowArchived(val showArchived: Boolean) : RoomListEvent

    sealed interface ContextMenuEvent : RoomListEvent
    data object HideContextMenu : ContextMenuEvent
    data class LeaveRoom(val roomId: RoomId, val needsConfirmation: Boolean) : ContextMenuEvent
    data class MarkAsRead(val roomId: RoomId) : ContextMenuEvent
    data class MarkAsUnread(val roomId: RoomId) : ContextMenuEvent
    data class SetRoomIsFavorite(val roomId: RoomId, val isFavorite: Boolean) : ContextMenuEvent
    data class SetRoomIsMuted(val roomId: RoomId, val isMuted: Boolean) : ContextMenuEvent

    /** Bipe (bipe#435): from the swipe or the menu. */
    data class BipeSetRoomIsArchived(val roomId: RoomId, val isArchived: Boolean) : ContextMenuEvent
}
