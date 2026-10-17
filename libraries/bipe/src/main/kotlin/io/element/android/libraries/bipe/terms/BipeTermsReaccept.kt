/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.terms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.element.android.libraries.bipe.account.BipeAccountSession
import io.element.android.libraries.bipe.api.BipeAccountStatus
import io.element.android.libraries.bipe.generated.BipeRegistries

/**
 * The re-accept screen over the signed-in app (ADR 0113, bipe#366). It reads the status that `BipeRecoveryAlerts` polls
 * from `GET /v1/me/account`. It shows only after a successful load whose terms version is not the current one; a load
 * that fails leaves the status as it was, so a network or server error never shows it (the server decides).
 * It waits while a recovery alert is open, so the two never show at once.
 * The app lock is a separate activity, so this screen stays under it and shows after unlock.
 */
@Composable
fun BipeTermsReaccept(account: BipeAccountSession, modifier: Modifier = Modifier) {
    val status by account.status.collectAsState()
    val alerts by account.alerts.collectAsState()
    if (status?.needsTermsAcceptance() != true || alerts.isNotEmpty()) return
    val presenter = remember(account) { BipeTermsReacceptPresenter(account::acceptTerms) }
    BipeTermsReacceptView(state = presenter.present(), modifier = modifier)
}

/** No recorded version, or an older or other one than the version this build ships. */
fun BipeAccountStatus.needsTermsAcceptance(): Boolean = termsVersion != BipeRegistries.LEGAL_VERSION
