/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.element.android.libraries.bipe.generated.BipeHaptic

/**
 * Plays [haptic] each time [value] changes and [plays] is true for the old and new value,
 * e.g. `{ old, new -> new < old }` for the attempts left after a wrong PIN.
 */
@Composable
fun BipeHapticOnChange(haptic: BipeHaptic, value: Int?, plays: (previous: Int, current: Int) -> Boolean) {
    val haptics = rememberBipeHaptics()
    val latestPlays by rememberUpdatedState(plays)
    var last by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        val previous = last
        last = value
        if (previous != null && value != null && latestPlays(previous, value)) haptics(haptic)
    }
}
