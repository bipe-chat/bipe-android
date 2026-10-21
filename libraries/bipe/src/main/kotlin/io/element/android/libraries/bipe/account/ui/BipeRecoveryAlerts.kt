/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipeAccountSession
import io.element.android.libraries.bipe.api.BipeRecovery
import io.element.android.libraries.bipe.api.BipeRecoveryMethod
import io.element.android.libraries.bipe.api.BipeRecoveryPurpose
import io.element.android.libraries.bipe.api.BipeRecoveryState
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.minutes

/**
 * The alert on the old devices (ADR 0057): a recovery of this account that waits for its delay, with "Cancel, it was not me";
 * or a sign-in on another device with the synced token and the PIN. Polls `GET /v1/me/account` while the app is in the foreground.
 */
@Composable
fun BipeRecoveryAlerts(account: BipeAccountSession) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentStateFlow.collectLatest { state ->
            if (state.isAtLeast(Lifecycle.State.RESUMED)) {
                while (true) {
                    account.refresh()
                    delay(POLL_INTERVAL)
                }
            }
        }
    }
    val alerts by account.alerts.collectAsState()
    val alert = alerts.firstOrNull() ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.bipe_recovery_alert_title)

    if (alert.state == BipeRecoveryState.COMPLETED) {
        ErrorDialog(
            title = title,
            content = stringResource(R.string.bipe_recovery_alert_token),
            submitText = stringResource(R.string.bipe_recovery_alert_keep),
            onSubmit = { scope.launch { account.dismiss(alert) } },
            canDismiss = false,
        )
        return
    }
    ConfirmationDialog(
        title = title,
        content = stringResource(alert.messageRes(), formatTime(alert.readyAt)),
        submitText = stringResource(R.string.bipe_recovery_alert_cancel),
        cancelText = stringResource(R.string.bipe_recovery_alert_keep),
        destructiveSubmit = true,
        onSubmitClick = {
            scope.launch {
                // Toast, not SnackbarDispatcher (bipe#450): the alert shows over any screen, and most screens have no snackbar host.
                account.cancelRecovery(alert)
                    .onSuccess { Toast.makeText(context, R.string.bipe_recovery_alert_cancelled, Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(context, R.string.bipe_error_generic, Toast.LENGTH_LONG).show() }
            }
        },
        onCancelClick = { scope.launch { account.dismiss(alert) } },
        // A security alert: the user answers it, a tap outside does not close it.
        onDismiss = {},
    )
}

private fun BipeRecovery.messageRes(): Int = when {
    purpose == BipeRecoveryPurpose.PIN_RESET -> R.string.bipe_recovery_alert_pin_reset
    method == BipeRecoveryMethod.GUARDIAN -> R.string.bipe_recovery_alert_guardian
    method == BipeRecoveryMethod.CPF -> R.string.bipe_recovery_alert_cpf
    else -> R.string.bipe_recovery_alert_email
}

/** Date and time in the phone's zone and language, e.g. "14 de out. de 2026 15:30". */
fun formatTime(instant: Instant?): String {
    instant ?: return ""
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(instant)
}

private val POLL_INTERVAL = 1.minutes
