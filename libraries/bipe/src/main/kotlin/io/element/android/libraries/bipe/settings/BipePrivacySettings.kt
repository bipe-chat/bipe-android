/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.settings

import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeMessagingSetting
import io.element.android.libraries.bipe.api.BipeWhoCanMessage
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * The Bipe API settings of the Privacidade group (ADR 0112) for one session. The Você root and the Privacidade screen
 * read the same values, so a change on the screen shows at once in the root subtitle.
 */
class BipePrivacySettings(
    private val apiClient: BipeApiClient,
    private val accessToken: suspend () -> String?,
) {
    private val messagingFlow = MutableStateFlow<BipeWhoCanMessage?>(null)

    /** `null` until the first answer of `GET /v1/me/messaging`. */
    val messaging: StateFlow<BipeWhoCanMessage?> = messagingFlow.asStateFlow()

    suspend fun refreshMessaging(): Result<BipeWhoCanMessage> = runCatchingExceptions {
        val setting = BipeApiClient.withTokenRetry(accessToken) { apiClient.messaging(it) }.whoCanMessage
        messagingFlow.value = setting
        setting
    }.onFailure { Timber.w(it, "Bipe: messaging setting not loaded") }

    suspend fun setMessaging(value: BipeWhoCanMessage): Result<BipeWhoCanMessage> = runCatchingExceptions {
        val saved = BipeApiClient.withTokenRetry(accessToken) { apiClient.setMessaging(BipeMessagingSetting(value), it) }.whoCanMessage
        messagingFlow.value = saved
        saved
    }.onFailure { Timber.e(it, "Bipe: messaging setting not saved") }
}
