/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.groups

import io.element.android.libraries.matrix.api.notificationsettings.NotificationSettingsService
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import timber.log.Timber

/**
 * Groups MVP (proseio#97): groups notify only for mentions, replies and new polls. iOS: `ProseioQuietGroups`.
 *
 * Mentions in encrypted rooms need the server to push every encrypted event so the client can check them after decryption
 * (Synapse `msc4028_push_encrypted_events`). Without it, mentions-only silences an encrypted group completely, so this
 * waits until the server reports support. The poll rule in the account's push rules marks the account as done.
 */
class ProseioQuietGroups(
    private val notificationSettingsService: NotificationSettingsService,
) {
    suspend fun run() {
        val rawRules = notificationSettingsService.getRawPushRules().getOrNull()
        if (rawRules?.contains(POLL_RULE_ID) == true) return

        if (notificationSettingsService.canHomeServerPushEncryptedEventsToDevice().getOrNull() != true) {
            Timber.i("Proseio quiet groups: waiting for encrypted event push support on the server")
            return
        }

        // A user who already picked a group default keeps it.
        for (isEncrypted in listOf(true, false)) {
            val current = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = isEncrypted, isOneToOne = false).getOrNull()
            if (current == RoomNotificationMode.ALL_MESSAGES) {
                notificationSettingsService.setDefaultRoomNotificationMode(
                    isEncrypted = isEncrypted,
                    mode = RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY,
                    isDM = false,
                ).onFailure {
                    Timber.e(it, "Proseio quiet groups: setting the group default failed")
                    return
                }
            }
        }
        // A user override rule outranks the room rule that mentions-only adds. A room muted before this rule exists
        // ranks below it, so polls still notify there; rooms muted later rank above it and stay silent.
        notificationSettingsService.setNotifyOverrideRule(ruleId = POLL_RULE_ID, eventTypePattern = "*.poll.start")
            .onSuccess { Timber.i("Proseio quiet groups: applied") }
            .onFailure { Timber.e(it, "Proseio quiet groups: setting the poll rule failed") }
    }

    private companion object {
        const val POLL_RULE_ID = "br.com.proseio.poll_start"
    }
}
