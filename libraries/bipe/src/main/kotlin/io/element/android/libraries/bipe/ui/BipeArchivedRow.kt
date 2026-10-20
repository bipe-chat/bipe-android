/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Bipe (bipe#435): "Arquivadas" with the count, at the top of the chat list. iOS: `BipeArchivedRow`.
 * [isOpen] is true while the list shows the archived chats: the row then leads back to the tab.
 */
@Composable
fun BipeArchivedRow(
    count: Int,
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (isOpen) CompoundIcons.ArrowLeft() else CompoundIcons.ExportArchive(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(32.dp).padding(4.dp),
        )
        Text(
            text = stringResource(R.string.bipe_archived_title),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (!isOpen) {
            Text(
                text = count.toString(),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Icon(
                imageVector = CompoundIcons.ChevronRight(),
                contentDescription = null,
                tint = ElementTheme.colors.iconTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun BipeArchivedRowPreview() = ElementPreview {
    Column {
        BipeArchivedRow(count = 4, isOpen = false, onClick = {})
        BipeArchivedRow(count = 4, isOpen = true, onClick = {})
    }
}
