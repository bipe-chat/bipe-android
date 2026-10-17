/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.settings

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.api.BipeWhoCanMessage

/** "Quem pode me mandar mensagem" (ADR 0016, ADR 0112): three values, saved with `PUT /v1/me/messaging`. Port of BipeMessagingScreen on iOS. */
data class BipeMessagingState(
    /** `null` while the setting is not loaded. */
    val selected: BipeWhoCanMessage?,
    val saveAction: AsyncAction<Unit>,
    val eventSink: (BipeMessagingEvent) -> Unit,
)

sealed interface BipeMessagingEvent {
    data class Select(val value: BipeWhoCanMessage) : BipeMessagingEvent
    data object ClearError : BipeMessagingEvent
}
