/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import dev.zacsweers.metro.Inject
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeCreatedInvite
import io.element.android.libraries.bipe.di.BipeSession
import java.time.Instant

/**
 * Creates personal invites and keeps each link on this device. The one place that creates an invite: "Convidar pessoas" and
 * the "Nova conta" window share it. The iOS counterpart is `BipeInviteCreator` in BipeKit.
 */
@Inject
class BipeInviteCreator(
    private val bipeSession: BipeSession,
    private val linkStore: BipeInviteLinkStore,
) {
    sealed interface Result {
        data class Created(val invite: BipeCreatedInvite) : Result

        data class NoneLeft(val nextAvailableAt: Instant?) : Result
    }

    /** Creates an invite and stores its link. The caller checks the quota first. Throws [io.element.android.libraries.bipe.api.BipeApiException]. */
    suspend fun create(): BipeCreatedInvite {
        val created = BipeApiClient.withTokenRetry(bipeSession.accessToken) { token -> bipeSession.apiClient.createInvite(token) }
        linkStore.saveLink(bipeSession.userId, created.id, created.url)
        return created
    }

    /** Reads the quota, then creates an invite only when one is left. For screens that do not show the quota. */
    suspend fun createIfAvailable(): Result {
        val myInvites = BipeApiClient.withTokenRetry(bipeSession.accessToken) { token -> bipeSession.apiClient.myInvites(token) }
        if (myInvites.available <= 0) return Result.NoneLeft(myInvites.nextAvailableAt)
        return Result.Created(create())
    }
}
