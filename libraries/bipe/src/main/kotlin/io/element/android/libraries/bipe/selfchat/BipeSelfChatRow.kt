/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.selfchat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.bipe.R

/** Você tab row: "Anotações", the self-chat (ADR 0053). It is the first row of the tab. */
@Composable
fun BipeSelfChatRow(onClick: () -> Unit) {
    ListItem(
        content = { Text(stringResource(R.string.bipe_self_chat_title)) },
        supportingContent = { Text(stringResource(R.string.bipe_self_chat_subtitle)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Edit())),
        onClick = onClick,
    )
}

@PreviewsDayNight
@Composable
internal fun BipeSelfChatRowPreview() = ElementPreview {
    BipeSelfChatRow(onClick = {})
}
