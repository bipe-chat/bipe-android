/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.weblink

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun BipeLinkedDevicesView(
    state: BipeLinkedDevicesState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.bipe_linked_devices_title),
    ) {
        if (state.showsLinkComputer) {
            ListItem(
                content = { Text(stringResource(R.string.bipe_linked_devices_link_computer)) },
                supportingContent = { Text(stringResource(R.string.bipe_linked_devices_footer)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Computer())),
                trailingContent = if (state.isCheckingSupport) ListItemContent.Custom { CircularProgressIndicator() } else null,
                enabled = !state.isCheckingSupport,
                onClick = { state.eventSink(BipeLinkedDevicesEvent.LinkComputer) },
            )
        }
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.devices.isEmpty() -> Text(
                text = stringResource(R.string.bipe_linked_devices_empty),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            else -> for (device in state.devices) {
                val lastUsed = device.lastSeenTs?.let { seen ->
                    val relative = DateUtils.getRelativeTimeSpanString(seen, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    stringResource(R.string.bipe_linked_devices_last_used, relative)
                }
                PreferenceCategory(showTopDivider = true) {
                    ListItem(
                        content = { Text(device.displayName ?: stringResource(R.string.bipe_linked_devices_unknown_name)) },
                        supportingContent = lastUsed?.let { text -> @Composable { Text(text) } },
                        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Devices())),
                    )
                    ListItem(
                        content = { Text(stringResource(R.string.bipe_linked_devices_sign_out)) },
                        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.SignOut())),
                        style = ListItemStyle.Destructive,
                        onClick = { state.eventSink(BipeLinkedDevicesEvent.AskSignOut(device)) },
                    )
                }
            }
        }
    }

    state.signOutCandidate?.let { device ->
        ConfirmationDialog(
            title = stringResource(
                R.string.bipe_linked_devices_sign_out_title,
                device.displayName ?: stringResource(R.string.bipe_linked_devices_unknown_name),
            ),
            content = stringResource(R.string.bipe_linked_devices_sign_out_body),
            submitText = stringResource(R.string.bipe_linked_devices_sign_out),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(BipeLinkedDevicesEvent.ConfirmSignOut) },
            onDismiss = { state.eventSink(BipeLinkedDevicesEvent.DismissDialog) },
        )
    }
    if (state.notSupported) {
        ErrorDialog(
            title = stringResource(R.string.bipe_link_computer_error_title),
            content = stringResource(R.string.bipe_link_computer_not_supported),
            onSubmit = { state.eventSink(BipeLinkedDevicesEvent.DismissDialog) },
        )
    } else if (state.loadFailed) {
        ErrorDialog(
            content = stringResource(R.string.bipe_linked_devices_error),
            onSubmit = { state.eventSink(BipeLinkedDevicesEvent.DismissDialog) },
        )
    }
}
