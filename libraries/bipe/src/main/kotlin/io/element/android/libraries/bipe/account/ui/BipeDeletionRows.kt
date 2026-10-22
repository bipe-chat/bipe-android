/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipeAccountSession
import io.element.android.libraries.bipe.generated.BipeHaptic
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.libraries.bipe.haptics.rememberBipeHaptics
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import kotlinx.coroutines.launch

private sealed interface DeletionDialog {
    data object Pin : DeletionDialog
    data class Error(val message: String) : DeletionDialog
}

/**
 * Apagar conta, the last row of Sua conta (ADR 0140): what happens, the switch to delete all messages, then the owner
 * check, the senha and `POST /v1/me/deletion`. While the deletion waits: the due time and "Manter minha conta".
 */
@Composable
fun BipeDeletionRows(account: BipeAccountSession, title: String, modifier: Modifier = Modifier) {
    val status by account.status.collectAsState()
    LaunchedEffect(Unit) { account.refresh() }
    val context = LocalContext.current
    val snackbarDispatcher = LocalSnackbarDispatcher.current
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val haptics = rememberBipeHaptics()
    var dialog by remember { mutableStateOf<DeletionDialog?>(null) }
    var busy by remember { mutableStateOf(false) }
    var redactMessages by remember { mutableStateOf(false) }
    val current = status ?: return

    val deleteTitle = stringResource(R.string.bipe_account_delete_title)
    val genericError = stringResource(R.string.bipe_error_generic)
    fun start() {
        scope.launch {
            // The owner check comes first. No biometrics and no screen lock: nothing proves the owner. Fail closed.
            if (activity != null && BipeBiometricCheck.isAvailable(context)) {
                if (BipeBiometricCheck.authenticate(activity, deleteTitle, deleteTitle)) dialog = DeletionDialog.Pin
            } else {
                dialog = DeletionDialog.Error(genericError)
            }
        }
    }

    Column(modifier = modifier) {
        val waiting = current.deletion
        if (waiting != null) {
            ListItem(
                content = { Text(stringResource(R.string.bipe_account_delete_waiting_title)) },
                supportingContent = { Text(stringResource(R.string.bipe_account_delete_waiting_body, formatTime(waiting.dueAt))) },
                style = ListItemStyle.Destructive,
            )
            ListItem(
                content = { Text(stringResource(R.string.bipe_account_delete_cancel)) },
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        account.cancelDeletion()
                            .onSuccess {
                                haptics(BipeHaptic.Success)
                                snackbarDispatcher.post(SnackbarMessage(R.string.bipe_account_delete_cancelled))
                            }
                            .onFailure { dialog = DeletionDialog.Error(account.messageOf(it)) }
                        busy = false
                    }
                },
            )
        } else {
            ListItem(
                content = {
                    Text(
                        stringResource(
                            R.string.bipe_account_delete_body,
                            BipeRegistries.DELETION_COOLDOWN_HOURS.toString(),
                            BipeRegistries.HANDLE_RELEASE_HOLD_DAYS.toString(),
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.bipe_account_delete_groups)) },
            )
            ListItem(
                content = { Text(stringResource(R.string.bipe_account_delete_redact)) },
                supportingContent = { Text(stringResource(R.string.bipe_account_delete_redact_footer)) },
                trailingContent = ListItemContent.Switch(checked = redactMessages),
                enabled = !busy,
                onClick = { redactMessages = !redactMessages },
            )
            ListItem(
                content = { Text(title) },
                style = ListItemStyle.Destructive,
                enabled = !busy,
                onClick = ::start,
            )
        }

        when (val shown = dialog) {
            null -> Unit
            DeletionDialog.Pin -> BipeEnterPinDialog(
                title = stringResource(R.string.bipe_account_delete_pin_title),
                subtitle = null,
                error = null,
                onSubmit = { pin ->
                    dialog = null
                    busy = true
                    scope.launch {
                        account.requestDeletion(pin, redactMessages)
                            .onSuccess { haptics(BipeHaptic.Warning) }
                            .onFailure { dialog = DeletionDialog.Error(account.messageOf(it)) }
                        busy = false
                    }
                },
                onDismiss = { dialog = null },
            )
            is DeletionDialog.Error -> ErrorDialog(content = shown.message, onSubmit = { dialog = null })
        }
    }
}
