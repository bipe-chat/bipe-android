/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.lockscreen.impl.pin

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.bipe.account.BipeAppLock
import kotlinx.coroutines.flow.first

/** Bipe: the account PIN (ADR 0057) is also the lock screen PIN. */
@ContributesBinding(AppScope::class)
class DefaultBipeAppLock(
    private val pinCodeManager: PinCodeManager,
) : BipeAppLock {
    override suspend fun setPin(pin: String) = pinCodeManager.createPinCode(pin)

    override suspend fun isPinSet(): Boolean = pinCodeManager.hasPinCode().first()

    override suspend fun verifyPin(pin: String): Boolean = pinCodeManager.verifyPinCode(pin)
}
