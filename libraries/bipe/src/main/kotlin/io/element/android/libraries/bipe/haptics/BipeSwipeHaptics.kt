/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import io.element.android.libraries.bipe.generated.BipeHaptic
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

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
