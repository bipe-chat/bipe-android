/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.voice.BipeVoiceTranscriptDraft
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField

private const val EDITOR_MAX_LINES = 5

/** Bipe: the transcript of a recorded voice message, above the preview. The sender corrects it before sending (ADR 0023). Port of iOS. */
@Composable
fun BipeVoiceTranscriptEditor(
    draft: BipeVoiceTranscriptDraft,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.bipe_voice_transcript_title),
            style = ElementTheme.typography.fontBodyXsMedium,
            color = ElementTheme.colors.textSecondary,
        )
        when (draft) {
            BipeVoiceTranscriptDraft.Transcribing -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.bipe_voice_transcribing),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            is BipeVoiceTranscriptDraft.Ready -> TextField(
                value = draft.text,
                onValueChange = onTextChange,
                placeholder = stringResource(R.string.bipe_voice_transcript_placeholder),
                maxLines = EDITOR_MAX_LINES,
                modifier = Modifier.fillMaxWidth(),
            )
            BipeVoiceTranscriptDraft.Unavailable -> Text(
                text = stringResource(R.string.bipe_voice_transcript_unavailable),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun BipeVoiceTranscriptEditorPreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BipeVoiceTranscriptEditor(draft = BipeVoiceTranscriptDraft.Transcribing, onTextChange = {})
        BipeVoiceTranscriptEditor(draft = BipeVoiceTranscriptDraft.Ready("Oi, chego às sete. Leva o carregador?"), onTextChange = {})
        BipeVoiceTranscriptEditor(draft = BipeVoiceTranscriptDraft.Unavailable, onTextChange = {})
    }
}
