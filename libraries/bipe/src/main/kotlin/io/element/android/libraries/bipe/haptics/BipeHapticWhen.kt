/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.element.android.libraries.bipe.generated.BipeHaptic

/**
 * Plays [haptic] once each time [condition] turns true, for a result that a state shows
 * (a PIN saved, the app unlocked, a warning screen). Never on a plain recomposition.
 */
@Composable
fun BipeHapticWhen(haptic: BipeHaptic, condition: Boolean) {
    val haptics = rememberBipeHaptics()
    LaunchedEffect(condition) {
        if (condition) haptics(haptic)
    }
}
