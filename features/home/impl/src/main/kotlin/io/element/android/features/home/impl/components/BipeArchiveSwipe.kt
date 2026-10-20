/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.bipe.R as BipeR

/**
 * Bipe (bipe#435): swipe a chat to the left to archive it, or to bring it back in "Arquivadas". iOS: `bipeSwipeAction`.
 * The row snaps back after the swipe; the tag change then moves the chat out of the list.
 */
@Composable
internal fun BipeArchiveSwipe(
    isArchived: Boolean,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            currentOnSwipe()
            state.reset()
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElementTheme.colors.bgAccentRest)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (isArchived) BipeR.string.bipe_unarchive_action else BipeR.string.bipe_archive_action),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textOnSolidPrimary,
                )
                Icon(
                    imageVector = CompoundIcons.ExportArchive(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconOnSolidPrimary,
                )
            }
        },
        content = {
            // The row has no background of its own, so the swipe colour would show through at rest.
            Box(modifier = Modifier.background(ElementTheme.colors.bgCanvasDefault)) { content() }
        },
    )
}
