/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.lockscreen.impl.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.lockscreen.impl.biometric.BiometricAuthenticator
import io.element.android.features.lockscreen.impl.biometric.BiometricAuthenticatorManager
import io.element.android.features.lockscreen.impl.pin.PinCodeManager
import io.element.android.features.lockscreen.impl.storage.LockScreenStore
import io.element.android.libraries.bipe.account.BipeBiometricUnlock
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import kotlinx.coroutines.launch
import io.element.android.libraries.bipe.R as BipeR

/** Bipe: the biometric unlock switch of Element's lock screen settings, shown in Sua conta (ADR 0112). */
@ContributesBinding(AppScope::class)
class DefaultBipeBiometricUnlock(
    private val pinCodeManager: PinCodeManager,
    private val lockScreenStore: LockScreenStore,
    private val biometricAuthenticatorManager: BiometricAuthenticatorManager,
) : BipeBiometricUnlock {
    @Composable
    override fun Row() {
        val hasPin by remember { pinCodeManager.hasPinCode() }.collectAsState(initial = false)
        val isEnabled by remember { lockScreenStore.isBiometricUnlockAllowed() }.collectAsState(initial = false)
        val biometricUnlock = biometricAuthenticatorManager.rememberConfirmBiometricAuthenticator()
        val scope = rememberCoroutineScope()
        if (!hasPin || !biometricAuthenticatorManager.hasAvailableAuthenticator) return
        PreferenceSwitch(
            title = stringResource(BipeR.string.bipe_account_biometric_unlock, stringResource(BipeR.string.bipe_account_biometric_word_android)),
            isChecked = isEnabled,
            onCheckedChange = { checked ->
                scope.launch {
                    if (checked) {
                        // Same as Element's lock screen settings: the key is made and confirmed before the switch turns on.
                        biometricUnlock.setup()
                        if (biometricUnlock.authenticate() == BiometricAuthenticator.AuthenticationResult.Success) {
                            lockScreenStore.setIsBiometricUnlockAllowed(true)
                        }
                    } else {
                        lockScreenStore.setIsBiometricUnlockAllowed(false)
                    }
                }
            },
        )
    }
}
