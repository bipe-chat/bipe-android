/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.weblink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags

/** Settings > Linked devices (ADR 0116). The root of the link-new-device flow. */
@ContributesNode(SessionScope::class)
@AssistedInject
class BipeLinkedDevicesNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val bipeSession: BipeSession,
    private val featureFlagService: FeatureFlagService,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        /** The support check passed: open the scanner (desktop direction only). */
        fun onLinkComputer()
        fun onSignOut(deviceId: String)
        fun onDone()
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val presenter = remember {
            BipeLinkedDevicesPresenter(
                bipeSession = bipeSession,
                showsLinkComputer = featureFlagService.isFeatureEnabledFlow(FeatureFlags.QrCodeLogin),
                onLinkComputer = callback::onLinkComputer,
                onSignOut = callback::onSignOut,
            )
        }
        BipeLinkedDevicesView(state = presenter.present(), onBackClick = callback::onDone, modifier = modifier)
    }
}
