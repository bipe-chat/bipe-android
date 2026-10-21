/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.contacts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.invites.BipeInviteButtons
import io.element.android.libraries.bipe.invites.BipeInviteSheets
import io.element.android.libraries.bipe.invites.BipeInvitesState
import io.element.android.libraries.bipe.invites.bipeInvitesFooter
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * "Contatos e convites" (Privacidade > Quem pode te achar). Port of BipeContactsScreen on iOS: the invite buttons, the
 * people who joined with the user's invite, the invites not used yet and the discovery switch.
 *
 * The iOS address-book match is not here. bipe#402 (LGPD) has not authorized a contact read or a number upload on
 * Android yet (bipe#401).
 */
@Composable
fun BipeContactsView(
    state: BipeInvitesState,
    discoverableByPhone: Boolean?,
    onDiscoverableChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onUserClick: (userId: String) -> Unit,
    onPendingInvitesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        title = stringResource(R.string.bipe_contacts_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        BipeInviteButtons(state)
        ListSupportingText(text = bipeInvitesFooter(state))
        val joinedUserIds = state.joinedInvites.mapNotNull { it.joinedUserId }
        val pendingCount = state.pendingInvites.size
        if (joinedUserIds.isNotEmpty() || pendingCount > 0) {
            PreferenceCategory {
                val joinedText = stringResource(R.string.bipe_contacts_joined_with_invite)
                for (userId in joinedUserIds) {
                    ListItem(
                        content = { Text(state.joinedUserTitles[userId] ?: userId) },
                        supportingContent = { Text(joinedText) },
                        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.CheckCircle())),
                        onClick = { onUserClick(userId) },
                    )
                }
                if (pendingCount > 0) {
                    ListItem(
                        content = { Text(stringResource(R.string.bipe_contacts_pending_invites)) },
                        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Time())),
                        trailingContent = ListItemContent.Text(pendingCount.toString()),
                        onClick = onPendingInvitesClick,
                    )
                }
            }
        }
        PreferenceCategory {
            PreferenceSwitch(
                title = stringResource(R.string.bipe_discovery_toggle),
                isChecked = discoverableByPhone == true,
                enabled = discoverableByPhone != null,
                onCheckedChange = onDiscoverableChange,
            )
            ListSupportingText(text = stringResource(R.string.bipe_discovery_footer))
        }
    }
    BipeInviteSheets(state)
}

@PreviewsDayNight
@Composable
internal fun BipeContactsViewPreview() = ElementPreview {
    BipeContactsView(
        state = BipeInvitesState(
            isLoading = false,
            quota = 5,
            available = 3,
            nextAvailableAt = null,
            invites = emptyList(),
            shareUrls = emptyMap(),
            joinedUserTitles = emptyMap(),
            action = AsyncAction.Uninitialized,
            shareSheetUrl = null,
            qrCodeUrl = null,
            createdCount = 0,
            noteDestination = null,
            eventSink = {},
        ),
        discoverableByPhone = true,
        onDiscoverableChange = {},
        onBackClick = {},
        onUserClick = {},
        onPendingInvitesClick = {},
    )
}
