/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.tabs

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.roomlist.RoomSummary

/**
 * Tab bar badges (ADR 0053). Conversas counts the notifications of direct chats. Grupos counts the notifications of
 * groups. The quiet-group push rules (ADR 0026) already limit a group's count to mentions, replies and new polls.
 * The split uses the SDK DM flag, as the room list category filter does. Spaces and the self-chat do not count.
 */
data class ProseioTabBadges(
    val chats: Long,
    val groups: Long,
) {
    companion object {
        val NONE = ProseioTabBadges(chats = 0, groups = 0)

        fun from(summaries: List<RoomSummary>, selfChatRoomId: RoomId?): ProseioTabBadges {
            var chats = 0L
            var groups = 0L
            for (summary in summaries) {
                val info = summary.info
                if (info.isSpace || info.currentUserMembership != CurrentUserMembership.JOINED || summary.roomId == selfChatRoomId) continue
                if (info.isDm) {
                    chats += info.numUnreadNotifications
                } else {
                    groups += info.numUnreadNotifications
                }
            }
            return ProseioTabBadges(chats = chats, groups = groups)
        }
    }
}
