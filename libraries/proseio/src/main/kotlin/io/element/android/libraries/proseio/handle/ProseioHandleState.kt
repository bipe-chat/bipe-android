/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.handle

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.proseio.ProseioHandle
import io.element.android.libraries.proseio.ProseioHandleStatus
import java.time.Instant

data class ProseioHandleState(
    val isLoading: Boolean,
    /** The handle the server holds now, without "@". */
    val currentHandle: String?,
    /** Set while the 30-day cooldown runs. */
    val changeableAt: Instant?,
    /** What the user typed. */
    val handleInput: String,
    val handleStatus: ProseioHandleStatus,
    /** Server error for the field (taken, invalid, cooldown), already localised. */
    val handleError: String?,
    /** Save request. Failure carries the localised message. */
    val saveAction: AsyncAction<Unit>,
    val now: Instant,
    val eventSink: (ProseioHandleEvent) -> Unit,
) {
    val isInCooldown: Boolean get() = changeableAt?.isAfter(now) == true

    val canSave: Boolean
        get() {
            val handle = ProseioHandle.sanitize(handleInput)
            return !isLoading &&
                !isInCooldown &&
                handle.isNotEmpty() &&
                handle != currentHandle &&
                !handleStatus.isBlocking
        }
}

sealed interface ProseioHandleEvent {
    data class UpdateHandle(val input: String) : ProseioHandleEvent
    data object Save : ProseioHandleEvent
    data object ClearError : ProseioHandleEvent
}
