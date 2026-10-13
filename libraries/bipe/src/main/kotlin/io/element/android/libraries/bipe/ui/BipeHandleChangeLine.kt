/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * The timeline line for a handle change (ADR 0066). The string follows the fields: old and new is a change, only new a
 * first set, only old a removal. [name] is the person's display name in the room. iOS: `BipeHandleChangeLine`.
 */
@Composable
fun BipeHandleChangeLine(
    name: String,
    oldHandle: String?,
    newHandle: String?,
    modifier: Modifier = Modifier,
) {
    val text = when {
        oldHandle != null && newHandle != null -> stringResource(R.string.bipe_handle_changed, name, newHandle, oldHandle)
        newHandle != null -> stringResource(R.string.bipe_handle_set, name, newHandle)
        oldHandle != null -> stringResource(R.string.bipe_handle_removed, name, oldHandle)
        else -> return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun BipeHandleChangeLinePreview() = ElementPreview {
    BipeHandleChangeLine(name = "Ana", oldHandle = "ana", newHandle = "ana.silva")
}
