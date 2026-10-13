/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.ui.strings.CommonStrings

/** Text field for an 8-digit PIN. Keeps digits only. The PIN never goes into saved instance state. */
@Composable
fun BipePinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    error: String?,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    hint: String? = null,
) {
    TextField(
        value = value,
        onValueChange = { onValueChange(BipePin.sanitizeInput(it)) },
        label = label,
        placeholder = "•".repeat(BipePin.LENGTH),
        supportingText = error ?: hint,
        validity = if (error != null) TextFieldValidity.Invalid else TextFieldValidity.None,
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false, imeAction = imeAction),
    )
}

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

/** The two fields of a [BipeNewPinState]. */
@Composable
fun BipeNewPinFields(state: BipeNewPinState, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BipePinField(
            value = state.pin,
            onValueChange = { state.pin = it },
            label = stringResource(R.string.bipe_pin_create_title),
            error = state.problemRes?.let { stringResource(it, BipePin.LENGTH.toString()) },
            enabled = enabled,
            imeAction = ImeAction.Next,
        )
        BipePinField(
            value = state.confirmation,
            onValueChange = { state.confirmation = it },
            label = stringResource(R.string.bipe_pin_confirm_title),
            error = if (state.isMismatch) stringResource(R.string.bipe_pin_mismatch) else null,
            enabled = enabled,
            hint = stringResource(R.string.bipe_pin_never_asked),
        )
    }
}

/** Dialog: a new PIN, typed twice, checked with the shared rule. */
@Composable
fun BipeNewPinDialog(
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
fun BipeEnterPinDialog(
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
