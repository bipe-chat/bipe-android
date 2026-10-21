/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.contacts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.bipe.invites.BipeInvitesPresenter
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.launch

/** "Contatos e convites" in Privacidade (bipe#401). */
@ContributesNode(SessionScope::class)
@AssistedInject
class BipeContactsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: BipeInvitesPresenter,
    private val bipeSession: BipeSession,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        /** Someone who joined with the user's invite: open their profile. */
        fun navigateToUserProfile(userId: String)

        /** "Convites ainda não usados": open "Convidar pessoas", where each invite can be shared or cancelled. */
        fun navigateToInvites()
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val coroutineScope = rememberCoroutineScope()
        val discoverableByPhone by bipeSession.privacy.discoverableByPhone.collectAsState()
        LaunchedEffect(Unit) { bipeSession.privacy.refreshDiscovery() }
        BipeContactsView(
            state = state,
            discoverableByPhone = discoverableByPhone,
            onDiscoverableChange = { value -> coroutineScope.launch { bipeSession.privacy.setDiscoverableByPhone(value) } },
            onBackClick = ::navigateUp,
            onUserClick = callback::navigateToUserProfile,
            onPendingInvitesClick = callback::navigateToInvites,
            modifier = modifier,
        )
    }
}
