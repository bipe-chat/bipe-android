/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R as BipeR
import io.element.android.libraries.bipe.qrcode.BipeProfileQrSheet
import io.element.android.libraries.bipe.qrcode.shareBipeProfile
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.aMatrixUser
import io.element.android.libraries.matrix.ui.model.getAvatarData

/**
 * The top of the Você tab (ADR 0112): photo, name and @. Never the Matrix ID.
 * With a handle: the QR code and "Compartilhar perfil". A tap on the header opens Seu perfil.
 */
@Composable
internal fun BipeYouHeader(
    matrixUser: MatrixUser,
    handle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQr by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Avatar(
                avatarData = matrixUser.getAvatarData(size = AvatarSize.UserPreference),
                avatarType = AvatarType.User,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                matrixUser.displayName?.takeIf { it.isNotBlank() }?.let { name ->
                    Text(
                        text = name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = ElementTheme.typography.fontHeadingMdRegular,
                        color = ElementTheme.colors.textPrimary,
                    )
                }
                Text(
                    text = handle?.let { "@$it" } ?: stringResource(BipeR.string.bipe_settings_handle_none),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
        if (handle != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    text = stringResource(BipeR.string.bipe_profile_qr_action),
                    leadingIcon = IconSource.Vector(CompoundIcons.QrCode()),
                    size = ButtonSize.Small,
                    onClick = { showQr = true },
                )
                OutlinedButton(
                    text = stringResource(BipeR.string.bipe_profile_share),
                    leadingIcon = IconSource.Vector(CompoundIcons.ShareAndroid()),
                    size = ButtonSize.Small,
                    onClick = { context.shareBipeProfile(handle) },
                )
            }
        }
    }
    if (showQr && handle != null) {
        BipeProfileQrSheet(handle = handle, onDismiss = { showQr = false })
    }
}

@PreviewsDayNight
@Composable
internal fun BipeYouHeaderPreview() = ElementPreview {
    BipeYouHeader(matrixUser = aMatrixUser(displayName = "Ana Souza"), handle = "ana.souza", onClick = {})
}
