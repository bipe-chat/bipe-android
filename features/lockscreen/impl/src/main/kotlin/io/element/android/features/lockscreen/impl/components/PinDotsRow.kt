/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.lockscreen.impl.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.lockscreen.impl.pin.model.PinDigit
import io.element.android.features.lockscreen.impl.pin.model.PinEntry

/** Bipe: one small dot per digit, filled or empty. Eight 48dp boxes do not fit a phone width. */
@Composable
fun PinDotsRow(
    pinEntry: PinEntry,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = spacedBy(8.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (digit in pinEntry.digits) {
            PinDot(isFilled = digit is PinDigit.Filled)
        }
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
) {
    val backgroundColor = if (isFilled) {
        ElementTheme.colors.iconPrimary
    } else {
        ElementTheme.colors.bgSubtlePrimary
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(backgroundColor, CircleShape)
    )
}
