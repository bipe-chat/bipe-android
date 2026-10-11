/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.invites

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

/** "Convites" in settings. */
@ContributesNode(SessionScope::class)
@AssistedInject
class ProseioInvitesNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: ProseioInvitesPresenter,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        /** A used invite: open the profile of the person who joined. */
        fun navigateToUserProfile(userId: String)
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ProseioInvitesView(
            state = state,
            onBackClick = ::navigateUp,
            onUserClick = callback::navigateToUserProfile,
            modifier = modifier,
        )
    }
}
