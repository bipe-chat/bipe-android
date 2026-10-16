/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import android.content.Context
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.bipe.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Opens the system share sheet with the invite message and [url]. */
internal fun Context.shareBipeInvite(url: String) {
    startSharePlainTextIntent(
        activityResultLauncher = null,
        chooserTitle = null,
        text = getString(R.string.bipe_invites_share_message, url),
    )
}

/** "No invites left", with the date of the next invite when the server sends one. */
internal fun Context.bipeInvitesNoneLeftText(nextAvailableAt: Instant?): String {
    val noneLeft = getString(R.string.bipe_invites_none_left)
    val next = nextAvailableAt ?: return noneLeft
    return "$noneLeft ${getString(R.string.bipe_invites_next, formatInviteDate(next, withTime = true))}"
}

internal fun formatInviteDate(instant: Instant, withTime: Boolean): String {
    val formatter = if (withTime) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    return formatter.withZone(ZoneId.systemDefault()).format(instant)
}
