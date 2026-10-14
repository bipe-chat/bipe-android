/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.qrcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.qrcode.QrCodeImage

/** A QR code for a Bipe link, an optional title, a footer and a share button. Port of BipeQRCodeSheet on iOS. */
@Composable
fun BipeQrCodeContent(
    url: String,
    footer: String,
    shareText: String,
    shareLabel: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = ElementTheme.typography.fontHeadingSmMedium,
                color = ElementTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
        QrCodeImage(
            data = url,
            forceMaxBrightness = false,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Text(
            text = footer,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            text = shareLabel,
            leadingIcon = IconSource.Vector(CompoundIcons.ShareAndroid()),
            onClick = {
                context.startSharePlainTextIntent(activityResultLauncher = null, chooserTitle = null, text = shareText)
            },
        )
    }
}
