/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.invites.BipeInviterGreeting
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.UserId

/** Bipe ADR 0034: opens the chat with the inviter, at the top of the chat list until the user sends them a first message. */
@Composable
fun BipeInviterRow(
    inviter: BipeInviterGreeting.Inviter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BipeInviterRow(
        inviterHandle = inviter.handle,
        avatarData = AvatarData(
            id = inviter.userId.value,
            name = inviter.displayName ?: inviter.handle,
            url = inviter.avatarUrl,
            size = AvatarSize.RoomListItem,
        ),
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * The avatar is the inviter's, at the size and inset of a chat row, so the row lines up with the chats under it (bipe#449).
 * A missing avatar shows the initials.
 */
@Composable
fun BipeInviterRow(
    inviterHandle: String,
    avatarData: AvatarData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(
            avatarData = avatarData,
            avatarType = AvatarType.User,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.bipe_inviter_card_title, inviterHandle),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.bipe_inviter_card_body),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Icon(
            imageVector = CompoundIcons.ChevronRight(),
            contentDescription = null,
            tint = ElementTheme.colors.iconTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun BipeInviterRowPreview() = ElementPreview {
    BipeInviterRow(
        inviter = BipeInviterGreeting.Inviter(userId = UserId("@mz:bipe.net.br"), handle = "mz", displayName = "MZ"),
        onClick = {},
    )
}
