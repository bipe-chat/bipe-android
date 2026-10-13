/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.bipe.BipePublicProfiles

/** The session's public profiles. `null` outside a signed-in session (and in previews): views show no bio. */
val LocalBipePublicProfiles = staticCompositionLocalOf<BipePublicProfiles?> { null }

/**
 * The bio of a user's public profile (ADR 0041), under the name in a user profile header.
 * Shows nothing when the user has no handle, no published profile or an empty bio.
 * No badge comes from the profile here.
 */
@Composable
fun BipePublicBio(userId: String, modifier: Modifier = Modifier) {
    val handle = bipeHandle(userId) ?: return
    val publicProfiles = LocalBipePublicProfiles.current ?: return
    val profiles by publicProfiles.profiles.collectAsState()
    val bio = remember(handle, profiles) { publicProfiles.profile(handle)?.bio }?.takeIf { it.isNotBlank() } ?: return
    Text(
        modifier = modifier,
        text = bio,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(12.dp))
}
