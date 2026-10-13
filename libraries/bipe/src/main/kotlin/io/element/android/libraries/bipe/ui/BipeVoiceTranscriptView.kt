/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.bipe.R

private const val COLLAPSED_LINE_LIMIT = 3

// A character count approximates truncation: measuring rendered lines costs a layout pass per row. Same as iOS.
private const val COLLAPSE_THRESHOLD = 140

/** Bipe: the sender-side transcript under a voice message (proseio/docs/audio.md, ADR 0023). */
@Composable
fun BipeVoiceTranscriptView(
    text: String,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.bipe_voice_transcript_title),
            style = ElementTheme.typography.fontBodyXsMedium,
            color = ElementTheme.colors.textSecondary,
        )
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
            maxLines = if (isExpanded) Int.MAX_VALUE else COLLAPSED_LINE_LIMIT,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.length > COLLAPSE_THRESHOLD) {
            Text(
                text = stringResource(if (isExpanded) R.string.bipe_action_show_less else R.string.bipe_action_show_more),
                style = ElementTheme.typography.fontBodySmMedium,
                color = ElementTheme.colors.textActionAccent,
                modifier = Modifier.clickable { isExpanded = !isExpanded },
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun BipeVoiceTranscriptViewPreview() = ElementPreview {
    BipeVoiceTranscriptView(text = "Oi, chego às sete. Leva o carregador?")
}
