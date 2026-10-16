/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import timber.log.Timber

/**
 * Storage for the recovery token that syncs with the Google account (ADR 0059). The gplay build contributes
 * Block Store (`libraries/bipeblockstore`). The F-Droid build has none: the token stays on the device only.
 */
interface BipeCloudTokenStore {
    /** `null` when no token is stored or the store cannot be read. */
    suspend fun read(): String?

    /** `false` when the store refused the token. */
    suspend fun write(token: String): Boolean

    suspend fun delete()
}

@ContributesTo(AppScope::class)
interface BipeAccountBindings {
    @Multibinds(allowEmpty = true)
    fun bipeCloudTokenStores(): Set<BipeCloudTokenStore>
}

/** The recovery token: in the synced store when the build has one, else in [BipeAccountVault] (no sync). */
@SingleIn(AppScope::class)
@Inject
class BipeRecoveryTokenStore(
    cloudTokenStores: Set<BipeCloudTokenStore>,
    private val vault: BipeAccountVault,
) {
    private val cloud: BipeCloudTokenStore? = cloudTokenStores.firstOrNull()

    /** `false` in the F-Droid build: a new phone cannot get the token back. */
    val isSynced: Boolean get() = cloud != null

    suspend fun read(): String? = cloud?.read() ?: vault.read().localToken

    /** `true` when the synced store holds a token, so a new phone signed in to the same Google account can use it. */
    suspend fun hasSyncedToken(): Boolean = cloud?.read() != null

    suspend fun write(token: String) {
        val synced = cloud?.write(token) == true
        if (cloud != null && !synced) Timber.w("Bipe recovery token not synced, kept on this device only")
        vault.update { it.copy(localToken = if (synced) null else token) }
    }

    suspend fun delete() {
        cloud?.delete()
        vault.update { it.copy(localToken = null) }
    }
}
