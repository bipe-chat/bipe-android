/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.compound.theme

import androidx.compose.ui.graphics.Color
import io.element.android.compound.annotations.CoreColorToken
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.internal.DarkColorTokens

/**
 * The Black theme colours: [this] with a black canvas and a lighter secondary separator.
 *
 * The Bipe token generator puts the colours of [SemanticColors] in one holder class for each token group,
 * so each `copy()` stays under the JVM limit of 255 argument slots. Copy the group that holds the colour.
 */
@OptIn(CoreColorToken::class)
internal fun SemanticColors.toBlackTheme(): SemanticColors = copy(
    bg = bg.copy(bgCanvasDefault = Color.Black),
    separator = separator.copy(separatorSecondary = DarkColorTokens.colorGray400),
)
