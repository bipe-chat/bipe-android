/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R as BipeR

enum class HomeNavigationBarItem(
    @StringRes
    val labelRes: Int,
) {
    Chats(
        labelRes = BipeR.string.proseio_tab_chats // Bipe: "Conversas"
    ),
    Spaces(
        labelRes = R.string.screen_home_tab_spaces
    ),

    // Bipe: Grupos and Você (ADR 0053). Chats is Conversas. Spaces stays in the code but is not shown.
    Groups(
        labelRes = BipeR.string.proseio_tab_groups
    ),
    You(
        labelRes = BipeR.string.proseio_tab_you
    );

    @Composable
    fun icon(
        isSelected: Boolean,
    ) = when (this) {
        Chats -> if (isSelected) CompoundIcons.ChatSolid() else CompoundIcons.Chat()
        Spaces -> if (isSelected) CompoundIcons.SpaceSolid() else CompoundIcons.Space()
        Groups -> CompoundIcons.Group()
        You -> if (isSelected) CompoundIcons.UserProfileSolid() else CompoundIcons.UserProfile()
    }

    /** Bipe: the room list tabs. */
    val isBipeRoomList: Boolean
        get() = this == Chats || this == Groups

    companion object {
        /** Bipe: the tabs in the bar, in order (ADR 0053). */
        val bipeVisibleItems = listOf(Chats, Groups, You)

        fun from(index: Int): HomeNavigationBarItem {
            return entries.getOrElse(index) { Chats }
        }
    }
}
