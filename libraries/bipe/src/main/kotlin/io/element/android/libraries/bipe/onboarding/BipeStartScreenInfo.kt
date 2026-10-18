/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.onboarding

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.core.extensions.runCatchingExceptions

/** What the start screen reads before it picks its variant (bipe-chat/bipe#415, ADR 0072). Every read fails soft to null. */
interface BipeStartScreenInfo {
    /** `GET /v1/signup/config`. Null when the server does not answer: the start screen keeps the invite-first variant. */
    suspend fun isInviteRequired(): Boolean?

    /** The inviter's handle (without @) for a valid personal invite, from `GET /v1/invites/{code}`. */
    suspend fun inviterHandle(code: String): String?
}

@ContributesBinding(AppScope::class)
class DefaultBipeStartScreenInfo(
    private val apiClient: BipeApiClient,
) : BipeStartScreenInfo {
    override suspend fun isInviteRequired(): Boolean? = runCatchingExceptions { apiClient.signupConfig().inviteRequired }.getOrNull()

    override suspend fun inviterHandle(code: String): String? =
        runCatchingExceptions { apiClient.invitePreview(code) }.getOrNull()?.takeIf { it.valid }?.inviterHandle
}
