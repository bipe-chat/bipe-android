/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.onboarding

import android.content.Context
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * "Escolha seu @" on the chat list (bipe-chat/bipe#200, bipe-chat/bipe#415), the same as iOS. Sign-up does not ask for the @.
 * The card shows once the server says the account has no @. A tap or "Agora não" removes it for good on this device.
 */
@SingleIn(SessionScope::class)
@Inject
class BipeHandlePrompt(
    private val bipeSession: BipeSession,
    @ApplicationContext private val context: Context,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
) {
    private val _shows = MutableStateFlow(false)

    /** True while the card is due. A failed read shows nothing. */
    val shows: StateFlow<Boolean> = _shows.asStateFlow()

    init {
        if (!isDismissed()) {
            sessionCoroutineScope.launch {
                val token = bipeSession.accessToken() ?: return@launch
                try {
                    _shows.value = bipeSession.apiClient.myHandle(token).handle == null
                } catch (error: BipeApiException) {
                    Timber.w(error, "Bipe handle prompt: handle read failed")
                }
            }
        }
    }

    fun dismiss() {
        _shows.value = false
        preferences().edit().putBoolean(key(), true).apply()
    }

    private fun isDismissed() = preferences().getBoolean(key(), false)

    private fun key() = "dismissed_${bipeSession.userId}"

    private fun preferences() = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val FILE_NAME = "bipe_handle_prompt"
    }
}
