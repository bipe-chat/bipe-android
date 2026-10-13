/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.bipe.R

/** Bipe: entry to the message requests (pending DM invites) at the top of the chat list. */
@Composable
fun BipeRequestsRow(
    count: Int,
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
            imageVector = CompoundIcons.UserAdd(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(32.dp).padding(4.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.proseio_requests_title),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.proseio_requests_row_body),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Text(
            text = count.toString(),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textOnSolidPrimary,
            modifier = Modifier
                .background(ElementTheme.colors.iconAccentPrimary, CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Icon(
            imageVector = CompoundIcons.ChevronRight(),
            contentDescription = null,
            tint = ElementTheme.colors.iconTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Bipe: "Conta não verificada" card on a message request (trust-ui.md, neutral level). */
@Composable
fun BipeUnverifiedRequestCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgTrustNeutralSubtle, shape)
            .border(1.dp, ElementTheme.colors.borderTrustNeutral, shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.Help(),
            contentDescription = null,
            tint = ElementTheme.colors.iconTrustNeutral,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.proseio_banner_unverified_title),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textTrustNeutral,
            )
            Text(
                text = stringResource(R.string.proseio_request_unverified_body),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textTrustNeutral,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun BipeRequestsRowPreview() = ElementPreview {
    Column {
        BipeRequestsRow(count = 3, onClick = {})
        BipeUnverifiedRequestCard(modifier = Modifier.padding(16.dp))
    }
}
