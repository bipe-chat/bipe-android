/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.element.android.libraries.bipe.BipeHandleResolver

/**
 * The session's handle resolver. `null` outside a signed-in session (and in previews): views keep upstream behaviour.
 * The iOS counterpart is the `bipeHandleResolver` environment value.
 */
val LocalBipeHandleResolver = staticCompositionLocalOf<BipeHandleResolver?> { null }

/**
 * "@handle" for a Bipe user ID with a known handle, otherwise [text] unchanged.
 * Safe with any text, e.g. a title that falls back to the Matrix ID. Recomposes when the handle arrives.
 */
@Composable
fun bipeDisplayText(text: String): String {
    val resolver = LocalBipeHandleResolver.current ?: return text
    val handles by resolver.handles.collectAsState()
    return remember(text, handles) { resolver.displayText(text) }
}

/** The handle (without "@") for a user ID, or `null`. Recomposes when the handle arrives. */
@Composable
fun bipeHandle(userId: String): String? {
    val resolver = LocalBipeHandleResolver.current ?: return null
    val handles by resolver.handles.collectAsState()
    return remember(userId, handles) { resolver.handle(userId) }
}

/** The Bipe profile link in place of a matrix.to user permalink, when the handle is known. */
@Composable
fun bipeShareUrl(permalink: String): String {
    val resolver = LocalBipeHandleResolver.current ?: return permalink
    val handles by resolver.handles.collectAsState()
    return remember(permalink, handles) { resolver.shareUrl(permalink) }
}
