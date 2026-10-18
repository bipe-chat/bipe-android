/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.generated.BipeOnboardingPrompt
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text

/** "Escolha seu @" at the top of the chat list while the account has no @ (bipe-chat/bipe#200, bipe-chat/bipe#415). Port of iOS. */
@Composable
fun BipeHandlePromptRow(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prompt = BipeOnboardingPrompt.HANDLE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(prompt.primary.title), onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(ElementTheme.colors.iconAccentPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "@", style = ElementTheme.typography.fontBodyLgMedium, color = ElementTheme.colors.iconOnSolidPrimary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.bipe_handle_prompt_title),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.bipe_handle_prompt_body),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        prompt.text.firstOrNull()?.let { skip ->
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = CompoundIcons.Close(),
                    contentDescription = stringResource(skip.title),
                    tint = ElementTheme.colors.iconTertiary,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun BipeHandlePromptRowPreview() = ElementPreview {
    BipeHandlePromptRow(onClick = {}, onDismiss = {})
}
