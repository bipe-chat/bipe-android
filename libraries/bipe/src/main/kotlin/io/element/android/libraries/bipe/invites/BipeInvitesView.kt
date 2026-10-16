/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeInvite
import io.element.android.libraries.bipe.generated.BipeHaptic
import io.element.android.libraries.bipe.haptics.BipeHapticOnChange
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/** Port of BipeInvitesScreen on iOS: one-tap invite, people who joined, then invites not used yet. */
@Composable
fun BipeInvitesView(
    state: BipeInvitesState,
    onBackClick: () -> Unit,
    onUserClick: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPendingInvite by remember { mutableStateOf<BipeInvite?>(null) }
    PreferencePage(
        title = stringResource(R.string.bipe_invites_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        BipeInviteButtonsSection(
            canCreate = state.canCreate,
            footer = bipeInviteFooter(available = state.available.takeUnless { state.isLoading }, nextAvailableAt = state.nextAvailableAt),
            onInviteByLink = { state.eventSink(BipeInvitesEvent.InviteByLink) },
            onInviteInPerson = { state.eventSink(BipeInvitesEvent.InviteInPerson) },
        )
        JoinedSection(state, onUserClick)
        PendingSection(state, onInviteClick = { selectedPendingInvite = it })
    }
    BipeHapticOnChange(BipeHaptic.Success, state.createdCount) { previous, current -> current > previous } // invite_created
    selectedPendingInvite?.let { invite ->
        PendingInviteActionsSheet(
            invite = invite,
            hasLink = state.shareUrls.containsKey(invite.id),
            eventSink = state.eventSink,
            onDismiss = { selectedPendingInvite = null },
        )
    }
    if (state.noteDestination != null) {
        BipeInviteNoteDialog(
            onSubmit = { state.eventSink(BipeInvitesEvent.CreateInvite(it)) },
            onDismiss = { state.eventSink(BipeInvitesEvent.DismissNote) },
        )
    }
    BipeInviteSheets(
        shareSheetUrl = state.shareSheetUrl,
        qrCodeUrl = state.qrCodeUrl,
        onShareSheetOpened = { state.eventSink(BipeInvitesEvent.ShareSheetOpened) },
        onDismissQrCode = { state.eventSink(BipeInvitesEvent.DismissQrCode) },
    )
    AsyncActionView(
        async = state.action,
        onSuccess = {},
        errorMessage = { it.message.orEmpty() },
        onErrorDismiss = { state.eventSink(BipeInvitesEvent.ClearError) },
    )
}

/** Bipe ADR 0136: the note for a new invite, pre-filled and editable, asked before the invite is made. Empty means no note. */
@Composable
internal fun BipeInviteNoteDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    TextFieldDialog(
        title = stringResource(R.string.bipe_invite_note_title),
        value = stringResource(R.string.bipe_invite_note_default),
        placeholder = stringResource(R.string.bipe_invite_note_placeholder),
        supportingText = stringResource(R.string.bipe_invite_note_footer),
        maxLines = 5,
        submitText = stringResource(CommonStrings.action_continue),
        onSubmit = onSubmit,
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun JoinedSection(state: BipeInvitesState, onUserClick: (String) -> Unit) {
    val joinedInvites = state.joinedInvites
    if (joinedInvites.isEmpty()) return
    PreferenceCategory(title = "${stringResource(R.string.bipe_invites_joined_header)} (${joinedInvites.size})") {
        val statusTitle = stringResource(R.string.bipe_invites_status_used)
        for (invite in joinedInvites) {
            val userId = invite.joinedUserId ?: continue
            ListItem(
                content = { Text(state.joinedUserTitles[userId] ?: userId) },
                supportingContent = { Text(statusTitle) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.CheckCircle())),
                onClick = { onUserClick(userId) },
            )
        }
    }
}

@Composable
private fun PendingSection(state: BipeInvitesState, onInviteClick: (BipeInvite) -> Unit) {
    val pendingInvites = state.pendingInvites
    if (pendingInvites.isEmpty()) return
    PreferenceCategory(title = stringResource(R.string.bipe_invites_pending_header)) {
        for (invite in pendingInvites) {
            val expiry = stringResource(R.string.bipe_invites_expires, formatInviteDate(invite.expiresAt, withTime = false))
            val otherDevice = stringResource(R.string.bipe_invites_other_device)
            val description = if (state.shareUrls.containsKey(invite.id)) expiry else "$expiry. $otherDevice"
            ListItem(
                content = { Text(stringResource(R.string.bipe_invites_link)) },
                supportingContent = { Text(description) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Time())),
                onClick = { onInviteClick(invite) },
            )
        }
    }
}

/** Android counterpart of the iOS confirmation dialog: Share, QR code and Cancel invite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingInviteActionsSheet(
    invite: BipeInvite,
    hasLink: Boolean,
    eventSink: (BipeInvitesEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = false) {
        // Links live only on the device that made them; the server keeps a hash of the code.
        if (hasLink) {
            ListItem(
                content = { Text(stringResource(CommonStrings.action_share)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.ShareAndroid())),
                onClick = {
                    onDismiss()
                    eventSink(BipeInvitesEvent.Share(invite.id))
                },
            )
            ListItem(
                content = { Text(stringResource(R.string.bipe_invites_qr_action)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.QrCode())),
                onClick = {
                    onDismiss()
                    eventSink(BipeInvitesEvent.ShowQrCode(invite.id))
                },
            )
        }
        ListItem(
            content = { Text(stringResource(R.string.bipe_invites_revoke)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Delete())),
            style = ListItemStyle.Destructive,
            onClick = {
                onDismiss()
                eventSink(BipeInvitesEvent.Revoke(invite.id))
            },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun BipeInvitesViewPreview() = ElementPreview {
    BipeInvitesView(
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
        onBackClick = {},
        onUserClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun BipeInviteNoteDialogPreview() = ElementPreview {
    BipeInviteNoteDialog(onSubmit = {}, onDismiss = {})
}
