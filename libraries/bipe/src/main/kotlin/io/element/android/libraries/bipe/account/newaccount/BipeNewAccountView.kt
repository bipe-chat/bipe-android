/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.newaccount

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.invites.bipeInvitesNoneLeftText
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * The "Nova conta" window (bipe#445, bipe#446). The avatar menu and Sua conta open it.
 * [onPersonalClick] gets the invite code for the sign-up, or `null` to show the invite step.
 * [onCompanyClick] is for the company sign-up, which comes with attestation (bipe-chat/bipe#92); the row is hidden until then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BipeNewAccountView(
    state: BipeNewAccountState,
    onDismiss: () -> Unit,
    onPersonalClick: (inviteCode: String?) -> Unit,
    onExistingClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCompanyClick: (inviteCode: String?) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true, modifier = modifier) {
        Text(
            text = stringResource(R.string.bipe_account_new_title),
            style = ElementTheme.typography.fontHeadingSmMedium,
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ListItem(
            content = { Text(stringResource(R.string.bipe_account_new_personal)) },
            supportingContent = { Text(signUpBody(state, R.string.bipe_account_new_personal_body)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.UserAdd())),
            enabled = state.canSignUp,
            onClick = {
                onDismiss()
                onPersonalClick(state.inviteCode)
            },
        )
        if (state.showsCompanyAccount) {
            ListItem(
                content = { Text(stringResource(R.string.bipe_account_new_company)) },
                supportingContent = { Text(signUpBody(state, R.string.bipe_account_new_company_body)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Company())),
                enabled = state.canSignUp,
                onClick = {
                    onDismiss()
                    onCompanyClick(state.inviteCode)
                },
            )
        }
        if (state.canSignUp && state.inviteCode != null) {
            ListSupportingText(text = stringResource(R.string.bipe_account_new_invite_note, state.accountName))
        }
        ListItem(
            content = { Text(stringResource(R.string.bipe_account_new_existing)) },
            supportingContent = { Text(stringResource(R.string.bipe_account_new_existing_body)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Key())),
            onClick = {
                onDismiss()
                onExistingClick()
            },
        )
    }
}

/** The body of a sign-up row, or why the row is off: the cap of personal accounts, then the invite quota. */
@Composable
private fun signUpBody(state: BipeNewAccountState, @StringRes body: Int): String = when {
    state.canAddPersonalAccount == false -> stringResource(R.string.bipe_account_cap_reached, state.maxPersonalAccounts.toString())
    state.invite is BipeNewAccountInvite.NoneLeft -> bipeInvitesNoneLeftText(state.invite.nextAvailableAt)
    else -> stringResource(body)
}

@PreviewsDayNight
@Composable
internal fun BipeNewAccountViewPreview(@PreviewParameter(BipeNewAccountStatePreviewParam::class) state: BipeNewAccountState) = ElementPreview {
    BipeNewAccountView(
        state = state,
        onDismiss = {},
        onPersonalClick = {},
        onExistingClick = {},
    )
}
