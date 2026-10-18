/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeWhoCanMessage
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

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
