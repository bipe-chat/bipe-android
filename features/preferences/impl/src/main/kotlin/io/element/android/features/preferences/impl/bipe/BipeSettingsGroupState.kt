/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.bipe

import io.element.android.features.preferences.impl.root.ThemeOption
import io.element.android.libraries.bipe.api.BipeWhoCanMessage
import io.element.android.libraries.bipe.generated.BipeSettingsGroup
import io.element.android.libraries.bipe.settings.BipeWayBack
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/** A group screen of ADR 0112, rendered from the settings registry ([BipeSettingsGroup.sections]). */
data class BipeSettingsGroupState(
    val group: BipeSettingsGroup,
    val myUser: MatrixUser,
    /** Without "@". `null` when there is none, or while it is not loaded. */
    val handle: String?,
    val publicProfileEnabled: Boolean?,
    /** Registry flag names (`flag:<name>`) that are on in this build. */
    val enabledFlags: ImmutableSet<String>,
    val theme: ThemeOption,
    val availableThemeOptions: ImmutableList<ThemeOption>,
    val mediaPreviewValue: MediaPreviewValue,
    /** `null` while the SDK waits for the first sync. */
    val recoveryKeyEnabled: Boolean?,
    /** The make and model of this phone. */
    val deviceName: String,
    /** Sua conta (#360): the ways back, or `null` while the account state is not loaded. */
    val waysBack: ImmutableList<BipeWayBack>?,
    // Privacidade (#359). null while not loaded.
    val whoCanMessage: BipeWhoCanMessage?,
    val hideInviteAvatars: Boolean,
    val blockedUsersCount: Int,
    val phoneLinked: Boolean?,
    val discoverableByPhone: Boolean?,
    val sharePresence: Boolean,
    // Ajuda e sobre (#362)
    val version: String,
    val deviceId: String,
    val eventSink: (BipeSettingsGroupEvent) -> Unit,
    /** The phone holds more than one account, so the account PIN is not the PIN that locks the app (ADR 0086). */
    val hasSeparateDevicePin: Boolean = false,
)

sealed interface BipeSettingsGroupEvent {
    data class SetTheme(val theme: ThemeOption) : BipeSettingsGroupEvent
    data class SetMediaPreview(val value: MediaPreviewValue) : BipeSettingsGroupEvent
    data class SetHideInviteAvatars(val hide: Boolean) : BipeSettingsGroupEvent
    data class SetDiscoverableByPhone(val value: Boolean) : BipeSettingsGroupEvent
    data class SetSharePresence(val value: Boolean) : BipeSettingsGroupEvent
}
