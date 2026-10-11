/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.handle

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.proseio.R

/** Settings row: "Nome de usuário" with the current @handle, or a prompt when there is none. */
@Composable
fun ProseioHandleRow(
    handle: String?,
    onClick: () -> Unit,
) {
    ListItem(
        content = { Text(stringResource(R.string.proseio_settings_handle)) },
        supportingContent = {
            Text(handle?.let { "@$it" } ?: stringResource(R.string.proseio_settings_handle_none))
        },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Mention())),
        onClick = onClick,
    )
}

@PreviewsDayNight
@Composable
internal fun ProseioHandleRowPreview() = ElementPreview {
    ProseioHandleRow(handle = "ana.souza", onClick = {})
}
