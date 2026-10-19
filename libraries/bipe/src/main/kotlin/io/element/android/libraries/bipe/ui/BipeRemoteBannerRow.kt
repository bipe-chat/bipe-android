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
import io.element.android.libraries.bipe.banner.BipeRemoteBannerShown
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * The remote critical banner above the chat list (ADR 0117, bipe-chat/bipe#386). Port of iOS. A notice can be closed;
 * an update banner opens the update link and has no close button.
 */
@Composable
fun BipeRemoteBannerRow(
    banner: BipeRemoteBannerShown,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(ElementTheme.colors.iconAccentPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (banner.isUpdate) CompoundIcons.Download() else CompoundIcons.Info(),
                contentDescription = null,
                tint = ElementTheme.colors.iconOnSolidPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = banner.title, style = ElementTheme.typography.fontBodyLgMedium, color = ElementTheme.colors.textPrimary)
            Text(text = banner.text, style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textSecondary)
            if (banner.isUpdate) {
                Text(
                    text = stringResource(R.string.bipe_remote_banner_update),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textActionAccent,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (!banner.isUpdate) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = CompoundIcons.Close(),
                    contentDescription = stringResource(R.string.bipe_remote_banner_dismiss),
                    tint = ElementTheme.colors.iconTertiary,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun BipeRemoteBannerRowPreview() = ElementPreview {
    Column {
        val notice = BipeRemoteBannerShown(
            title = "Instabilidade agora",
            text = "Algumas mensagens podem demorar. Estamos cuidando disso.",
            url = "https://bipe.chat/",
            isUpdate = false,
        )
        BipeRemoteBannerRow(banner = notice, onClick = {}, onDismiss = {})
        BipeRemoteBannerRow(banner = notice.copy(isUpdate = true), onClick = {}, onDismiss = {})
    }
}
