/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.invites

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipeDeviceAccount
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.StartDMResult
import io.element.android.libraries.matrix.api.room.startDM
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Bipe ADR 0034: after a personal-invite sign-up, the chat list and the chat with the inviter prompt the new user
 * to message the inviter, until the user sends a first message there. iOS: `BipeInviterGreeting`.
 */
@SingleIn(SessionScope::class)
@Inject
class BipeInviterGreeting(
    private val matrixClient: MatrixClient,
    private val bipeSession: BipeSession,
    private val deviceAccount: BipeDeviceAccount,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val stringProvider: StringProvider,
) {
    /** The inviter, by Matrix ID, with the handle (without "@") for the prompt. */
    data class Inviter(val userId: UserId, val handle: String)

    private val _inviter = MutableStateFlow<Inviter?>(null)

    /** `null` when there is no prompt: no personal invite, or the user already messaged the inviter. */
    val inviter: StateFlow<Inviter?> = _inviter.asStateFlow()

    init {
        sessionCoroutineScope.launch { load() }
    }

    /** The existing chat with the inviter, or a new one. `null` when there is no inviter or both fail. */
    suspend fun openChat(): RoomId? {
        val userId = _inviter.value?.userId ?: return null
        return when (val result = matrixClient.startDM(userId = userId, createIfDmDoesNotExist = true, isEncryped = true)) {
            is StartDMResult.Success -> result.roomId
            else -> {
                Timber.e("Bipe: could not open a chat with the inviter $userId: $result")
                null
            }
        }
    }

    /** The text the "say hi" button sends. */
    val greetingText: String get() = stringProvider.getString(R.string.bipe_inviter_greeting)

    /** Ends the prompt. Called when the user sends a message in the chat with the inviter. */
    fun end() {
        if (_inviter.value == null) return
        _inviter.value = null
        sessionCoroutineScope.launch { deviceAccount.endInviterGreeting(bipeSession.userId) }
    }

    private suspend fun load() {
        val userId = deviceAccount.inviterGreeting(bipeSession.userId)
            ?.let { runCatchingExceptions { UserId(it) }.getOrNull() }
            ?: return
        val cached = bipeSession.handleResolver.handle(userId.value)
        _inviter.value = Inviter(userId, cached ?: userId.value.removePrefix("@"))
        if (cached == null) {
            val handle = bipeSession.handleResolver.resolve(userId.value) ?: return
            if (_inviter.value?.userId == userId) _inviter.value = Inviter(userId, handle)
        }
    }
}
