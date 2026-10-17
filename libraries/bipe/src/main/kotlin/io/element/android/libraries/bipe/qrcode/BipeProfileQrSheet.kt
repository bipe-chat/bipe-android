/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.qrcode

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.bipe.BipeProfileLink
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet

/** The user's own profile link (`https://bipe.chat/@handle`) as a QR code, from the Você header (ADR 0112). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BipeProfileQrSheet(handle: String, onDismiss: () -> Unit) {
    val url = BipeProfileLink.url(handle) ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        BipeQrCodeContent(
            url = url,
            title = stringResource(R.string.bipe_profile_qr_title),
            footer = stringResource(R.string.bipe_profile_qr_footer),
            shareText = url,
            shareLabel = stringResource(R.string.bipe_profile_share),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
    }
}

/** "Compartilhar perfil": the system share sheet with the profile link. */
fun Context.shareBipeProfile(handle: String) {
    val url = BipeProfileLink.url(handle) ?: return
    startSharePlainTextIntent(activityResultLauncher = null, chooserTitle = null, text = url)
}
