/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

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
                text = stringResource(R.string.bipe_banner_unverified_title),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textTrustNeutral,
            )
            Text(
                text = stringResource(R.string.bipe_request_unverified_body),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textTrustNeutral,
            )
        }
    }
}
