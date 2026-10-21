/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
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
import io.element.android.libraries.bipe.haptics.rememberBipeHaptics
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import kotlinx.coroutines.launch

private sealed interface AppLockDialog {
    data class CurrentPin(val error: String?) : AppLockDialog
    data object Failed : AppLockDialog
}

/**
 * "Bloquear o app" in the Senha section of Sua conta. The app lock is off by default. The switch shows whether the lock
 * PIN is set.
 * - On: [onTurnOn] opens Element's lock screen setup (new PIN, confirm, then the biometric offer). With one account the
 *   new lock PIN also becomes the account PIN (ADR 0086), so the owner is checked first, the same as before a PIN change.
 *   If the user leaves the setup, no PIN is set and the switch stays off.
 * - Off: the owner is checked (biometrics or the phone's screen lock, else the app lock PIN), then the PIN and the
 *   biometric unlock are removed. On a failed or cancelled check the switch stays on.
 * Nothing that can prove the owner: refused (fail closed).
 */
@Composable
fun BipeAppLockRow(
    account: BipeAccountSession,
    title: String,
    onTurnOn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOn by remember { account.appLockOnFlow() }.collectAsState(initial = false)
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<AppLockDialog?>(null) }
    val haptics = rememberBipeHaptics()
    val onReason = stringResource(R.string.bipe_pin_change_biometric_reason)
    val offReason = stringResource(R.string.bipe_account_app_lock_off_reason)
    val mismatchError = stringResource(R.string.bipe_pin_mismatch)

    fun turnOn() {
        busy = true
        scope.launch {
            when {
                // More than one account: the lock PIN belongs to the phone and no account PIN changes (ADR 0086).
                account.hasSeparateDevicePin() -> onTurnOn()
                activity != null && BipeBiometricCheck.isAvailable(context) -> {
                    if (BipeBiometricCheck.authenticate(activity, title, onReason)) onTurnOn()
                }
                // No biometrics and no screen lock: nothing can prove the owner before the account PIN changes. Fail closed.
                else -> dialog = AppLockDialog.Failed
            }
            busy = false
        }
    }

    suspend fun turnOff() {
        account.turnOffAppLock().onFailure { dialog = AppLockDialog.Failed }
    }

    fun startTurnOff() {
        busy = true
        scope.launch {
            when {
                activity != null && BipeBiometricCheck.isAvailable(context) -> {
                    if (BipeBiometricCheck.authenticate(activity, title, offReason)) turnOff()
                }
                account.hasAppLockPin() -> dialog = AppLockDialog.CurrentPin(error = null)
                // Nothing can prove the owner. Fail closed.
                else -> dialog = AppLockDialog.Failed
            }
            busy = false
        }
    }

    PreferenceSwitch(
        modifier = modifier,
        title = title,
        isChecked = isOn,
        enabled = !busy,
        onCheckedChange = { checked -> if (checked) turnOn() else startTurnOff() },
    )

    when (val shown = dialog) {
        null -> Unit
        is AppLockDialog.CurrentPin -> BipeEnterPinDialog(
            title = stringResource(R.string.bipe_pin_enter_title),
            subtitle = offReason,
            error = shown.error,
            onSubmit = { pin ->
                scope.launch {
                    if (account.verifyAppLockPin(pin)) {
                        dialog = null
                        busy = true
                        turnOff()
                        busy = false
                    } else {
                        haptics(BipeHaptic.Error) // pin_wrong
                        dialog = AppLockDialog.CurrentPin(mismatchError)
                    }
                }
            },
            onDismiss = { dialog = null },
        )
        AppLockDialog.Failed -> ErrorDialog(content = stringResource(R.string.bipe_error_generic), onSubmit = { dialog = null })
    }
}
