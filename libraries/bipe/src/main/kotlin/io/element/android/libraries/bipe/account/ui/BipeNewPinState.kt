/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.element.android.libraries.bipe.account.BipePin

/** State of a "new PIN, then the same PIN again" form. */
class BipeNewPinState {
    var pin by mutableStateOf("")
    var confirmation by mutableStateOf("")

    /** Shown under the first field once it has all digits. */
    val problemRes: Int? get() = BipePin.problem(pin)?.takeIf { pin.length == BipePin.LENGTH }?.messageRes

    val isMismatch: Boolean get() = confirmation.length == BipePin.LENGTH && confirmation != pin

    val isValid: Boolean get() = BipePin.problem(pin) == null && confirmation == pin

    fun clear() {
        pin = ""
        confirmation = ""
    }
}
