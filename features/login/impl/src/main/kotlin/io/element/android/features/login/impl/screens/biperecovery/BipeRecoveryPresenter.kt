/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.biperecovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.BipeSignupRules
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipeDeviceAccount
import io.element.android.libraries.bipe.account.BipePendingClaim
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.bipe.account.ui.BipeNewPinState
import io.element.android.libraries.bipe.api.BipeApiErrorCode
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeRecoveryPurpose
import io.element.android.libraries.bipe.api.BipeRecoveryState
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

/**
 * Getting back into a device-first account on a new phone (ADR 0057, ADR 0059). Each path asks for the PIN only after its
 * other factor: the synced token, or the e-mail link. The QR path is Element's QR sign-in (the old phone shows the code).
 */
@AssistedInject
class BipeRecoveryPresenter(
    @Assisted private val linkToken: String?,
    private val deviceAccount: BipeDeviceAccount,
    private val stringProvider: StringProvider,
) : Presenter<BipeRecoveryScreenState> {
    @AssistedFactory
    interface Factory {
        fun create(linkToken: String?): BipeRecoveryPresenter
    }

    @Composable
    override fun present(): BipeRecoveryScreenState {
        val scope = rememberCoroutineScope()
        var mode by remember { mutableStateOf(if (linkToken != null) BipeRecoveryMode.LINK_PIN else BipeRecoveryMode.CHOOSE) }
        var hasToken by remember { mutableStateOf(false) }
        var pin by remember { mutableStateOf("") }
        val newPinState = remember { BipeNewPinState() }
        var email by remember { mutableStateOf("") }
        var isEmailOpen by remember { mutableStateOf(false) }
        var isEmailSent by remember { mutableStateOf(false) }
        var pendingClaim by remember { mutableStateOf<BipePendingClaim?>(null) }
        var notice by remember { mutableStateOf<String?>(null) }
        var pinError by remember { mutableStateOf<String?>(null) }
        var action by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        LaunchedEffect(Unit) {
            hasToken = deviceAccount.hasRecoveryToken()
            if (linkToken == null) {
                deviceAccount.pendingClaim()?.let {
                    pendingClaim = it
                    mode = BipeRecoveryMode.WAITING
                }
            }
        }

        fun message(error: Throwable): String =
            (error as? BipeApiException)?.localizedMessage(stringProvider) ?: stringProvider.getString(R.string.bipe_error_generic)

        /** PIN errors stay under the PIN field; anything else is a dialog. */
        fun show(error: Throwable) {
            Timber.w(error, "Bipe recovery step failed")
            action = AsyncAction.Uninitialized
            val code = (error as? BipeApiException.Server)?.code
            when (code) {
                BipeApiErrorCode.PIN_WRONG, BipeApiErrorCode.PIN_BLOCKED, BipeApiErrorCode.PIN_WEAK -> {
                    pin = ""
                    newPinState.clear()
                    pinError = message(error)
                }
                else -> action = AsyncAction.Failure(Exception(message(error)))
            }
            if (code == BipeApiErrorCode.RECOVERY_INVALID) {
                // The token or the link no longer works: back to the choices.
                scope.launch { hasToken = deviceAccount.hasRecoveryToken() }
                if (mode != BipeRecoveryMode.WAITING) mode = BipeRecoveryMode.CHOOSE
            }
        }

        // The delay runs: poll the claim. A completed access recovery signs in, and the root node opens the app.
        LaunchedEffect(mode) {
            if (mode != BipeRecoveryMode.WAITING) return@LaunchedEffect
            while (true) {
                val result = deviceAccount.claim()
                val claim = result.getOrNull()
                val failure = result.exceptionOrNull()
                when {
                    failure != null -> {
                        if ((failure as? BipeApiException.Server)?.code == BipeApiErrorCode.RECOVERY_INVALID) {
                            notice = stringProvider.getString(R.string.bipe_error_recovery_invalid)
                            pendingClaim = null
                            mode = BipeRecoveryMode.CHOOSE
                            return@LaunchedEffect
                        }
                    }
                    claim == null -> {
                        pendingClaim = null
                        mode = BipeRecoveryMode.CHOOSE
                        return@LaunchedEffect
                    }
                    claim.state == BipeRecoveryState.COMPLETED && claim.purpose == BipeRecoveryPurpose.PIN_RESET -> {
                        notice = stringProvider.getString(R.string.bipe_recovery_pin_ready)
                        pendingClaim = null
                        mode = BipeRecoveryMode.CHOOSE
                        return@LaunchedEffect
                    }
                    claim.state == BipeRecoveryState.COMPLETED -> {
                        // Signed in. Keep this screen until the root node replaces it.
                        notice = stringProvider.getString(R.string.bipe_recovery_ready)
                        return@LaunchedEffect
                    }
                    claim.state == BipeRecoveryState.CANCELLED -> {
                        notice = stringProvider.getString(R.string.bipe_recovery_cancelled)
                        pendingClaim = null
                        mode = BipeRecoveryMode.CHOOSE
                        return@LaunchedEffect
                    }
                    claim.state == BipeRecoveryState.EXPIRED -> {
                        notice = stringProvider.getString(R.string.bipe_error_recovery_invalid)
                        pendingClaim = null
                        mode = BipeRecoveryMode.CHOOSE
                        return@LaunchedEffect
                    }
                }
                delay(POLL_INTERVAL)
            }
        }

        fun handleEvent(event: BipeRecoveryEvent) {
            when (event) {
                is BipeRecoveryEvent.UpdatePin -> {
                    pin = BipePin.sanitizeInput(event.value)
                    pinError = null
                }
                is BipeRecoveryEvent.UpdateEmail -> {
                    email = event.value
                    isEmailSent = false
                }
                BipeRecoveryEvent.SubmitTokenPin -> scope.launch {
                    action = AsyncAction.Loading
                    deviceAccount.recoverWithToken(pin)
                        .onSuccess { action = AsyncAction.Uninitialized }
                        .onFailure(::show)
                }
                BipeRecoveryEvent.OpenEmail -> isEmailOpen = true
                BipeRecoveryEvent.SendEmail -> scope.launch {
                    if (!BipeSignupRules.isPlausibleEmail(email.trim())) return@launch
                    action = AsyncAction.Loading
                    deviceAccount.requestEmailRecovery(email)
                        .onSuccess {
                            action = AsyncAction.Uninitialized
                            isEmailSent = true
                        }
                        .onFailure(::show)
                }
                BipeRecoveryEvent.SubmitLinkPin -> scope.launch {
                    val token = linkToken ?: return@launch
                    action = AsyncAction.Loading
                    deviceAccount.confirmEmailRecovery(token, pin = pin, newPin = null)
                        .onSuccess {
                            action = AsyncAction.Uninitialized
                            pin = ""
                            pendingClaim = it
                            mode = BipeRecoveryMode.WAITING
                        }
                        .onFailure(::show)
                }
                BipeRecoveryEvent.ForgotPin -> {
                    pin = ""
                    pinError = null
                    mode = if (linkToken != null) BipeRecoveryMode.LINK_NEW_PIN else mode
                    if (linkToken == null) isEmailOpen = true
                }
                BipeRecoveryEvent.SubmitNewPin -> scope.launch {
                    val token = linkToken ?: return@launch
                    if (!newPinState.isValid) return@launch
                    action = AsyncAction.Loading
                    deviceAccount.confirmEmailRecovery(token, pin = null, newPin = newPinState.pin)
                        .onSuccess {
                            action = AsyncAction.Uninitialized
                            newPinState.clear()
                            pendingClaim = it
                            mode = BipeRecoveryMode.WAITING
                        }
                        .onFailure(::show)
                }
                BipeRecoveryEvent.ClearError -> action = AsyncAction.Uninitialized
            }
        }

        return BipeRecoveryScreenState(
            mode = mode,
            hasToken = hasToken,
            pin = pin,
            newPinState = newPinState,
            email = email,
            isEmailOpen = isEmailOpen,
            isEmailSent = isEmailSent,
            pendingClaim = pendingClaim,
            notice = notice,
            pinError = pinError,
            action = action,
            eventSink = ::handleEvent,
        )
    }

    private companion object {
        val POLL_INTERVAL = 1.minutes
    }
}
