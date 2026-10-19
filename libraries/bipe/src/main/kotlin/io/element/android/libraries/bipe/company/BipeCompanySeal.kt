/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.company

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.bipe.BadgeCnpjMethod
import io.element.android.libraries.bipe.BadgeLevel
import io.element.android.libraries.bipe.BadgePayload
import io.element.android.libraries.bipe.R
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The session's company seals. `null` outside a signed-in session (and in previews): views show no seal and no notice. */
val LocalBipeCompanySeals = staticCompositionLocalOf<BipeCompanySeals?> { null }

/** The company seal state of [userId]. Recomposes when the answer arrives. */
@Composable
fun bipeCompanySealState(userId: String): BipeCompanySealState {
    val seals = LocalBipeCompanySeals.current ?: return BipeCompanySealState.None
    val states by seals.states.collectAsState()
    return remember(userId, states) { seals.seal(userId) }
}

/**
 * The verified company seal next to a name (trust-ui.md: solid seal plus building glyph). Shows only for a verified company.
 * A tap opens the details of the seal.
 */
@Composable
fun BipeCompanySeal(userId: String, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val state = bipeCompanySealState(userId) as? BipeCompanySealState.Verified ?: return
    BipeCompanySeal(payload = state.payload, modifier = modifier, size = size)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BipeCompanySeal(payload: BadgePayload, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    CompanySealMark(
        size = size,
        modifier = modifier.clickable(role = Role.Button) { showDetails = true },
    )
    if (showDetails) {
        ModalBottomSheet(onDismissRequest = { showDetails = false }, scrollable = true) {
            CompanySealDetails(payload = payload, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp))
        }
    }
}

@Composable
private fun CompanySealMark(size: Dp, modifier: Modifier = Modifier) {
    val fill = ElementTheme.colors.bgTrustVerifiedSolid
    val glyph = ElementTheme.colors.iconOnSolidPrimary
    val vector = remember(fill, glyph) { sealVector(fill, glyph) }
    Image(
        imageVector = vector,
        contentDescription = stringResource(R.string.bipe_badge_company),
        modifier = modifier.size(size),
    )
}

/** The Bipe mark shape (a rounded square with a tight top-left corner) with a building inside. Not Element's verified shield. */
private fun sealVector(fill: Color, glyph: Color): ImageVector =
    ImageVector.Builder(name = "BipeCompanySeal", defaultWidth = 48.dp, defaultHeight = 48.dp, viewportWidth = 48f, viewportHeight = 48f)
        // The path data uses a 64 viewBox with the shape from 8 to 56. Move it so the shape fills the icon.
        .group(translationX = -8f, translationY = -8f) {
            addPath(pathData = addPathNodes(SEAL_PATH), fill = SolidColor(fill))
            addPath(pathData = addPathNodes(BUILDING_PATH), fill = SolidColor(glyph), pathFillType = PathFillType.EvenOdd)
        }
        .build()

private const val SEAL_PATH = "M8 10c0-1.1.9-2 2-2h28c9.9 0 18 8.1 18 18v12c0 9.9-8.1 18-18 18H26C16.1 56 8 47.9 8 38V10Z"

/** A building with four windows and a door. Even-odd fill cuts the windows and the door out. */
private const val BUILDING_PATH = "M21 19h22v28H21Z M25 23h5v5h-5Z M34 23h5v5h-5Z M25 31h5v5h-5Z M34 31h5v5h-5Z M29 40h6v7h-6Z"

@Composable
private fun CompanySealDetails(payload: BadgePayload, modifier: Modifier = Modifier) {
    val locale: Locale = LocalConfiguration.current.locales.get(0)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompanySealMark(size = 24.dp)
            Text(
                text = stringResource(R.string.bipe_badge_company),
                style = ElementTheme.typography.fontHeadingSmMedium,
                color = ElementTheme.colors.textPrimary,
            )
        }
        payload.legalName?.let { DetailRow(R.string.bipe_badge_company_legal_name, it) }
        payload.tradeName?.takeIf { it.isNotBlank() }?.let { DetailRow(R.string.bipe_badge_company_trade_name, it) }
        payload.cnpj?.let { DetailRow(R.string.bipe_badge_company_cnpj, formatCnpj(it)) }
        payload.checkedMonth?.let { formatMonth(it, locale) }?.let { DetailRow(R.string.bipe_badge_company_checked, it) }
        DetailRow(R.string.bipe_badge_company_valid_until, formatDate(payload.exp, locale))
        Text(
            text = stringResource(R.string.bipe_badge_company_body),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun DetailRow(@StringRes label: Int, value: String) {
    Column {
        Text(
            text = stringResource(label),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
    }
}

/** 12.345.678/0001-90. An alphanumeric CNPJ uses the same groups. Any other length stays as it is. */
internal fun formatCnpj(cnpj: String): String =
    if (cnpj.length != 14) cnpj else "${cnpj.take(2)}.${cnpj.substring(2, 5)}.${cnpj.substring(5, 8)}/${cnpj.substring(8, 12)}-${cnpj.substring(12)}"

/** "YYYY-MM" as month and year in [locale], e.g. "outubro de 2026". */
private fun formatMonth(month: String, locale: Locale): String? = runCatchingExceptions {
    YearMonth.parse(month).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale))
}.getOrNull()

private fun formatDate(epochSeconds: Long, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))

@PreviewsDayNight
@Composable
internal fun BipeCompanySealPreview() = ElementPreview {
    val payload = BadgePayload(
        sub = "@lojadaana:bipe.net.br",
        lvl = BadgeLevel.CNPJ,
        name = "Doces da Ana",
        cnpj = "12345678000190",
        msk = "preview",
        legalName = "Ana Souza Comércio de Doces Ltda",
        tradeName = "Doces da Ana",
        method = BadgeCnpjMethod.ECNPJ,
        checkedMonth = "2026-09",
        iat = 1_790_000_000,
        exp = 1_797_000_000,
    )
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BipeCompanySeal(payload = payload)
            BipeCompanySeal(payload = payload, size = 24.dp)
        }
        CompanySealDetails(payload = payload)
    }
}
