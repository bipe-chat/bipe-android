/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.lockscreen.impl.pin

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.lockscreen.impl.biometric.BiometricAuthenticatorManager
import io.element.android.libraries.bipe.account.BipeAppLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Bipe: the lock screen PIN. While the lock is on, it follows the account PIN (ADR 0057, ADR 0086). */
@ContributesBinding(AppScope::class)
class DefaultBipeAppLock(
    private val pinCodeManager: PinCodeManager,
    private val biometricAuthenticatorManager: BiometricAuthenticatorManager,
) : BipeAppLock {
    override suspend fun setPin(pin: String) = pinCodeManager.createPinCode(pin)

    override suspend fun isPinSet(): Boolean = pinCodeManager.hasPinCode().first()

    override fun pinSetFlow(): Flow<Boolean> = pinCodeManager.hasPinCode()

    override suspend fun verifyPin(pin: String): Boolean = pinCodeManager.verifyPinCode(pin)

    /** Same as Element's "Remove PIN" in the lock screen settings. */
    override suspend fun removePin() {
        pinCodeManager.deletePinCode()
        biometricAuthenticatorManager.disable()
    }
}
