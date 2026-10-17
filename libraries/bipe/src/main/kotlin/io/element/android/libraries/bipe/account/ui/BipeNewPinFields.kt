/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipePin

/** The two fields of a [BipeNewPinState]. */
@Composable
fun BipeNewPinFields(
    state: BipeNewPinState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
