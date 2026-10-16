/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import dev.zacsweers.metro.Inject
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeCreatedInvite
import io.element.android.libraries.bipe.api.BipeMyInvites
import io.element.android.libraries.bipe.di.BipeSession
import timber.log.Timber

/** Loads and creates personal invites for the session user. The invites screen and "Convidar amigos" both use it. */
@Inject
class BipeInviteCreator(
    private val bipeSession: BipeSession,
    private val linkStore: BipeInviteLinkStore,
) {
    @Throws(BipeApiException::class)
    suspend fun myInvites(): BipeMyInvites = withTokenRetry { token -> bipeSession.apiClient.myInvites(token) }

    /** Creates an invite and keeps its link on this device, because the server keeps only a hash of the code. */
    @Throws(BipeApiException::class)
    suspend fun create(): BipeCreatedInvite {
        val created = withTokenRetry { token -> bipeSession.apiClient.createInvite(token) }
        Timber.i("Bipe invite created $created")
        linkStore.saveLink(bipeSession.userId, created.id, created.url)
        return created
    }

    private suspend fun <T> withTokenRetry(request: suspend (String) -> T): T =
        BipeApiClient.withTokenRetry(bipeSession.accessToken, request)
}
