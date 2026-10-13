/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account

/**
 * The app lock, which is Element's lock screen with an 8-digit PIN (features/lockscreen). The app lock checks the PIN
 * on the device. The Bipe PIN and the app lock PIN are the same PIN: Bipe sets one after it sets the other.
 */
interface BipeAppLock {
    /** Sets the PIN that unlocks the app. */
    suspend fun setPin(pin: String)

    suspend fun isPinSet(): Boolean

    /** Counts a wrong PIN like the lock screen does. */
    suspend fun verifyPin(pin: String): Boolean
}
