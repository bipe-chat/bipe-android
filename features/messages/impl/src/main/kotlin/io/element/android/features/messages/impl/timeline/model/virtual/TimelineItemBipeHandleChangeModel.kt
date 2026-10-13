/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.timeline.model.virtual

/**
 * Bipe: a local line for a handle change (ADR 0066), e.g. "Ana agora é @ana.silva (antes era @ana)". No Matrix event:
 * the history comes from bipe-api. [name] is the member's display name in this room.
 */
data class TimelineItemBipeHandleChangeModel(
    val userId: String,
    val name: String,
    val oldHandle: String?,
    val newHandle: String?,
    val atMillis: Long,
) : TimelineItemVirtualModel {
    override val type: String = "TimelineItemBipeHandleChangeModel"
}
