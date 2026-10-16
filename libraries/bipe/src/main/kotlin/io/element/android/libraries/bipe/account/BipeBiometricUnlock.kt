/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account

import androidx.compose.runtime.Composable

/**
 * The "Desbloquear com biometria" switch of the Senha section in Sua conta (ADR 0057, ADR 0112). It turns on Element's
 * biometric unlock of the lock screen, so features/lockscreen implements it. The row is absent without an app lock PIN
 * or without biometrics on the phone.
 */
interface BipeBiometricUnlock {
    @Composable
    fun Row()
}
