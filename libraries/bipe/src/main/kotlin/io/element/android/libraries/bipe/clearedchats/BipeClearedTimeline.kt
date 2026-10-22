/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.clearedchats

import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.virtual.VirtualTimelineItem

/**
 * The timeline of a cleared DM (bipe-chat/bipe#464). The items come oldest first, as the SDK sends them.
 */
object BipeClearedTimeline {
    /**
     * Drops every event with a time up to [marker], with its reactions and read receipts, and the room beginning.
     * When events were dropped, the loading indicator and the read marker before the first kept event go too, and
     * only the day divider of the first kept event stays. A null [marker] keeps all items.
     */
    fun filter(items: List<MatrixTimelineItem>, marker: Long?): List<MatrixTimelineItem> {
        if (marker == null) return items
        val firstKept = items.indexOfFirst { it is MatrixTimelineItem.Event && ClearedChats.keepsEvent(marker, it.event.timestamp) }
        if (firstKept < 0) {
            return items.filter { it is MatrixTimelineItem.Virtual && it.virtual.staysWithoutEvents() }
        }
        val before = items.subList(0, firstKept)
        val head = if (before.any { it is MatrixTimelineItem.Event }) {
            listOfNotNull(before.lastOrNull { it is MatrixTimelineItem.Virtual && it.virtual is VirtualTimelineItem.DayDivider })
        } else {
            before
        }
        val tail = items.subList(firstKept, items.size).filter {
            it !is MatrixTimelineItem.Event || ClearedChats.keepsEvent(marker, it.event.timestamp)
        }
        return (head + tail).filterNot { it is MatrixTimelineItem.Virtual && it.virtual is VirtualTimelineItem.RoomBeginning }
    }

    /**
     * Drops every event with a time up to [marker] and keeps the other items as they are. For lists that are not the
     * room timeline: the pinned events (bipe-chat/bipe#479). A null [marker] keeps all items.
     */
    fun keptEvents(items: List<MatrixTimelineItem>, marker: Long?): List<MatrixTimelineItem> {
        if (marker == null) return items
        return items.filter { it !is MatrixTimelineItem.Event || ClearedChats.keepsEvent(marker, it.event.timestamp) }
    }

    /** True when the loaded items reach the marker, so back-pagination has nothing more to show. */
    fun reachesMarker(items: List<MatrixTimelineItem>, marker: Long?): Boolean {
        if (marker == null) return false
        return items.any { it is MatrixTimelineItem.Event && !ClearedChats.keepsEvent(marker, it.event.timestamp) }
    }

    private fun VirtualTimelineItem.staysWithoutEvents(): Boolean = when (this) {
        is VirtualTimelineItem.LoadingIndicator -> direction == Timeline.PaginationDirection.FORWARDS
        VirtualTimelineItem.TypingNotification -> true
        is VirtualTimelineItem.DayDivider,
        VirtualTimelineItem.ReadMarker,
        VirtualTimelineItem.RoomBeginning,
        VirtualTimelineItem.LastForwardIndicator -> false
    }
}
