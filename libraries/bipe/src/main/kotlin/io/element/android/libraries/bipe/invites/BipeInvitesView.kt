/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeInvite
import io.element.android.libraries.bipe.api.BipeInviteStatus
import io.element.android.libraries.ui.strings.CommonStrings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Port of BipeInvitesScreen on iOS: personal invites, their status and the share link. */
@Composable
fun BipeInvitesView(
    state: BipeInvitesState,
    onBackClick: () -> Unit,
    onUserClick: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        title = stringResource(R.string.proseio_invites_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        ListItem(
            content = { Text(stringResource(R.string.proseio_invites_available, state.available.toString())) },
        )
        ListItem(
            content = { Text(stringResource(R.string.proseio_invites_create)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
            enabled = state.canCreate,
            onClick = { state.eventSink(BipeInvitesEvent.Create) },
        )
        ListSupportingText(text = footer(state))
        HorizontalDivider()
        if (state.invites.isEmpty() && !state.isLoading) {
            ListItem(content = { Text(stringResource(R.string.proseio_invites_empty)) })
        }
        for (invite in state.invites) {
            InviteRow(invite = invite, state = state, onUserClick = onUserClick)
        }
    }
    AsyncActionView(
        async = state.action,
        onSuccess = {},
        errorMessage = { it.message.orEmpty() },
        onErrorDismiss = { state.eventSink(BipeInvitesEvent.ClearError) },
    )
}

@Composable
private fun footer(state: BipeInvitesState): String {
    if (state.isLoading || state.available > 0) return stringResource(R.string.proseio_invites_explainer)
    val noneLeft = stringResource(R.string.proseio_invites_none_left)
    val next = state.nextAvailableAt ?: return noneLeft
    return "$noneLeft ${stringResource(R.string.proseio_invites_next, formatDate(next, withTime = true))}"
}

@Composable
private fun InviteRow(
    invite: BipeInvite,
    state: BipeInvitesState,
    onUserClick: (String) -> Unit,
) {
    val statusTitle = stringResource(invite.status.titleRes)
    when (invite.status) {
        BipeInviteStatus.PENDING -> Column {
            ListItem(
                content = { Text(statusTitle) },
                supportingContent = { Text(stringResource(R.string.proseio_invites_expires, formatDate(invite.expiresAt, withTime = false))) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Time())),
            )
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.shareUrls[invite.id]?.let { url ->
                    val context = LocalContext.current
                    val message = stringResource(R.string.proseio_invites_share_message, url)
                    OutlinedButton(
                        text = stringResource(CommonStrings.action_share),
                        size = ButtonSize.Medium,
                        leadingIcon = IconSource.Vector(CompoundIcons.ShareAndroid()),
                        onClick = { context.startSharePlainTextIntent(activityResultLauncher = null, chooserTitle = null, text = message) },
                    )
                }
                TextButton(
                    text = stringResource(R.string.proseio_invites_revoke),
                    size = ButtonSize.Medium,
                    destructive = true,
                    onClick = { state.eventSink(BipeInvitesEvent.Revoke(invite.id)) },
                )
            }
        }
        BipeInviteStatus.USED -> invite.joinedUserId?.let { userId ->
            ListItem(
                content = { Text(state.joinedUserTitles[userId] ?: userId) },
                supportingContent = { Text(statusTitle) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.CheckCircle())),
                onClick = { onUserClick(userId) },
            )
        }
        BipeInviteStatus.EXPIRED, BipeInviteStatus.REVOKED -> ListItem(
            content = { Text(statusTitle) },
            supportingContent = { Text(formatDate(invite.createdAt, withTime = false)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Close())),
        )
    }
}

private val BipeInviteStatus.titleRes: Int
    get() = when (this) {
        BipeInviteStatus.PENDING -> R.string.proseio_invites_status_pending
        BipeInviteStatus.USED -> R.string.proseio_invites_status_used
        BipeInviteStatus.EXPIRED -> R.string.proseio_invites_status_expired
        BipeInviteStatus.REVOKED -> R.string.proseio_invites_status_revoked
    }

private fun formatDate(instant: Instant, withTime: Boolean): String {
    val formatter = if (withTime) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    return formatter.withZone(ZoneId.systemDefault()).format(instant)
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
            eventSink = {},
        ),
        onBackClick = {},
        onUserClick = {},
    )
}
