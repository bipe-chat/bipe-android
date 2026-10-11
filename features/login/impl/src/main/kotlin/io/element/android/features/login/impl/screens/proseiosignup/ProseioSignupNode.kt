/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.proseiosignup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.proseio.ProseioInviteCode

/** Proseio: invite sign-up through the Proseio API, then sign-in through MAS. */
@ContributesNode(AppScope::class)
@AssistedInject
class ProseioSignupNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ProseioSignupPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(
        /** Normalised code from an invite link, or null. */
        val inviteCode: String?,
    ) : NodeInputs

    interface Callback : Plugin {
        fun navigateToOAuth(oAuthDetails: OAuthDetails)
        fun navigateToLoginPassword()
    }

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(
        ProseioSignupPresenter.Params(inviteCode = inputs.inviteCode?.let(ProseioInviteCode::parse))
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ProseioSignupView(
            state = state,
            onBackClick = ::navigateUp,
            onOAuthDetails = callback::navigateToOAuth,
            onNeedLoginPassword = callback::navigateToLoginPassword,
            modifier = modifier,
        )
    }
}
