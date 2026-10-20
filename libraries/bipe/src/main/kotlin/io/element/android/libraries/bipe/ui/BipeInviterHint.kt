/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Bipe ADR 0034: above the composer in the chat with the inviter, until the user sends them a first message.
 * ADR 0136: [inviterKnowsName] adds "já sabe que você chegou" above it while the inviter has not written yet.
 */
@Composable
fun BipeInviterHint(
    inviterHandle: String,
    onSayHiClick: () -> Unit,
    modifier: Modifier = Modifier,
    inviterKnowsName: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (inviterKnowsName != null) {
            Text(
                text = stringResource(R.string.bipe_inviter_knows_you_joined, inviterKnowsName),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.bipe_inviter_hint, inviterHandle),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.bipe_inviter_hint_action),
                size = ButtonSize.Small,
                onClick = onSayHiClick,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun BipeInviterHintPreview() = ElementPreview {
    BipeInviterHint(inviterHandle = "mz", onSayHiClick = {})
}

@PreviewsDayNight
@Composable
internal fun BipeInviterHintKnowsPreview() = ElementPreview {
    BipeInviterHint(inviterHandle = "mz", onSayHiClick = {}, inviterKnowsName = "MZ")
}
