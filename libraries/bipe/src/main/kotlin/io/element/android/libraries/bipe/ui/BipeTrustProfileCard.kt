/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.BipeTrustProfile
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * The trust card on the Você root (ADR 0112): the full card while a live step is left to do, else one row with the progress.
 * A tap on the row opens the full card.
 */
@Composable
fun BipeTrustProfileCard(
    profile: BipeTrustProfile,
    onEditProfileClick: () -> Unit,
    onHandleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (profile.liveStepsDone && !expanded) {
        ListItem(
            modifier = modifier,
            content = { Text(stringResource(R.string.bipe_trust_title)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.CheckCircleSolid()), tintColor = ElementTheme.colors.iconSuccessPrimary),
            trailingContent = ListItemContent.Text(
                stringResource(R.string.bipe_trust_progress, profile.completedCount.toString(), profile.totalCount.toString())
            ),
            onClick = { expanded = true },
        )
    } else {
        BipeTrustProfileSection(
            profile = profile,
            onEditProfileClick = onEditProfileClick,
            onHandleClick = onHandleClick,
            modifier = modifier,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun BipeTrustProfileCardPreview() = ElementPreview {
    BipeTrustProfileCard(
        profile = BipeTrustProfile.create(displayName = "Ana", hasAvatar = true, handle = "ana.souza"),
        onEditProfileClick = {},
        onHandleClick = {},
    )
}
