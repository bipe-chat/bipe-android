/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.handle

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

/** Settings row: "Nome de usuário" with the current @handle, or a prompt when there is none. */
@Composable
fun BipeHandleRow(
    handle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        content = { Text(stringResource(R.string.bipe_settings_handle)) },
        supportingContent = {
            Text(handle?.let { "@$it" } ?: stringResource(R.string.bipe_settings_handle_none))
        },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Mention())),
        onClick = onClick,
        modifier = modifier,
    )
}

@PreviewsDayNight
@Composable
internal fun BipeHandleRowPreview() = ElementPreview {
    BipeHandleRow(handle = "ana.souza", onClick = {})
}
