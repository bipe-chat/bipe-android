/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.features.preferences.impl.developer.appsettings.AppDeveloperSettingsNode
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.bipe.generated.BipeSettingsGroup

@ContributesBinding(AppScope::class)
class DefaultPreferencesEntryPoint : PreferencesEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: PreferencesEntryPoint.Params,
        callback: PreferencesEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<PreferencesFlowNode>(
            buildContext = buildContext,
            plugins = listOf(params, callback)
        )
    }

    override fun createAppDeveloperSettingsNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: PreferencesEntryPoint.DeveloperSettingsCallback,
    ): Node {
        return parentNode.createNode<AppDeveloperSettingsNode>(
            buildContext = buildContext,
            plugins = listOf(callback),
        )
    }
}

internal fun PreferencesEntryPoint.InitialTarget.toNavTarget() = when (this) {
    is PreferencesEntryPoint.InitialTarget.Root -> PreferencesFlowNode.NavTarget.Root
    is PreferencesEntryPoint.InitialTarget.NotificationSettings -> PreferencesFlowNode.NavTarget.NotificationSettings
    PreferencesEntryPoint.InitialTarget.NotificationTroubleshoot -> PreferencesFlowNode.NavTarget.TroubleshootNotifications
    PreferencesEntryPoint.InitialTarget.DeveloperSettings -> PreferencesFlowNode.NavTarget.DeveloperSettings
    // Bipe: the Você tab rows (ADR 0053)
    is PreferencesEntryPoint.InitialTarget.BipeEditProfile -> PreferencesFlowNode.NavTarget.UserProfile(matrixUser)
    is PreferencesEntryPoint.InitialTarget.BipeScreen -> when (screen) {
        PreferencesEntryPoint.BipeSettingsScreen.Profile -> BipeSettingsGroup.PROFILE.toNavTarget()
        PreferencesEntryPoint.BipeSettingsScreen.Privacy -> BipeSettingsGroup.PRIVACY.toNavTarget()
        PreferencesEntryPoint.BipeSettingsScreen.Account -> BipeSettingsGroup.ACCOUNT.toNavTarget()
        PreferencesEntryPoint.BipeSettingsScreen.Chats -> BipeSettingsGroup.CHATS.toNavTarget()
        PreferencesEntryPoint.BipeSettingsScreen.Help -> BipeSettingsGroup.HELP.toNavTarget()
        PreferencesEntryPoint.BipeSettingsScreen.Analytics -> PreferencesFlowNode.NavTarget.AnalyticsSettings
        PreferencesEntryPoint.BipeSettingsScreen.About -> PreferencesFlowNode.NavTarget.About
        PreferencesEntryPoint.BipeSettingsScreen.DeveloperSettings -> PreferencesFlowNode.NavTarget.DeveloperSettings
        PreferencesEntryPoint.BipeSettingsScreen.LockScreen -> PreferencesFlowNode.NavTarget.LockScreenSettings
        PreferencesEntryPoint.BipeSettingsScreen.Media -> PreferencesFlowNode.NavTarget.MediaSettings
        PreferencesEntryPoint.BipeSettingsScreen.Location -> PreferencesFlowNode.NavTarget.LocationSettings
        PreferencesEntryPoint.BipeSettingsScreen.Labs -> PreferencesFlowNode.NavTarget.Labs
        PreferencesEntryPoint.BipeSettingsScreen.ModerationAndSafety -> BipeSettingsGroup.PRIVACY.toNavTarget()
        PreferencesEntryPoint.BipeSettingsScreen.Notifications -> PreferencesFlowNode.NavTarget.NotificationSettings
        PreferencesEntryPoint.BipeSettingsScreen.SignOut -> PreferencesFlowNode.NavTarget.SignOut
        PreferencesEntryPoint.BipeSettingsScreen.AccountDeactivation -> PreferencesFlowNode.NavTarget.AccountDeactivation
        PreferencesEntryPoint.BipeSettingsScreen.Handle -> PreferencesFlowNode.NavTarget.BipeHandle
        PreferencesEntryPoint.BipeSettingsScreen.Invites -> PreferencesFlowNode.NavTarget.BipeInvites
        PreferencesEntryPoint.BipeSettingsScreen.PublicProfile -> PreferencesFlowNode.NavTarget.BipePublicProfile
    }
}
