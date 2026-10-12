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
    // Proseio: the Você tab rows (ADR 0053)
    is PreferencesEntryPoint.InitialTarget.ProseioEditProfile -> PreferencesFlowNode.NavTarget.UserProfile(matrixUser)
    is PreferencesEntryPoint.InitialTarget.ProseioScreen -> when (screen) {
        PreferencesEntryPoint.ProseioSettingsScreen.Analytics -> PreferencesFlowNode.NavTarget.AnalyticsSettings
        PreferencesEntryPoint.ProseioSettingsScreen.About -> PreferencesFlowNode.NavTarget.About
        PreferencesEntryPoint.ProseioSettingsScreen.DeveloperSettings -> PreferencesFlowNode.NavTarget.DeveloperSettings
        PreferencesEntryPoint.ProseioSettingsScreen.LockScreen -> PreferencesFlowNode.NavTarget.LockScreenSettings
        PreferencesEntryPoint.ProseioSettingsScreen.Media -> PreferencesFlowNode.NavTarget.MediaSettings
        PreferencesEntryPoint.ProseioSettingsScreen.Location -> PreferencesFlowNode.NavTarget.LocationSettings
        PreferencesEntryPoint.ProseioSettingsScreen.Labs -> PreferencesFlowNode.NavTarget.Labs
        PreferencesEntryPoint.ProseioSettingsScreen.ModerationAndSafety -> PreferencesFlowNode.NavTarget.ModerationAndSafety
        PreferencesEntryPoint.ProseioSettingsScreen.Notifications -> PreferencesFlowNode.NavTarget.NotificationSettings
        PreferencesEntryPoint.ProseioSettingsScreen.SignOut -> PreferencesFlowNode.NavTarget.SignOut
        PreferencesEntryPoint.ProseioSettingsScreen.AccountDeactivation -> PreferencesFlowNode.NavTarget.AccountDeactivation
        PreferencesEntryPoint.ProseioSettingsScreen.Handle -> PreferencesFlowNode.NavTarget.ProseioHandle
        PreferencesEntryPoint.ProseioSettingsScreen.Invites -> PreferencesFlowNode.NavTarget.ProseioInvites
        PreferencesEntryPoint.ProseioSettingsScreen.PublicProfile -> PreferencesFlowNode.NavTarget.ProseioPublicProfile
    }
}
