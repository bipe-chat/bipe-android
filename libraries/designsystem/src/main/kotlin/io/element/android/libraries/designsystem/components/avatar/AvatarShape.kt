/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

@Composable
fun AvatarType.User.avatarShape() = CircleShape

@Composable
fun AvatarType.Room.avatarShape() = CircleShape

@Composable
fun AvatarType.Space.avatarShape(avatarSize: Dp) = RoundedCornerShape(avatarSize * 0.25f)

/** Bipe (ADR 0132): which user IDs are company accounts. The session provides it; previews and tests see none. */
interface BipeCompanyAccounts {
    /** True for a company account, verified or not. Unknown or a failed lookup gives false (a circle). */
    @Composable
    fun isCompany(userId: String): Boolean
}

private object NoBipeCompanyAccounts : BipeCompanyAccounts {
    @Composable
    override fun isCompany(userId: String) = false
}

val LocalBipeCompanyAccounts = staticCompositionLocalOf<BipeCompanyAccounts> { NoBipeCompanyAccounts }

/** Bipe (ADR 0132): a company account's avatar is a rounded square like a space, a person's is a circle. */
@Composable
fun bipeUserAvatarShape(userId: String, avatarSize: Dp): Shape =
    if (LocalBipeCompanyAccounts.current.isCompany(userId)) AvatarType.Space().avatarShape(avatarSize) else AvatarType.User.avatarShape()

@Composable
fun AvatarType.avatarShape(avatarSize: Dp): Shape {
    return when (this) {
        is AvatarType.Space -> avatarShape(avatarSize)
        is AvatarType.Room -> avatarShape()
        is AvatarType.User -> avatarShape()
    }
}
