/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.handle

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.BipeHandle
import io.element.android.libraries.bipe.BipeHandleStatus
import java.time.Instant

data class BipeHandleState(
    val isLoading: Boolean,
    /** The handle the server holds now, without "@". */
    val currentHandle: String?,
    /** Set while the 30-day cooldown runs. */
    val changeableAt: Instant?,
    /** What the user typed. */
    val handleInput: String,
    val handleStatus: BipeHandleStatus,
    /** Server error for the field (taken, invalid, cooldown), already localised. */
    val handleError: String?,
    /** Save request. Failure carries the localised message. */
    val saveAction: AsyncAction<Unit>,
    /** Removal request (ADR 0066). [AsyncAction.Confirming] while the confirmation dialog shows. Failure carries the localised message. */
    val removeAction: AsyncAction<Unit>,
    val now: Instant,
    val eventSink: (BipeHandleEvent) -> Unit,
) {
    val isInCooldown: Boolean get() = changeableAt?.isAfter(now) == true

    /** A removal is allowed at any time, also during the cooldown. */
    val canRemove: Boolean get() = !isLoading && currentHandle != null

    val canSave: Boolean
        get() {
            val handle = BipeHandle.sanitize(handleInput)
            return !isLoading &&
                !isInCooldown &&
                handle.isNotEmpty() &&
                handle != currentHandle &&
                !handleStatus.isBlocking
        }
}

sealed interface BipeHandleEvent {
    data class UpdateHandle(val input: String) : BipeHandleEvent
    data object Save : BipeHandleEvent
    data object ClearError : BipeHandleEvent
    data object AskRemove : BipeHandleEvent
    data object Remove : BipeHandleEvent
    data object CancelRemove : BipeHandleEvent
}
