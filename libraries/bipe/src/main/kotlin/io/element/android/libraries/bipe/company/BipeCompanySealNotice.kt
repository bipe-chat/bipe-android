/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.company

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeBadgeGoneReason
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

/** The caution row in a DM when the other user's company seal ended or is suspended. Shows nothing in the other states. */
@Composable
fun BipeCompanySealNotice(userId: String, modifier: Modifier = Modifier) {
    BipeCompanySealNotice(state = bipeCompanySealState(userId), modifier = modifier)
}

@Composable
private fun BipeCompanySealNotice(state: BipeCompanySealState, modifier: Modifier = Modifier) {
    val text = when (state) {
        is BipeCompanySealState.Revoked -> when (state.reason) {
            BipeBadgeGoneReason.NOT_ACTIVE, BipeBadgeGoneReason.NOT_FOUND -> R.string.bipe_badge_company_revoked_not_active
            BipeBadgeGoneReason.REVOKED, BipeBadgeGoneReason.STAFF -> R.string.bipe_badge_company_revoked
        }
        BipeCompanySealState.Suspended -> R.string.bipe_badge_company_suspended
        BipeCompanySealState.None, is BipeCompanySealState.Verified -> return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgTrustCautionSubtle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.Warning(),
            contentDescription = null,
            tint = ElementTheme.colors.iconTrustCaution,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(text),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun BipeCompanySealNoticePreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BipeCompanySealNotice(state = BipeCompanySealState.Revoked(BipeBadgeGoneReason.REVOKED))
        BipeCompanySealNotice(state = BipeCompanySealState.Revoked(BipeBadgeGoneReason.NOT_ACTIVE))
        BipeCompanySealNotice(state = BipeCompanySealState.Suspended)
    }
}
