/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.bipesignup

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
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.bipe.BipeInviteCode

/** Bipe: device-first sign-up through the Bipe API, then sign-in with the hidden password (ADR 0057). No browser. */
@ContributesNode(AppScope::class)
@AssistedInject
class BipeSignupNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: BipeSignupPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(
        /** Normalised code from an invite link, or null. */
        val inviteCode: String?,
        /** False when the start screen's button goes straight to the name (bipe-chat/bipe#415). */
        val startsWithInviteCode: Boolean = true,
    ) : NodeInputs

    private val inputs: Inputs = inputs()
    private val presenter = presenterFactory.create(
        BipeSignupPresenter.Params(
            inviteCode = inputs.inviteCode?.let(BipeInviteCode::parse),
            startsWithInviteCode = inputs.startsWithInviteCode,
        )
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        BipeSignupView(
            state = state,
            onBackClick = ::navigateUp,
            modifier = modifier,
        )
    }
}
