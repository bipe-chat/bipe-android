/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.profile

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.proseio.ProseioProfileLink
import io.element.android.libraries.proseio.ProseioProfileProblem
import io.element.android.libraries.proseio.api.ProseioMyProfile

data class ProseioPublicProfileState(
    val isLoading: Boolean,
    /** The profile the server holds now. `null` until it is loaded. */
    val saved: ProseioMyProfile?,
    /** The form: what the user set. */
    val enabled: Boolean,
    val name: String,
    val bio: String,
    /** Local check or server answer for the form. */
    val problem: ProseioProfileProblem?,
    /** Save request. Failure carries the localised message. */
    val saveAction: AsyncAction<Unit>,
    /** Photo upload or removal. Failure carries the localised message. */
    val photoAction: AsyncAction<Unit>,
    val eventSink: (ProseioPublicProfileEvent) -> Unit,
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
    val shareUrl: String? get() = saved?.takeIf { it.enabled }?.handle?.let(ProseioProfileLink::url)
}

sealed interface ProseioPublicProfileEvent {
    data class SetEnabled(val enabled: Boolean) : ProseioPublicProfileEvent
    data class UpdateName(val name: String) : ProseioPublicProfileEvent
    data class UpdateBio(val bio: String) : ProseioPublicProfileEvent
    data object Save : ProseioPublicProfileEvent
    data object ChangePhoto : ProseioPublicProfileEvent
    data object RemovePhoto : ProseioPublicProfileEvent
    data object ClearError : ProseioPublicProfileEvent
}
