/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.profile

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.BipeProfileLink
import io.element.android.libraries.bipe.BipeProfileProblem
import io.element.android.libraries.bipe.api.BipeMyProfile

data class BipePublicProfileState(
    val isLoading: Boolean,
    /** The profile the server holds now. `null` until it is loaded. */
    val saved: BipeMyProfile?,
    /** The form: what the user set. */
    val enabled: Boolean,
    val name: String,
    val bio: String,
    /** Local check or server answer for the form. */
    val problem: BipeProfileProblem?,
    /** Save request. Failure carries the localised message. */
    val saveAction: AsyncAction<Unit>,
    /** Photo upload or removal. Failure carries the localised message. */
    val photoAction: AsyncAction<Unit>,
    val eventSink: (BipePublicProfileEvent) -> Unit,
) {
    /** Without a handle there is no profile (ADR 0041). The toggle stays off. */
    val needsHandle: Boolean get() = saved != null && saved.handle == null

    val hasChanges: Boolean
        get() = saved != null && (enabled != saved.enabled || name != saved.name || bio != saved.bio)

    val canSave: Boolean
        get() = !isLoading &&
            hasChanges &&
            problem == null &&
            !(enabled && needsHandle) &&
            saveAction !is AsyncAction.Loading &&
            photoAction !is AsyncAction.Loading

    /** `https://proseio.com.br/@handle`, only while the saved profile is on. */
    val shareUrl: String? get() = saved?.takeIf { it.enabled }?.handle?.let(BipeProfileLink::url)
}

sealed interface BipePublicProfileEvent {
    data class SetEnabled(val enabled: Boolean) : BipePublicProfileEvent
    data class UpdateName(val name: String) : BipePublicProfileEvent
    data class UpdateBio(val bio: String) : BipePublicProfileEvent
    data object Save : BipePublicProfileEvent
    data object ChangePhoto : BipePublicProfileEvent
    data object RemovePhoto : BipePublicProfileEvent
    data object ClearError : BipePublicProfileEvent
}
