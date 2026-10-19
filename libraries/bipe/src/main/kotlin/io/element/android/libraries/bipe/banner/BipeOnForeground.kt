/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.banner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest

/** Runs [onForeground] each time the screen comes to the foreground, the first time included. Call it from a view. */
@Composable
fun BipeOnForeground(onForeground: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnForeground by rememberUpdatedState(onForeground)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentStateFlow.collectLatest { state ->
            if (state == Lifecycle.State.STARTED) currentOnForeground()
        }
    }
}
