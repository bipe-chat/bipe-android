/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.runtime.Immutable

/** Groups MVP (bipe#98): what a member missed since the last visit. iOS: `BipeMissedSummary`. */
@Immutable
data class BipeMissedSummary(
    val lines: List<Line>,
    val otherCount: Int,
) {
    @Immutable
    data class Line(
        val kind: Kind,
        val eventId: String,
        val senderName: String,
        val text: String,
    )

    enum class Kind { MENTION, REPLY, POLL }

    companion object {
        /** Below this many unread messages the block stays hidden: a short gap reads fine without it. */
        const val MINIMUM_UNREAD_COUNT = 20
        const val MAXIMUM_LINES = 4
    }
}
