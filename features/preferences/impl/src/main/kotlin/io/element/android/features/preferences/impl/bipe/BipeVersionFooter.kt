/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.bipe

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.bipe.R as BipeR

/**
 * The version ("Versão x (n)") on the Você root and in Ajuda e sobre (ADR 0112). A long press copies the device ID,
 * which the screen never shows. [onClick] counts the taps that unlock the developer options.
 */
@Composable
internal fun BipeVersionFooter(
    version: String,
    deviceId: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val snackbarDispatcher = LocalSnackbarDispatcher.current
    Text(
        modifier = modifier
            .combinedClickable(
                onClick = onClick ?: {},
                onLongClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(null, deviceId))
                    snackbarDispatcher.post(SnackbarMessage(BipeR.string.bipe_help_device_id_copied))
                },
            )
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        textAlign = TextAlign.Center,
        text = version,
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
}
