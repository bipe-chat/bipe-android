/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.contacts

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.androidutils.system.openAppSettingsPage
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.invites.BipeInviteButtonsSection
import io.element.android.libraries.bipe.invites.BipeInviteSheets
import io.element.android.libraries.bipe.invites.bipeInviteFooter
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent

/**
 * Port of BipeContactsScreen on iOS: the invite buttons, people who joined with the user's invite, address-book matches, the count of
 * unused invites, then the phone discovery setting.
 */
@Composable
fun BipeContactsView(
    state: BipeContactsState,
    onBackClick: () -> Unit,
    onUserClick: (userId: String) -> Unit,
    onPendingInvitesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) state.eventSink(BipeContactsEvent.Resumed)
    }
    PreferencePage(
        title = stringResource(R.string.bipe_contacts_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        BipeInviteButtonsSection(
            canCreate = state.canCreateInvite,
            footer = bipeInviteFooter(available = state.invitesAvailable, nextAvailableAt = state.nextInviteAt),
            onInviteByLink = { state.eventSink(BipeContactsEvent.InviteByLink) },
            onInviteInPerson = { state.eventSink(BipeContactsEvent.InviteInPerson) },
        )
        PreferenceCategory {
            for (row in state.rows) {
                PersonRow(row, state.displayTexts[row.userId] ?: row.userId, onClick = { onUserClick(row.userId) })
            }
            AccessRows(state)
            if (state.pendingInviteCount > 0) {
                ListItem(
                    content = { Text(stringResource(R.string.bipe_contacts_pending_invites)) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Time())),
                    trailingContent = ListItemContent.Text(state.pendingInviteCount.toString()),
                    onClick = onPendingInvitesClick,
                )
            }
            // Without access, the allow and settings rows carry this text, so it shows before the permission request (Play user data policy).
            if (state.access == BipeContactsAccess.Granted) {
                ListSupportingText(text = stringResource(R.string.bipe_contacts_intro))
            }
        }
        PreferenceCategory {
            PreferenceSwitch(
                title = stringResource(R.string.bipe_discovery_toggle),
                icon = CompoundIcons.Search(),
                isChecked = state.discoverableByPhone,
                onCheckedChange = { state.eventSink(BipeContactsEvent.SetDiscoverable(it)) },
            )
            ListSupportingText(text = discoveryFooter(state.phoneLinked))
        }
    }
    BipeInviteSheets(
        shareSheetUrl = state.shareSheetUrl,
        qrCodeUrl = state.qrCodeUrl,
        onShareSheetOpened = { state.eventSink(BipeContactsEvent.ShareSheetOpened) },
        onDismissQrCode = { state.eventSink(BipeContactsEvent.DismissQrCode) },
    )
    AsyncActionView(
        async = state.action,
        onSuccess = {},
        errorMessage = { it.message.orEmpty() },
        onErrorDismiss = { state.eventSink(BipeContactsEvent.ClearError) },
    )
}

@Composable
private fun PersonRow(row: BipeContactsRow, displayText: String, onClick: () -> Unit) {
    // The handle shows under the contact's name; without a name, the handle is the title.
    val handle = if (row.contactName == null) null else displayText.takeIf { it != row.userId }
    val description = if (row.joinedWithInvite) {
        listOfNotNull(handle, stringResource(R.string.bipe_contacts_joined_with_invite)).joinToString(" · ")
    } else {
        handle
    }
    ListItem(
        content = { Text(row.contactName ?: displayText) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = ListItemContent.Icon(
            IconSource.Vector(if (row.joinedWithInvite) CompoundIcons.CheckCircle() else CompoundIcons.UserProfile())
        ),
        onClick = onClick,
    )
}

@Composable
private fun AccessRows(state: BipeContactsState) {
    when (state.access) {
        BipeContactsAccess.NotDetermined -> {
            val activity = LocalActivity.current
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) ?: true
                state.eventSink(BipeContactsEvent.AccessResult(granted = granted, canAskAgain = canAskAgain))
            }
            ListItem(
                content = { Text(stringResource(R.string.bipe_contacts_allow)) },
                supportingContent = { Text(stringResource(R.string.bipe_contacts_intro)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.UserProfile())),
                style = ListItemStyle.Primary,
                onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) },
            )
        }
        BipeContactsAccess.Denied -> {
            val context = LocalContext.current
            ListItem(
                content = { Text(stringResource(R.string.bipe_contacts_open_settings_android)) },
                supportingContent = { Text(stringResource(R.string.bipe_contacts_denied_android)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Settings())),
                style = ListItemStyle.Primary,
                onClick = { context.openAppSettingsPage() },
            )
        }
        BipeContactsAccess.Granted -> when {
            state.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.hasSearched && state.rows.isEmpty() -> ListItem(
                content = { Text(stringResource(R.string.bipe_contacts_none)) },
            )
        }
    }
}

@Composable
private fun discoveryFooter(phoneLinked: Boolean?): String {
    val footer = stringResource(R.string.bipe_discovery_footer)
    return when (phoneLinked) {
        false -> "$footer ${stringResource(R.string.bipe_phone_not_linked)}"
        true -> "$footer ${stringResource(R.string.bipe_phone_linked)}"
        null -> footer
    }
}

@PreviewsDayNight
@Composable
internal fun BipeContactsViewPreview() = ElementPreview {
    BipeContactsView(
        state = BipeContactsState(
            access = BipeContactsAccess.Granted,
            isLoading = false,
            hasSearched = true,
            matches = listOf(BipeContactsMatch("@b:bipe.net.br", "Bruna")),
            phoneLinked = false,
            discoverableByPhone = true,
            invitesAvailable = 3,
            nextInviteAt = null,
            joinedUserIds = listOf("@a:bipe.net.br"),
            pendingInviteCount = 2,
            displayTexts = mapOf("@a:bipe.net.br" to "@ana", "@b:bipe.net.br" to "@bruna"),
            action = AsyncAction.Uninitialized,
            shareSheetUrl = null,
            qrCodeUrl = null,
            eventSink = {},
        ),
        onBackClick = {},
        onUserClick = {},
        onPendingInvitesClick = {},
    )
}
