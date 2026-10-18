/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import android.widget.Toast
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipeAccountSession
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.launch

private sealed interface PinDialog {
    data object NewPin : PinDialog
    data object MoveToDeviceFirst : PinDialog
    data class CurrentPin(val error: String?) : PinDialog
    data class Error(val message: String) : PinDialog
}

/**
 * The Senha row of Sua conta (ADR 0057, ADR 0112):
 * - beta account: "Create your PIN", which also moves the account to a hidden password;
 * - blocked PIN: the blocked state; a new PIN unblocks it;
 * - else: "Change PIN" after a biometric check (or the phone's screen lock).
 */
@Composable
fun BipePinRow(account: BipeAccountSession, modifier: Modifier = Modifier) {
    val status by account.status.collectAsState()
    LaunchedEffect(Unit) { account.refresh() }
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<PinDialog?>(null) }
    var busy by remember { mutableStateOf(false) }
    val current = status ?: return

    val changeTitle = stringResource(R.string.bipe_pin_change_title)
    val changeReason = stringResource(R.string.bipe_pin_change_biometric_reason)
    val genericError = stringResource(R.string.bipe_error_generic)
    val mismatchError = stringResource(R.string.bipe_pin_mismatch)
    fun startChange() {
        if (!current.pinSet) {
            dialog = PinDialog.NewPin
            return
        }
        scope.launch {
            when {
                activity != null && BipeBiometricCheck.isAvailable(context) -> {
                    if (BipeBiometricCheck.authenticate(activity, changeTitle, changeReason)) dialog = PinDialog.NewPin
                }
                account.hasAppLockPin() -> dialog = PinDialog.CurrentPin(error = null)
                // No biometrics, no screen lock and no app lock: nothing can prove the owner. Fail closed.
                else -> dialog = PinDialog.Error(genericError)
            }
        }
    }

    Column(modifier = modifier) {
        when {
            !current.deviceFirst -> ListItem(
                content = { Text(stringResource(R.string.bipe_pin_create_title)) },
                supportingContent = { Text(stringResource(R.string.bipe_pin_create_body, BipePin.LENGTH.toString())) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Lock())),
                enabled = !busy,
                onClick = { dialog = PinDialog.MoveToDeviceFirst },
            )
            current.pinBlocked -> ListItem(
                content = { Text(stringResource(R.string.bipe_pin_blocked_title)) },
                supportingContent = { Text(stringResource(R.string.bipe_pin_blocked_body, BipeRegistries.PIN_MAX_WRONG_TRIES.toString())) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.LockOff())),
                enabled = !busy,
                onClick = ::startChange,
            )
            else -> ListItem(
                content = { Text(if (current.pinSet) changeTitle else stringResource(R.string.bipe_pin_create_title)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Lock())),
                enabled = !busy,
                onClick = ::startChange,
            )
        }

        when (val shown = dialog) {
            null -> Unit
            PinDialog.NewPin, PinDialog.MoveToDeviceFirst -> BipeNewPinDialog(
                title = if (shown == PinDialog.NewPin && current.pinSet) changeTitle else stringResource(R.string.bipe_pin_create_title),
                onSubmit = { pin ->
                    dialog = null
                    busy = true
                    scope.launch {
                        val result = if (shown == PinDialog.MoveToDeviceFirst) account.moveToDeviceFirst(pin) else account.changePin(pin)
                        busy = false
                        result
                            .onSuccess { Toast.makeText(context, R.string.bipe_pin_changed, Toast.LENGTH_SHORT).show() }
                            .onFailure { dialog = PinDialog.Error(account.messageOf(it)) }
                    }
                },
                onDismiss = { dialog = null },
            )
            is PinDialog.CurrentPin -> BipeEnterPinDialog(
                title = stringResource(R.string.bipe_pin_enter_title),
                subtitle = changeReason,
                error = shown.error,
                onSubmit = { pin ->
                    scope.launch {
                        dialog = if (account.verifyAppLockPin(pin)) PinDialog.NewPin else PinDialog.CurrentPin(mismatchError)
                    }
                },
                onDismiss = { dialog = null },
            )
            is PinDialog.Error -> ErrorDialog(content = shown.message, onSubmit = { dialog = null })
        }
    }
}

/** The session's device-first account. `null` outside a signed-in session (and in previews). */
val LocalBipeAccount = staticCompositionLocalOf<BipeAccountSession?> { null }
