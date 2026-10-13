/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.biperecovery

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

/** Bipe: get back into a device-first account (ADR 0057, ADR 0059). */
@ContributesNode(AppScope::class)
@AssistedInject
class BipeRecoveryNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: BipeRecoveryPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(
        /** The token from a `bipe.chat/recuperar/` e-mail link, or null. */
        val linkToken: String?,
    ) : NodeInputs {
        override fun toString(): String = "Inputs(hasLinkToken=${linkToken != null})"
    }

    interface Callback : Plugin {
        fun navigateToQrCode()
    }

    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(inputs<Inputs>().linkToken)

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        BipeRecoveryView(
            state = state,
            onBackClick = ::navigateUp,
            onSignInWithQrCode = callback::navigateToQrCode,
            modifier = modifier,
        )
    }
}
