/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.proseio.R

/** Groups MVP (proseio#98): what a member missed since the last visit. iOS: `ProseioMissedSummary`. */
@Immutable
data class ProseioMissedSummary(
    val lines: List<Line>,
    val otherCount: Int,
) {
    @Immutable
    data class Line(
        val kind: Kind,
        val eventId: String,
        val senderName: String,
        val text: String,
    )

    enum class Kind { MENTION, REPLY, POLL }

    companion object {
        /** Below this many unread messages the block stays hidden: a short gap reads fine without it. */
        const val MINIMUM_UNREAD_COUNT = 20
        const val MAXIMUM_LINES = 4
    }
}

/** The "O que você perdeu" block above the read marker. Each line opens its message. */
@Composable
fun ProseioMissedSummaryView(
    summary: ProseioMissedSummary,
    onLineClick: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(ElementTheme.colors.bgSubtleSecondary, shape)
            .border(0.5.dp, ElementTheme.colors.borderInteractiveSecondary, shape)
    ) {
        Text(
            text = stringResource(R.string.proseio_group_missed_title),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textActionAccent,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        summary.lines.forEach { line ->
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLineClick(line.eventId) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = lineTitle(line),
                    style = ElementTheme.typography.fontBodySmMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                if (line.kind != ProseioMissedSummary.Kind.POLL) {
                    Text(
                        text = line.text,
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider()
        Text(
            text = stringResource(R.string.proseio_group_missed_more, summary.otherCount.toString()),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun lineTitle(line: ProseioMissedSummary.Line): String = when (line.kind) {
    ProseioMissedSummary.Kind.MENTION -> stringResource(R.string.proseio_group_missed_mention, line.senderName)
    ProseioMissedSummary.Kind.REPLY -> stringResource(R.string.proseio_group_missed_reply, line.senderName)
    ProseioMissedSummary.Kind.POLL -> stringResource(R.string.proseio_group_missed_poll, line.text)
}

@PreviewsDayNight
@Composable
internal fun ProseioMissedSummaryViewPreview() = ElementPreview {
    ProseioMissedSummaryView(
        summary = ProseioMissedSummary(
            lines = listOf(
                ProseioMissedSummary.Line(ProseioMissedSummary.Kind.MENTION, "\$1", "Bia", "@gabi você leva o carvão?"),
                ProseioMissedSummary.Line(ProseioMissedSummary.Kind.REPLY, "\$2", "Léo", "Fechado, 13h lá em casa"),
                ProseioMissedSummary.Line(ProseioMissedSummary.Kind.POLL, "\$3", "Léo", "Sábado ou domingo?"),
            ),
            otherCount = 142,
        ),
        onLineClick = {},
    )
}
