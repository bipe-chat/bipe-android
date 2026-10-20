/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.model.getAvatarData
import kotlinx.collections.immutable.ImmutableList
import io.element.android.libraries.bipe.R as BipeR

/**
 * Bipe: the account menu on a long press of the Você tab (ADR 0135, bipe#446). Every account as a single choice, the
 * current one first with a check, then "Nova conta" while the cap allows one more account (ADR 0086).
 */
@Composable
internal fun BipeAccountMenuAnchor(
    accounts: ImmutableList<MatrixUser>,
    canAddAccount: Boolean,
    onAccountSwitch: (SessionId) -> Unit,
    onAddAccount: () -> Unit,
    content: @Composable (openMenu: () -> Unit) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        content { showMenu = true }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.selectableGroup(),
        ) {
            accounts.forEachIndexed { index, account ->
                val isCurrent = index == 0
                DropdownMenuItem(
                    text = { Text(account.displayName ?: account.userId.value) },
                    leadingIcon = { Avatar(avatarData = account.getAvatarData(size = AvatarSize.AccountItem), avatarType = AvatarType.User) },
                    trailingIcon = if (isCurrent) {
                        { Icon(imageVector = CompoundIcons.Check(), contentDescription = null, tint = ElementTheme.colors.iconAccentPrimary) }
                    } else {
                        null
                    },
                    onClick = {
                        showMenu = false
                        if (!isCurrent) onAccountSwitch(SessionId(account.userId.value))
                    },
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        selected = isCurrent
                    },
                )
            }
            if (canAddAccount) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(BipeR.string.bipe_account_add)) },
                    leadingIcon = { Icon(imageVector = CompoundIcons.Plus(), contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onAddAccount()
                    },
                )
            }
        }
    }
}
