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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import io.element.android.libraries.bipe.generated.BipeHaptic

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
