/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.ui

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
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.proseio.R
import io.element.android.libraries.proseio.ScamWarningKind

/** Proseio: caution card under an incoming message that looks like a common scam pattern (trust-ui.md, caution level). */
@Composable
fun ProseioScamWarningView(
    kind: ScamWarningKind,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgTrustCautionSubtle, shape)
            .border(1.dp, ElementTheme.colors.borderTrustCaution, shape)
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.Warning(),
            contentDescription = null,
            tint = ElementTheme.colors.iconTrustCaution,
            modifier = Modifier.size(16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.proseio_scam_warning_title),
                style = ElementTheme.typography.fontBodySmMedium,
                color = ElementTheme.colors.textTrustCaution,
            )
            Text(
                text = stringResource(kind.messageRes),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textTrustCaution,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ProseioScamWarningViewPreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProseioScamWarningView(kind = ScamWarningKind.PIX_KEY)
        ProseioScamWarningView(kind = ScamWarningKind.NEW_NUMBER)
    }
}
