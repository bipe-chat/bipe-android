/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.qrcode.BipeQrCodeContent
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.ui.strings.CommonStrings
import java.time.Instant

/** "Convidar alguém" and "Mostrar QR code" with the quota footer. Port of BipeInviteButtonsSection on iOS; the invites and contacts screens use it. */
@Composable
internal fun BipeInviteButtonsSection(
    canCreate: Boolean,
    footer: String,
    onInviteByLink: () -> Unit,
    onInviteInPerson: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            text = stringResource(R.string.bipe_invites_invite_someone),
            leadingIcon = IconSource.Vector(CompoundIcons.ShareAndroid()),
            enabled = canCreate,
            onClick = onInviteByLink,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            text = stringResource(R.string.bipe_invites_show_qr),
            leadingIcon = IconSource.Vector(CompoundIcons.QrCode()),
            enabled = canCreate,
            onClick = onInviteInPerson,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ListSupportingText(text = footer)
}

/** The quota footer. [available] is `null` while the invites load. */
@Composable
internal fun bipeInviteFooter(available: Int?, nextAvailableAt: Instant?): String {
    val explainer = stringResource(R.string.bipe_invites_explainer)
    if (available == null) return explainer
    if (available <= 0) return bipeInvitesNoneLeftText(nextAvailableAt)
    // A large count means nothing to the person, so it shows only when few invites are left.
    if (available > LOW_INVITE_COUNT) return explainer
    return "$explainer ${stringResource(R.string.bipe_invites_available, available.toString())}"
}

private const val LOW_INVITE_COUNT = 5

/** The quota text when no invite is left, with when the next one comes back. Port of `BipeInviteQuota.noneLeft` on iOS. */
@Composable
internal fun bipeInvitesNoneLeftText(nextAvailableAt: Instant?): String = LocalContext.current.bipeInvitesNoneLeftText(nextAvailableAt)

/** Opens the share sheet once for [shareSheetUrl] and shows [qrCodeUrl] as a QR code. Port of bipeInviteSheets on iOS. */
@Composable
internal fun BipeInviteSheets(
    shareSheetUrl: String?,
    qrCodeUrl: String?,
    onShareSheetOpened: () -> Unit,
    onDismissQrCode: () -> Unit,
) {
    shareSheetUrl?.let { url ->
        val context = LocalContext.current
        LaunchedEffect(url) {
            context.shareBipeInvite(url)
            onShareSheetOpened()
        }
    }
    qrCodeUrl?.let { url ->
        InviteQrCodeSheet(
            url = url,
            shareText = stringResource(R.string.bipe_invites_share_message, url),
            onDismiss = onDismissQrCode,
        )
    }
}

/** The invite link as a QR code. Scanned with the camera, it opens sign-up with the code, or the invite page when the app is missing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteQrCodeSheet(url: String, shareText: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        BipeQrCodeContent(
            url = url,
            title = stringResource(R.string.bipe_invites_qr_title),
            footer = stringResource(R.string.bipe_invites_qr_footer),
            shareText = shareText,
            shareLabel = stringResource(CommonStrings.action_share),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
    }
}
