/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text

/** The "O que você perdeu" block above the read marker. Each line opens its message. */
@Composable
fun BipeMissedSummaryView(
    summary: BipeMissedSummary,
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
            text = stringResource(R.string.bipe_group_missed_title),
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
                if (line.kind != BipeMissedSummary.Kind.POLL) {
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
            text = stringResource(R.string.bipe_group_missed_more, summary.otherCount.toString()),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun lineTitle(line: BipeMissedSummary.Line): String = when (line.kind) {
    BipeMissedSummary.Kind.MENTION -> stringResource(R.string.bipe_group_missed_mention, line.senderName)
    BipeMissedSummary.Kind.REPLY -> stringResource(R.string.bipe_group_missed_reply, line.senderName)
    BipeMissedSummary.Kind.POLL -> stringResource(R.string.bipe_group_missed_poll, line.text)
}

@PreviewsDayNight
@Composable
internal fun BipeMissedSummaryViewPreview() = ElementPreview {
    BipeMissedSummaryView(
        summary = BipeMissedSummary(
            lines = listOf(
                BipeMissedSummary.Line(BipeMissedSummary.Kind.MENTION, "\$1", "Bia", "@gabi você leva o carvão?"),
                BipeMissedSummary.Line(BipeMissedSummary.Kind.REPLY, "\$2", "Léo", "Fechado, 13h lá em casa"),
                BipeMissedSummary.Line(BipeMissedSummary.Kind.POLL, "\$3", "Léo", "Sábado ou domingo?"),
            ),
            otherCount = 142,
        ),
        onLineClick = {},
    )
}
