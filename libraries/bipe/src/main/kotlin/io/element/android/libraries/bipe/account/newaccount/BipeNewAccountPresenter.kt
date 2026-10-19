/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.newaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.account.BipeAccountCap
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.invites.BipeInviteCreator
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import timber.log.Timber

/**
 * The "Nova conta" window. Present it only while the window shows: each time the window opens, it counts the accounts on this
 * phone once and, under the cap, creates at most one invite from the account on screen (bipe#445).
 */
@Inject
class BipeNewAccountPresenter(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val inviteCreator: BipeInviteCreator,
) : Presenter<BipeNewAccountState> {
    @Composable
    override fun present(): BipeNewAccountState {
        val user by matrixClient.userProfile.collectAsState()
        val maxPersonalAccounts by BipeAccountCap.maxPersonalAccounts.collectAsState()
        var canAddPersonalAccount by remember { mutableStateOf<Boolean?>(null) }
        var invite by remember { mutableStateOf<BipeNewAccountInvite>(BipeNewAccountInvite.Loading) }

        LaunchedEffect(Unit) {
            // Every account on the phone counts, as the avatar menu did before this window (ADR 0086).
            val canAdd = BipeAccountCap.canAdd(sessionStore.numberOfSessions())
            canAddPersonalAccount = canAdd
            invite = if (canAdd) createInvite() else BipeNewAccountInvite.NotCreated
        }

        return BipeNewAccountState(
            accountName = user.displayName?.takeIf { it.isNotBlank() } ?: user.userId.value,
            maxPersonalAccounts = maxPersonalAccounts,
            canAddPersonalAccount = canAddPersonalAccount,
            invite = invite,
        )
    }

    private suspend fun createInvite(): BipeNewAccountInvite = try {
        when (val result = inviteCreator.createIfAvailable()) {
            is BipeInviteCreator.Result.Created -> BipeNewAccountInvite.Created(result.invite.code)
            is BipeInviteCreator.Result.NoneLeft -> BipeNewAccountInvite.NoneLeft(result.nextAvailableAt)
        }
    } catch (error: BipeApiException) {
        // The sign-up then shows its invite step, as before.
        Timber.w(error, "Bipe new account: no invite")
        BipeNewAccountInvite.NotCreated
    }
}
