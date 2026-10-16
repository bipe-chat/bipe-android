/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.tabs

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.roomlist.RoomSummary

/**
 * Tab bar badges (ADR 0053). Conversas counts the notifications of direct chats. Grupos counts the notifications of
 * groups. The quiet-group push rules (ADR 0026) already limit a group's count to mentions, replies and new polls.
 * A group invite the user has not opened counts 1 on Grupos. DM invites stay behind the Pedidos filter.
 * The split uses the SDK DM flag, as the room list category filter does. Spaces and the self-chat do not count.
 */
data class BipeTabBadges(
    val chats: Long,
    val groups: Long,
) {
    companion object {
        val NONE = BipeTabBadges(chats = 0, groups = 0)

        fun from(summaries: List<RoomSummary>, selfChatRoomId: RoomId?, seenInviteRoomIds: Set<RoomId>): BipeTabBadges {
            var chats = 0L
            var groups = 0L
            for (summary in summaries) {
                val info = summary.info
                if (info.isSpace || summary.roomId == selfChatRoomId) continue
                if (info.currentUserMembership == CurrentUserMembership.INVITED) {
                    if (!info.isDm && summary.roomId !in seenInviteRoomIds) groups += 1
                    continue
                }
                if (info.currentUserMembership != CurrentUserMembership.JOINED) continue
                if (info.isDm) {
                    chats += info.numUnreadNotifications
                } else {
                    groups += info.numUnreadNotifications
                }
            }
            return BipeTabBadges(chats = chats, groups = groups)
        }
    }
}
