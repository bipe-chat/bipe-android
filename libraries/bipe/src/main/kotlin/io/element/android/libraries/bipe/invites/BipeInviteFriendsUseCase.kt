/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import android.app.Activity
import android.widget.Toast
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.deeplink.api.usecase.InviteFriendsUseCase
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Convidar amigos" in the start-chat screen and the room-list menu. Bipe needs an invite code to sign up, so it creates a
 * personal invite and shares its link, as "Convidar por link" on the invites screen does. Replaces DefaultInviteFriendsUseCase.
 */
@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
@Inject
class BipeInviteFriendsUseCase(
    private val inviteCreator: BipeInviteCreator,
    private val stringProvider: StringProvider,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : InviteFriendsUseCase {
    /** A second tap while a request runs must not create a second invite. */
    private val isRunning = AtomicBoolean(false)

    override fun execute(activity: Activity) {
        if (!isRunning.compareAndSet(false, true)) return
        sessionCoroutineScope.launch {
            try {
                when (val result = inviteCreator.createIfAvailable()) {
                    is BipeInviteCreator.Result.NoneLeft -> toast(activity, activity.bipeInvitesNoneLeftText(result.nextAvailableAt))
                    is BipeInviteCreator.Result.Created -> withContext(Dispatchers.Main) {
                        if (!activity.isFinishing && !activity.isDestroyed) activity.shareBipeInvite(result.invite.url)
                    }
                }
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe invite friends failed")
                toast(activity, error.localizedMessage(stringProvider))
            } finally {
                isRunning.set(false)
            }
        }
    }

    private suspend fun toast(activity: Activity, message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(activity.applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
