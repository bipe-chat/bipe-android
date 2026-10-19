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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.account.BipeAccountCap
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.invites.BipeInviteCreator
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The "Nova conta" window. Present it only while the window shows. When the window opens, it counts the accounts on this
 * phone and, under the cap, reads the invite quota. It creates an invite only on a tap of a sign-up row, one per tap, so a
 * dismissed window never uses up an invite (bipe#445).
 */
@Inject
class BipeNewAccountPresenter(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val inviteCreator: BipeInviteCreator,
) : Presenter<BipeNewAccountState> {
    @Composable
    override fun present(): BipeNewAccountState {
        val coroutineScope = rememberCoroutineScope()
        val user by matrixClient.userProfile.collectAsState()
        val maxPersonalAccounts by BipeAccountCap.maxPersonalAccounts.collectAsState()
        var canAddPersonalAccount by remember { mutableStateOf<Boolean?>(null) }
        var quota by remember { mutableStateOf<BipeNewAccountQuota>(BipeNewAccountQuota.Loading) }
        var creatingFor by remember { mutableStateOf<BipeNewAccountKind?>(null) }
        var signup by remember { mutableStateOf<BipeNewAccountSignup?>(null) }

        LaunchedEffect(Unit) {
            // Every account on the phone counts, as the avatar menu did before this window (ADR 0086).
            val canAdd = BipeAccountCap.canAdd(sessionStore.numberOfSessions())
            canAddPersonalAccount = canAdd
            quota = if (canAdd) readQuota() else BipeNewAccountQuota.Unknown
        }

        fun handleEvent(event: BipeNewAccountEvent) {
            when (event) {
                is BipeNewAccountEvent.SignUp -> {
                    if (creatingFor != null || signup != null) return
                    creatingFor = event.kind
                    coroutineScope.launch {
                        try {
                            when (val result = inviteCreator.createIfAvailable()) {
                                is BipeInviteCreator.Result.Created -> signup = BipeNewAccountSignup(event.kind, result.invite.code)
                                // Another device used the last invite since the window opened.
                                is BipeInviteCreator.Result.NoneLeft -> quota = BipeNewAccountQuota.NoneLeft(result.nextAvailableAt)
                            }
                        } catch (error: BipeApiException) {
                            // The sign-up then shows its invite step, as before.
                            Timber.w(error, "Bipe new account: no invite")
                            signup = BipeNewAccountSignup(event.kind, inviteCode = null)
                        } finally {
                            creatingFor = null
                        }
                    }
                }
            }
        }

        return BipeNewAccountState(
            accountName = user.displayName?.takeIf { it.isNotBlank() } ?: user.userId.value,
            maxPersonalAccounts = maxPersonalAccounts,
            canAddPersonalAccount = canAddPersonalAccount,
            quota = quota,
            creatingFor = creatingFor,
            signup = signup,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun readQuota(): BipeNewAccountQuota = try {
        val myInvites = inviteCreator.myInvites()
        if (myInvites.available > 0) BipeNewAccountQuota.Available else BipeNewAccountQuota.NoneLeft(myInvites.nextAvailableAt)
    } catch (error: BipeApiException) {
        // The row stays on; the tap tries to create the invite.
        Timber.w(error, "Bipe new account: no quota")
        BipeNewAccountQuota.Unknown
    }
}
