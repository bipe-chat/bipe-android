/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.bipe

import io.element.android.features.preferences.impl.root.ThemeOption
import io.element.android.libraries.bipe.generated.BipeSettingsGroup
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
    val eventSink: (BipeSettingsGroupEvent) -> Unit,
)

sealed interface BipeSettingsGroupEvent {
    data class SetTheme(val theme: ThemeOption) : BipeSettingsGroupEvent
    data class SetMediaPreview(val value: MediaPreviewValue) : BipeSettingsGroupEvent
}
