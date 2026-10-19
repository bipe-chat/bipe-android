/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.haptics

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import io.element.android.libraries.bipe.generated.BipeHaptic
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

/**
 * Plays a Bipe haptic (ADR 0134, docs/design/haptics.md in the umbrella repo).
 * It uses the best constant that this API level has. The system touch feedback setting wins:
 * no FLAG_IGNORE_GLOBAL_SETTING.
 */
@SuppressLint("WrongConstant") // Every constant in the registry is a HapticFeedbackConstants value.
fun View.bipeHaptic(haptic: BipeHaptic) {
    val constant = haptic.constants.firstOrNull { it.minSdk <= Build.VERSION.SDK_INT } ?: return
    performHapticFeedback(constant.constant)
}

/** The Compose form of [bipeHaptic]: `val haptics = rememberBipeHaptics()`, then `haptics(BipeHaptic.Tick)`. */
@Composable
fun rememberBipeHaptics(): (BipeHaptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { haptic: BipeHaptic -> view.bipeHaptic(haptic) } }
}

/**
 * Swipe haptics (ADR 0134 rule 2): [BipeHaptic.Threshold] when the drag crosses [thresholdPx],
 * [BipeHaptic.Release] when the drag goes back under it. Nothing when the finger lifts:
 * the reset animation after release ([isResetting] true) plays no haptic.
 */
@Composable
fun BipeSwipeHaptics(
    offset: () -> Float,
    thresholdPx: Float,
    isResetting: () -> Boolean,
) {
    val haptics = rememberBipeHaptics()
    val latestOffset by rememberUpdatedState(offset)
    val latestIsResetting by rememberUpdatedState(isResetting)
    LaunchedEffect(thresholdPx) {
        snapshotFlow { abs(latestOffset()) > thresholdPx }
            .drop(1)
            .collect { crossed ->
                when {
                    crossed -> haptics(BipeHaptic.Threshold)
                    !latestIsResetting() -> haptics(BipeHaptic.Release)
                }
            }
    }
}

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

/** Plays [haptic] each time [value] goes down, e.g. the attempts left after a wrong PIN. */
@Composable
fun BipeHapticOnDecrease(haptic: BipeHaptic, value: Int?) = BipeHapticOnStep(haptic, value) { previous, current -> current < previous }

/** Plays [haptic] each time [value] goes up, e.g. a count of results made on this screen. */
@Composable
fun BipeHapticOnIncrease(haptic: BipeHaptic, value: Int?) = BipeHapticOnStep(haptic, value) { previous, current -> current > previous }

@Composable
private fun BipeHapticOnStep(haptic: BipeHaptic, value: Int?, plays: (previous: Int, current: Int) -> Boolean) {
    val haptics = rememberBipeHaptics()
    val latestPlays by rememberUpdatedState(plays)
    var last by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        val previous = last
        last = value
        if (previous != null && value != null && latestPlays(previous, value)) haptics(haptic)
    }
}
