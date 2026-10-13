/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.R as DesignSystemR

/**
 * Bipe's welcome content on the onboarding screen: the logo, the promise and three value points.
 * iOS: `BipeWelcomeView`.
 */
@Composable
fun BipeWelcomeView(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // iOS hides the logo only in compact height (landscape). Here the buttons below take part of the
        // screen, so the logo shrinks first and hides only when the space is very short.
        val showLogo = maxHeight >= 360.dp
        val logoSize = if (maxHeight >= 560.dp) 96.dp else 72.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showLogo) {
                Image(
                    painter = painterResource(id = DesignSystemR.drawable.bipe_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(logoSize)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(logoSize * 0.22f),
                            ambientColor = ElementTheme.colors.bgAccentRest,
                            spotColor = ElementTheme.colors.bgAccentRest,
                        ),
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
            Text(
                text = stringResource(R.string.bipe_onboarding_title),
                style = ElementTheme.typography.fontHeadingXlBold,
                color = ElementTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BipeWelcomePoint(CompoundIcons.Shield(), stringResource(R.string.bipe_onboarding_point_scam))
                BipeWelcomePoint(CompoundIcons.Mention(), stringResource(R.string.bipe_onboarding_point_handle))
                BipeWelcomePoint(CompoundIcons.Block(), stringResource(R.string.bipe_onboarding_point_no_ads))
            }
        }
    }
}

/** Bipe's onboarding background: a soft açaí wash from the top over the canvas colour. Replaces Element's artwork. */
@Composable
fun Modifier.bipeWelcomeBackground(): Modifier {
    val colors = ElementTheme.colors
    return this
        .background(colors.bgCanvasDefault)
        .background(
            Brush.verticalGradient(
                0f to colors.gradientSubtleStop1,
                0.12f to colors.gradientSubtleStop2,
                0.25f to colors.gradientSubtleStop3,
                0.38f to colors.gradientSubtleStop4,
                0.5f to colors.gradientSubtleStop5,
                0.65f to colors.gradientSubtleStop6,
            )
        )
}

@Composable
private fun BipeWelcomePoint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ElementTheme.colors.bgAccentSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElementTheme.colors.iconAccentPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun BipeWelcomeViewPreview() = ElementPreview {
    Box(modifier = Modifier.fillMaxSize().bipeWelcomeBackground()) {
        BipeWelcomeView()
    }
}
