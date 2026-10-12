/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope

/** "Perfil público" in settings. */
@ContributesNode(SessionScope::class)
@AssistedInject
class ProseioPublicProfileNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: ProseioPublicProfilePresenter,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        /** No handle yet: open the handle screen. */
        fun navigateToHandle()
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ProseioPublicProfileView(
            state = state,
            onBackClick = ::navigateUp,
            onHandleClick = callback::navigateToHandle,
            onSaveSuccess = ::navigateUp,
            modifier = modifier,
        )
    }
}
