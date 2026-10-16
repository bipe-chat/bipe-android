/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeWhoCanMessage
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.launch

/** "Quem pode me mandar mensagem" (ADR 0016, ADR 0112): three values, saved with `PUT /v1/me/messaging`. Port of BipeMessagingScreen on iOS. */
data class BipeMessagingState(
    /** `null` while the setting is not loaded. */
    val selected: BipeWhoCanMessage?,
    val saveAction: AsyncAction<Unit>,
    val eventSink: (BipeMessagingEvent) -> Unit,
)

sealed interface BipeMessagingEvent {
    data class Select(val value: BipeWhoCanMessage) : BipeMessagingEvent
    data object ClearError : BipeMessagingEvent
}

@Inject
class BipeMessagingPresenter(
    private val bipeSession: BipeSession,
) : Presenter<BipeMessagingState> {
    @Composable
    override fun present(): BipeMessagingState {
        val scope = rememberCoroutineScope()
        val selected by bipeSession.privacy.messaging.collectAsState()
        var saveAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        LaunchedEffect(Unit) { bipeSession.privacy.refreshMessaging() }

        fun handleEvent(event: BipeMessagingEvent) {
            when (event) {
                is BipeMessagingEvent.Select -> {
                    if (event.value == selected || saveAction.isLoading()) return
                    saveAction = AsyncAction.Loading
                    scope.launch {
                        saveAction = bipeSession.privacy.setMessaging(event.value).fold(
                            onSuccess = { AsyncAction.Uninitialized },
                            onFailure = { AsyncAction.Failure(Exception(bipeSession.account.messageOf(it))) },
                        )
                    }
                }
                BipeMessagingEvent.ClearError -> saveAction = AsyncAction.Uninitialized
            }
        }

        return BipeMessagingState(selected = selected, saveAction = saveAction, eventSink = ::handleEvent)
    }
}

@ContributesNode(SessionScope::class)
@AssistedInject
class BipeMessagingNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: BipeMessagingPresenter,
) : Node(buildContext, plugins = plugins) {
    @Composable
    override fun View(modifier: Modifier) {
        BipeMessagingView(state = presenter.present(), onBackClick = ::navigateUp, modifier = modifier)
    }
}

@Composable
fun BipeMessagingView(
    state: BipeMessagingState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.bipe_messaging_title),
    ) {
        for (option in BipeWhoCanMessage.entries) {
            ListItem(
                content = { Text(stringResource(option.titleRes)) },
                supportingContent = { Text(stringResource(option.bodyRes)) },
                leadingContent = ListItemContent.RadioButton(selected = option == state.selected),
                enabled = state.selected != null && !state.saveAction.isLoading(),
                onClick = { state.eventSink(BipeMessagingEvent.Select(option)) },
            )
        }
    }
    AsyncActionView(
        async = state.saveAction,
        onSuccess = {},
        errorMessage = { it.message.orEmpty() },
        onErrorDismiss = { state.eventSink(BipeMessagingEvent.ClearError) },
    )
}

/** The value as the settings show it: "Todo mundo", "Só contas verificadas", "Só contatos". */
val BipeWhoCanMessage.titleRes: Int
    get() = when (this) {
        BipeWhoCanMessage.EVERYONE -> R.string.bipe_messaging_everyone
        BipeWhoCanMessage.VERIFIED -> R.string.bipe_messaging_verified
        BipeWhoCanMessage.CONTACTS -> R.string.bipe_messaging_contacts
    }

private val BipeWhoCanMessage.bodyRes: Int
    get() = when (this) {
        BipeWhoCanMessage.EVERYONE -> R.string.bipe_messaging_everyone_body
        BipeWhoCanMessage.VERIFIED -> R.string.bipe_messaging_verified_body
        BipeWhoCanMessage.CONTACTS -> R.string.bipe_messaging_contacts_body
    }

@PreviewsDayNight
@Composable
internal fun BipeMessagingViewPreview() = ElementPreview {
    BipeMessagingView(
        state = BipeMessagingState(selected = BipeWhoCanMessage.EVERYONE, saveAction = AsyncAction.Uninitialized, eventSink = {}),
        onBackClick = {},
    )
}
