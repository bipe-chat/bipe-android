/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.ui.strings.CommonStrings

/** Dialog: a new PIN, typed twice, checked with the shared rule. */
@Composable
internal fun BipeNewPinDialog(
    title: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember { BipeNewPinState() }
    ListDialog(
        title = title,
        subtitle = stringResource(R.string.bipe_pin_create_body, BipePin.LENGTH.toString()),
        submitText = stringResource(CommonStrings.action_continue),
        enabled = state.isValid,
        onSubmit = { if (state.isValid) onSubmit(state.pin) },
        onDismissRequest = onDismiss,
    ) {
        item { BipeNewPinFields(state) }
    }
}

/** Dialog: the current PIN, e.g. as the check before a PIN change when the phone has no screen lock. */
@Composable
internal fun BipeEnterPinDialog(
    title: String,
    subtitle: String?,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    ListDialog(
        title = title,
        subtitle = subtitle,
        submitText = stringResource(CommonStrings.action_continue),
        enabled = pin.length == BipePin.LENGTH,
        onSubmit = { onSubmit(pin) },
        onDismissRequest = onDismiss,
    ) {
        item {
            BipePinField(
                value = pin,
                onValueChange = { pin = it },
                label = null,
                error = error,
                hint = stringResource(R.string.bipe_pin_never_asked),
            )
        }
    }
}
