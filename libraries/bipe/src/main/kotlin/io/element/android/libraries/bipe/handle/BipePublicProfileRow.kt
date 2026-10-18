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
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

/** Settings row: "Perfil público" with on or off, or nothing while the profile is not loaded. */
@Composable
fun BipePublicProfileRow(
    enabled: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        content = { Text(stringResource(R.string.bipe_settings_public_profile)) },
        supportingContent = enabled?.let {
            {
                Text(stringResource(if (it) R.string.bipe_public_profile_on else R.string.bipe_public_profile_off))
            }
        },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Public())),
        onClick = onClick,
        modifier = modifier,
    )
}
