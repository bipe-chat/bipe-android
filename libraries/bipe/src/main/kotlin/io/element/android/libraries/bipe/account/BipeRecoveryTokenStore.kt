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
 * Storage for the recovery tokens that sync with the Google account (ADR 0059), one per account (ADR 0086). The gplay build
 * contributes Block Store (`libraries/bipeblockstore`). The F-Droid build has none: the tokens stay on the device only.
 */
interface BipeCloudTokenStore {
    /**
     * Every token in the store by Matrix ID. The `null` key is the one token from before #265, whose account the store
     * does not know. `null` when the store cannot be read.
     */
    suspend fun readAll(): Map<String?, String>?

    /** `false` when the store refused the token. */
    suspend fun write(userId: String, token: String): Boolean

    /** [userId] `null` deletes the token from before #265. */
    suspend fun delete(userId: String?)
}

@ContributesTo(AppScope::class)
interface BipeAccountBindings {
    @Multibinds(allowEmpty = true)
    fun bipeCloudTokenStores(): Set<BipeCloudTokenStore>
}

/**
 * The recovery tokens by Matrix ID: in the synced store when the build has one, else in [BipeAccountVault] (no sync).
 * A `null` Matrix ID is a token from before #265 whose account is unknown; it still works for a recovery.
 */
@SingleIn(AppScope::class)
@Inject
class BipeRecoveryTokenStore(
    cloudTokenStores: Set<BipeCloudTokenStore>,
    private val vault: BipeAccountVault,
) {
    private val cloud: BipeCloudTokenStore? = cloudTokenStores.firstOrNull()

    /** `false` in the F-Droid build: a new phone cannot get the token back. */
    val isSynced: Boolean get() = cloud != null

    suspend fun readAll(): Map<String?, String> {
        migrateLegacyCloudToken()
        val data = vault.read()
        val local = buildMap<String?, String> {
            data.legacyLocalToken?.let { put(null, it) }
            data.accounts.forEach { (userId, account) -> account.localToken?.let { put(userId, it) } }
        }
        return local + cloud?.readAll().orEmpty()
    }

    /** `true` when the synced store holds the token of [userId], so a new phone signed in to the same Google account can use it. */
    suspend fun hasSyncedToken(userId: String): Boolean {
        migrateLegacyCloudToken()
        return cloud?.readAll()?.containsKey(userId) == true
    }

    /**
     * The token for a recovery on the sign-in screen, with its Matrix ID: the newest account's token first.
     * Accounts in [signedIn] are left out: they are on the phone already (ADR 0086).
     */
    suspend fun recoveryToken(signedIn: Set<String> = emptySet()): Pair<String?, String>? {
        val tokens = readAll().filterKeys { it !in signedIn }
        if (tokens.isEmpty()) return null
        // A `null` key is a real entry (the token from before #265), so `firstOrNull` cannot mean "none" here.
        val userId = vault.read().accounts.keys.reversed().firstOrNull { it in tokens } ?: tokens.keys.first()
        return userId to tokens.getValue(userId)
    }

    suspend fun write(userId: String, token: String) {
        val synced = cloud?.write(userId, token) == true
        if (cloud != null && !synced) Timber.w("Bipe recovery token not synced, kept on this device only")
        vault.update { data -> data.updatingAccount(userId) { it.copy(localToken = if (synced) null else token) } }
    }

    suspend fun delete(userId: String?) {
        cloud?.delete(userId)
        vault.update { data ->
            if (userId == null) data.copy(legacyLocalToken = null) else data.updatingAccount(userId) { it.copy(localToken = null) }
        }
    }

    /** Moves the synced token from before #265 to its account's key, once the vault knows that account. */
    private suspend fun migrateLegacyCloudToken() {
        val owner = vault.read().legacyTokenOwner ?: return
        val cloud = cloud
        if (cloud != null) {
            val tokens = cloud.readAll() ?: return
            val legacy = tokens[null]
            if (legacy != null) {
                if (owner !in tokens && !cloud.write(owner, legacy)) return
                cloud.delete(null)
                Timber.i("Bipe: moved the synced recovery token of $owner to its own key")
            }
        }
        vault.update { it.copy(legacyTokenOwner = null) }
    }
}
