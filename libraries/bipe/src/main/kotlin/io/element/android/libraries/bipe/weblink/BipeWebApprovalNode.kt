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
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import kotlinx.coroutines.CoroutineScope

/** Hosts [BipeWebApprovalView]. The session scope keeps the finish call alive after the screen closes. */
@ContributesNode(SessionScope::class)
@AssistedInject
class BipeWebApprovalNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val bipeSession: BipeSession,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val purpose: BipeWebApprovalController.Purpose) : NodeInputs

    interface Callback : Plugin {
        /** The sign-out page closed, or the flow could not start. */
        fun onFinished()
    }

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val controller = remember {
            BipeWebApprovalController(purpose = inputs.purpose, webLink = bipeSession.webLink, scope = sessionCoroutineScope)
                .also { it.onFinished = callback::onFinished }
        }
        BipeWebApprovalView(controller = controller, modifier = modifier)
    }
}
