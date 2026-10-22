/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.contacts

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

/** "Contatos e convites" in settings. */
@ContributesNode(SessionScope::class)
@AssistedInject
class BipeContactsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: BipeContactsPresenter,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun navigateToUserProfile(userId: String)

        /** "Convites ainda não usados": the invites screen. */
        fun navigateToInvites()
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        BipeContactsView(
            state = state,
            onBackClick = ::navigateUp,
            onUserClick = callback::navigateToUserProfile,
            onPendingInvitesClick = callback::navigateToInvites,
            modifier = modifier,
        )
    }
}
