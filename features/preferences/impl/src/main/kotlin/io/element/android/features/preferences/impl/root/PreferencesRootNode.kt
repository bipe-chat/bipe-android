/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.preferences.impl.account.PreferencesAccountCallback
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.bipe.generated.BipeSettingsGroup
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class PreferencesRootNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: PreferencesRootPresenter,
) : Node(buildContext, plugins = plugins) {
    interface Callback : PreferencesAccountCallback, Plugin {
        fun navigateToAddAccount()
        fun navigateToAnalyticsSettings()
        fun navigateToAbout()
        fun navigateToDeveloperSettings()
        fun navigateToLockScreenSettings()
        fun navigateToMediaSettings()
        fun navigateToLocationSettings()
        fun navigateToLabs()
        fun navigateToBipeHandle() // Bipe
        fun navigateToBipeContacts() // Bipe: "Convidar pessoas" opens the contacts screen, as on iOS
        fun navigateToBipePublicProfile() // Bipe
        fun navigateToBipeGroup(group: BipeSettingsGroup) // Bipe: Seu perfil and the groups of ADR 0112
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        PreferencesRootView(
            state = state,
            modifier = modifier,
            onBackClick = this::navigateUp,
            onOpenAnalytics = callback::navigateToAnalyticsSettings,
            onOpenDeveloperSettings = callback::navigateToDeveloperSettings,
            onOpenLabs = callback::navigateToLabs,
            onOpenRageShake = callback::navigateToBugReport,
            onEditProfileClick = callback::navigateToUserProfile,
            onBipeHandleClick = callback::navigateToBipeHandle,
            onBipeInvitesClick = callback::navigateToBipeContacts,
            onBipeProfileClick = { callback.navigateToBipeGroup(BipeSettingsGroup.PROFILE) },
            onBipeGroupClick = callback::navigateToBipeGroup,
        )
    }
}
