/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.element.android.libraries.proseio.ProseioHandleResolver

/**
 * The session's handle resolver. `null` outside a signed-in session (and in previews): views keep upstream behaviour.
 * The iOS counterpart is the `proseioHandleResolver` environment value.
 */
val LocalProseioHandleResolver = staticCompositionLocalOf<ProseioHandleResolver?> { null }

/**
 * "@handle" for a Proseio user ID with a known handle, otherwise [text] unchanged.
 * Safe with any text, e.g. a title that falls back to the Matrix ID. Recomposes when the handle arrives.
 */
@Composable
fun proseioDisplayText(text: String): String {
    val resolver = LocalProseioHandleResolver.current ?: return text
    val handles by resolver.handles.collectAsState()
    return remember(text, handles) { resolver.displayText(text) }
}

/** The handle (without "@") for a user ID, or `null`. Recomposes when the handle arrives. */
@Composable
fun proseioHandle(userId: String): String? {
    val resolver = LocalProseioHandleResolver.current ?: return null
    val handles by resolver.handles.collectAsState()
    return remember(userId, handles) { resolver.handle(userId) }
}

/** The Proseio profile link in place of a matrix.to user permalink, when the handle is known. */
@Composable
fun proseioShareUrl(permalink: String): String {
    val resolver = LocalProseioHandleResolver.current ?: return permalink
    val handles by resolver.handles.collectAsState()
    return remember(permalink, handles) { resolver.shareUrl(permalink) }
}
