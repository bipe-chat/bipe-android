/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.timeline

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemBipeHandleChangeModel
import io.element.android.libraries.bipe.api.BipeHandleChange
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.MembershipChange
import io.element.android.libraries.matrix.api.timeline.item.event.RoomMembershipContent

/**
 * Bipe: handle change lines in the timeline (ADR 0066). A change of a person shows in each room where the person is a
 * joined member, at its time, inside the loaded range only, and after the person joined (when the join is in the range).
 */
internal object BipeHandleChangeLines {
    /** Matrix ID to the time (ms) of the latest join in the loaded timeline. */
    fun joinTimes(items: List<MatrixTimelineItem>): Map<String, Long> {
        val joins = HashMap<String, Long>()
        for (item in items) {
            val event = (item as? MatrixTimelineItem.Event)?.event ?: continue
            val content = event.content as? RoomMembershipContent ?: continue
            if (content.change == MembershipChange.JOINED || content.change == MembershipChange.INVITATION_ACCEPTED) {
                val userId = content.userId.value
                joins[userId] = maxOf(joins[userId] ?: Long.MIN_VALUE, event.timestamp)
            }
        }
        return joins
    }

    /**
     * [items] newest first, as the timeline renders them. Returns the list with a virtual item before (newer than) the newest
     * event that is not newer than each change. Changes older than the oldest loaded event are left out.
     */
    fun insert(
        items: List<TimelineItem>,
        changes: Map<String, List<BipeHandleChange>>,
        members: List<RoomMember>,
        joinTimes: Map<String, Long>,
    ): List<TimelineItem> {
        if (changes.isEmpty()) return items
        val lines = members
            .filter { it.membership == RoomMembershipState.JOIN }
            .flatMap { member ->
                val userId = member.userId.value
                val joinedAt = joinTimes[userId] ?: Long.MIN_VALUE
                changes[userId].orEmpty()
                    .filter { it.at.toEpochMilli() > joinedAt }
                    .map { change ->
                        TimelineItem.Virtual(
                            id = UniqueId("bipe_handle_${userId}_${change.at.toEpochMilli()}"),
                            model = TimelineItemBipeHandleChangeModel(
                                userId = userId,
                                name = member.displayName?.takeIf { it.isNotBlank() } ?: userId,
                                oldHandle = change.old,
                                newHandle = change.new,
                                atMillis = change.at.toEpochMilli(),
                            ),
                        )
                    }
            }
            .sortedByDescending { (it.model as TimelineItemBipeHandleChangeModel).atMillis }
        if (lines.isEmpty()) return items

        val result = ArrayList<TimelineItem>(items.size + lines.size)
        var next = 0
        for (item in items) {
            val time = item.sentTimeMillis()
            if (time != null) {
                while (next < lines.size && (lines[next].model as TimelineItemBipeHandleChangeModel).atMillis >= time) {
                    result.add(lines[next])
                    next++
                }
            }
            result.add(item)
        }
        return result
    }

    private fun TimelineItem.sentTimeMillis(): Long? = when (this) {
        is TimelineItem.Event -> sentTimeMillis.takeIf { it > 0 }
        is TimelineItem.GroupedEvents -> events.maxOfOrNull { it.sentTimeMillis }?.takeIf { it > 0 }
        is TimelineItem.Virtual -> null
    }
}
