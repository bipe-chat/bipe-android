/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity

/** Text field for an 8-digit PIN. Keeps digits only. The PIN never goes into saved instance state. */
@Composable
fun BipePinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    error: String?,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
    )
}
