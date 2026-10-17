/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.timeline

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemPollContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemReadMarkerModel
import io.element.android.libraries.bipe.ui.BipeMissedSummary
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetails
import io.element.android.libraries.textcomposer.mentions.MentionType
import io.element.android.libraries.textcomposer.mentions.getMentionSpans

/**
 * Groups MVP (bipe#98): builds the "O que você perdeu" block from the loaded items after the read marker.
 * Fixed rules over decrypted items on the device. Nothing goes to the server.
 */
internal fun bipeMissedSummary(items: List<TimelineItem>, currentUserId: UserId, isDm: Boolean): BipeMissedSummary? {
    if (isDm) return null
    val markerIndex = items.indexOfFirst { (it as? TimelineItem.Virtual)?.model is TimelineItemReadMarkerModel }
    // reverseLayout: index 0 is the newest item, so the unread items come before the marker.
    if (markerIndex <= 0) return null
    val unread = items.subList(0, markerIndex)
        .asReversed()
        .filterIsInstance<TimelineItem.Event>()
        .filter { it.content !is TimelineItemStateContent }
    if (unread.size < BipeMissedSummary.MINIMUM_UNREAD_COUNT) return null

    val matches = unread.mapNotNull { it.bipeMissedLine(currentUserId) }
    if (matches.isEmpty()) return null
    val lines = matches.take(BipeMissedSummary.MAXIMUM_LINES)
    return BipeMissedSummary(lines = lines, otherCount = unread.size - lines.size)
}

private fun TimelineItem.Event.bipeMissedLine(currentUserId: UserId): BipeMissedSummary.Line? {
    val eventId = eventId ?: return null
    if (isMine) return null
    val senderName = senderProfile.getDisambiguatedDisplayName(senderId)
    val content = content
    if (content is TimelineItemPollContent) {
        if (content.isEnded || content.answerItems.any { it.isSelected }) return null
        return BipeMissedSummary.Line(BipeMissedSummary.Kind.POLL, eventId.value, senderName, content.question)
    }
    if (content is TimelineItemTextBasedContent && content.mentions(currentUserId)) {
        return BipeMissedSummary.Line(BipeMissedSummary.Kind.MENTION, eventId.value, senderName, content.plainText)
    }
    val reply = inReplyTo
    if (reply is InReplyToDetails.Ready && reply.senderId == currentUserId) {
        val text = (content as? TimelineItemTextBasedContent)?.plainText.orEmpty()
        return BipeMissedSummary.Line(BipeMissedSummary.Kind.REPLY, eventId.value, senderName, text)
    }
    return null
}

private fun TimelineItemTextBasedContent.mentions(userId: UserId): Boolean =
    formattedBody.getMentionSpans().any { span ->
        when (val type = span.type) {
            is MentionType.User -> type.userId == userId
            MentionType.Everyone -> true
            else -> false
        }
    }
