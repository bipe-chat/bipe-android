/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.BipeTrustProfile
import io.element.android.libraries.bipe.BipeTrustProfile.ProfileItem
import io.element.android.libraries.bipe.BipeTrustProfile.Status
import io.element.android.libraries.bipe.BipeTrustProfile.StepId
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeBusinessClaim
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.Text

/** Bipe: the "Perfil de confiança" card in settings (bipe/docs/trust-levels.md). Port of BipeTrustProfileSection on iOS. */
@Composable
fun BipeTrustProfileSection(
    profile: BipeTrustProfile,
    onEditProfileClick: () -> Unit,
    onHandleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Header(profile)
        for (step in profile.steps) {
            StepRow(step, profile.isCompany)
            if (step.id == StepId.PROFILE) {
                for (item in profile.missingProfileItems) {
                    ListItem(
                        content = { Text(stringResource(item.actionRes(profile.isCompany))) },
                        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
                        onClick = when (item) {
                            ProfileItem.DISPLAY_NAME, ProfileItem.AVATAR -> onEditProfileClick
                            ProfileItem.HANDLE -> onHandleClick
                        },
                    )
                }
            }
        }
        ListSupportingText(text = stringResource(R.string.bipe_trust_footer))
    }
}

@Composable
private fun Header(profile: BipeTrustProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.bipe_trust_title),
                style = ElementTheme.typography.fontHeadingSmMedium,
                color = ElementTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.bipe_trust_progress, profile.completedCount.toString(), profile.totalCount.toString()),
                style = ElementTheme.typography.fontBodySmMedium,
                color = ElementTheme.colors.textSecondary,
            )
        }
        LinearProgressIndicator(
            progress = { profile.progress },
            color = ElementTheme.colors.iconAccentPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepRow(step: BipeTrustProfile.Step, isCompany: Boolean) {
    val (icon, tint) = when (step.status) {
        Status.DONE -> CompoundIcons.CheckCircleSolid() to ElementTheme.colors.iconSuccessPrimary
        Status.TODO -> CompoundIcons.Circle() to ElementTheme.colors.iconSecondary
        Status.COMING_SOON -> CompoundIcons.Circle() to ElementTheme.colors.iconDisabled
    }
    ListItem(
        content = { Text(stringResource(step.id.titleRes)) },
        supportingContent = { Text(stringResource(step.id.bodyRes(isCompany))) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(icon), tintColor = tint),
        trailingContent = if (step.status == Status.COMING_SOON) {
            ListItemContent.Custom {
                Text(
                    text = stringResource(R.string.bipe_trust_coming_soon),
                    style = ElementTheme.typography.fontBodyXsMedium,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier
                        .background(ElementTheme.colors.bgSubtleSecondary, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            null
        },
    )
}

private val StepId.titleRes: Int
    get() = when (this) {
        StepId.ACCOUNT -> R.string.bipe_trust_step_account
        StepId.PROFILE -> R.string.bipe_trust_step_profile
        StepId.EMAIL -> R.string.bipe_trust_step_email
        StepId.PIX -> R.string.bipe_trust_step_pix
        StepId.GOVBR -> R.string.bipe_trust_step_govbr
        StepId.CNPJ -> R.string.bipe_trust_step_cnpj
        StepId.PROOF -> R.string.bipe_trust_step_proof
        StepId.DOMAIN -> R.string.bipe_trust_step_domain
    }

private fun StepId.bodyRes(isCompany: Boolean): Int = when (this) {
    StepId.ACCOUNT -> R.string.bipe_trust_step_account_body
    StepId.PROFILE -> if (isCompany) R.string.bipe_trust_step_company_profile_body else R.string.bipe_trust_step_profile_body
    StepId.EMAIL -> R.string.bipe_trust_step_email_body
    StepId.PIX -> R.string.bipe_trust_step_pix_body
    StepId.GOVBR -> R.string.bipe_trust_step_govbr_body
    StepId.CNPJ -> R.string.bipe_trust_step_cnpj_body
    StepId.PROOF -> R.string.bipe_trust_step_proof_body
    StepId.DOMAIN -> R.string.bipe_trust_step_domain_body
}

private fun ProfileItem.actionRes(isCompany: Boolean): Int = when (this) {
    ProfileItem.DISPLAY_NAME -> if (isCompany) R.string.bipe_trust_step_profile_add_company_name else R.string.bipe_trust_step_profile_add_name
    ProfileItem.HANDLE -> R.string.bipe_trust_step_profile_add_handle
    ProfileItem.AVATAR -> if (isCompany) R.string.bipe_trust_step_profile_add_logo else R.string.bipe_trust_step_profile_add_photo
}

@PreviewsDayNight
@Composable
internal fun BipeTrustProfileSectionPreview() = ElementPreview {
    BipeTrustProfileSection(
        profile = BipeTrustProfile.create(displayName = "Ana", hasAvatar = false, handle = null),
        onEditProfileClick = {},
        onHandleClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun BipeTrustProfileSectionCompanyPreview() = ElementPreview {
    BipeTrustProfileSection(
        profile = BipeTrustProfile.create(
            displayName = "Padaria Sol",
            hasAvatar = false,
            handle = "padariasol",
            isCompany = true,
            business = BipeBusinessClaim(status = "awaiting_proof"),
        ),
        onEditProfileClick = {},
        onHandleClick = {},
    )
}
