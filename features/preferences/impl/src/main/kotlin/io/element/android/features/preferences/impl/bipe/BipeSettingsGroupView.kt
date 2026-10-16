/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.bipe

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R as BipeR
import io.element.android.libraries.bipe.account.ui.BipePinRow
import io.element.android.libraries.bipe.account.ui.BipeRecoveryEmailRows
import io.element.android.libraries.bipe.account.ui.LocalBipeAccount
import io.element.android.libraries.bipe.generated.BipeSettingsGroup
import io.element.android.libraries.bipe.generated.BipeSettingsRow
import io.element.android.libraries.bipe.generated.BipeSettingsRowKind
import io.element.android.libraries.bipe.settings.BipeSettingsGate
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.DropdownOption
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferenceDropdown
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.ui.components.aMatrixUser
import io.element.android.features.preferences.impl.root.ThemeOption
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A group screen of ADR 0112. The sections, rows, order and gates come from the settings registry in bipe-shared;
 * this view only says how each row looks on Android. [onRowClick] gets the link, url, action and destructive rows.
 */
@Composable
fun BipeSettingsGroupView(
    state: BipeSettingsGroupState,
    onBackClick: () -> Unit,
    onRowClick: (BipeSettingsRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarDispatcher = LocalSnackbarDispatcher.current
    val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = snackbarMessage)
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(state.group.title),
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
    ) {
        var first = true
        for (section in state.group.sections) {
            val rows = BipeSettingsGate.visibleRows(section) { it in state.enabledFlags }.filter { isAvailable(it) }
            if (rows.isEmpty()) continue
            PreferenceCategory(
                title = section.title?.let { stringResource(it) },
                showTopDivider = !first,
            ) {
                for (row in rows) {
                    SettingsRow(row = row, state = state, onClick = { onRowClick(row) })
                    row.footer?.let { ListSupportingText(text = stringResource(it)) }
                }
                section.footer?.let { ListSupportingText(text = stringResource(it)) }
            }
            first = false
        }
    }
}

/** Rows that need something this build does not have yet. */
private fun isAvailable(row: BipeSettingsRow): Boolean = when (row) {
    // Bipe: built with the Privacidade group (#359).
    BipeSettingsRow.PRIVACY_MESSAGING, BipeSettingsRow.PRIVACY_REQUEST_PHOTOS, BipeSettingsRow.PRIVACY_BLOCKED,
    BipeSettingsRow.PRIVACY_NUMBER, BipeSettingsRow.PRIVACY_DISCOVERY, BipeSettingsRow.PRIVACY_CONTACTS,
    BipeSettingsRow.PRIVACY_PRESENCE, BipeSettingsRow.PRIVACY_READ_RECEIPTS, BipeSettingsRow.PRIVACY_WHO_CAN_READ,
    BipeSettingsRow.PRIVACY_EXPORT, BipeSettingsRow.PRIVACY_WHAT_BIPE_KEEPS,
    // Bipe: built with Sua conta (#360).
    BipeSettingsRow.ACCOUNT_WAYS_BACK_STATUS, BipeSettingsRow.ACCOUNT_BIOMETRIC_UNLOCK,
    // Bipe: built with Ajuda e sobre (#362).
    BipeSettingsRow.HELP_DOCS, BipeSettingsRow.HELP_TERMS, BipeSettingsRow.HELP_PRIVACY, BipeSettingsRow.HELP_LICENSES -> false
    else -> true
}

@Composable
private fun SettingsRow(
    row: BipeSettingsRow,
    state: BipeSettingsGroupState,
    onClick: () -> Unit,
) {
    val title = stringResource(row.title)
    when (row) {
        BipeSettingsRow.PROFILE_NAME_PHOTO -> LinkRow(title, state.myUser.displayName, onClick)
        BipeSettingsRow.PROFILE_HANDLE -> LinkRow(title, state.handle?.let { "@$it" } ?: stringResource(BipeR.string.bipe_settings_handle_none), onClick)
        BipeSettingsRow.PROFILE_PUBLIC_PROFILE -> LinkRow(
            title = title,
            subtitle = state.publicProfileEnabled?.let {
                stringResource(if (it) BipeR.string.bipe_public_profile_on else BipeR.string.bipe_public_profile_off)
            },
            onClick = onClick,
        )
        BipeSettingsRow.ACCOUNT_CHANGE_PIN -> LocalBipeAccount.current?.let { BipePinRow(it) }
        BipeSettingsRow.ACCOUNT_EMAIL -> LocalBipeAccount.current?.let { BipeRecoveryEmailRows(it) }
        BipeSettingsRow.ACCOUNT_RECOVERY_KEY -> LinkRow(
            title = title,
            subtitle = state.recoveryKeyEnabled?.let {
                stringResource(if (it) BipeR.string.bipe_account_recovery_key_saved else BipeR.string.bipe_account_recovery_key_missing)
            },
            onClick = onClick,
        )
        BipeSettingsRow.ACCOUNT_THIS_DEVICE -> ListItem(
            content = { Text(title) },
            trailingContent = ListItemContent.Text(state.deviceName),
        )
        BipeSettingsRow.CHATS_APPEARANCE -> PreferenceDropdown(
            title = title,
            selectedOption = state.theme,
            options = state.availableThemeOptions,
            onSelectOption = { state.eventSink(BipeSettingsGroupEvent.SetTheme(it)) },
        )
        BipeSettingsRow.CHATS_SHOW_MEDIA -> PreferenceDropdown(
            title = title,
            selectedOption = ShowMediaOption.of(state.mediaPreviewValue),
            options = ShowMediaOption.entries.toImmutableList(),
            onSelectOption = { state.eventSink(BipeSettingsGroupEvent.SetMediaPreview(it.value)) },
        )
        else -> when (row.kind) {
            BipeSettingsRowKind.URL -> ListItem(
                content = { Text(title) },
                trailingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.PopOut())),
                onClick = onClick,
            )
            BipeSettingsRowKind.DESTRUCTIVE -> ListItem(
                content = { Text(title) },
                style = ListItemStyle.Destructive,
                onClick = onClick,
            )
            else -> LinkRow(title, null, onClick)
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        content = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        onClick = onClick,
    )
}

/** "Mostrar fotos e vídeos" (ADR 0112): Element's timeline media preview setting in Bipe words. */
private enum class ShowMediaOption(val value: MediaPreviewValue) : DropdownOption {
    Always(MediaPreviewValue.On) {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(BipeR.string.bipe_chats_show_media_always)
    },
    Private(MediaPreviewValue.Private) {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(BipeR.string.bipe_chats_show_media_private)
    },
    Never(MediaPreviewValue.Off) {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(BipeR.string.bipe_chats_show_media_never)
    };

    companion object {
        fun of(value: MediaPreviewValue): ShowMediaOption = entries.first { it.value == value }
    }
}

@PreviewsDayNight
@Composable
internal fun BipeSettingsGroupViewPreview() = ElementPreview {
    BipeSettingsGroupView(
        state = BipeSettingsGroupState(
            group = BipeSettingsGroup.CHATS,
            myUser = aMatrixUser(displayName = "Ana Souza"),
            handle = "ana.souza",
            publicProfileEnabled = true,
            enabledFlags = persistentSetOf(),
            theme = ThemeOption.System,
            availableThemeOptions = ThemeOption.entries.toImmutableList(),
            mediaPreviewValue = MediaPreviewValue.On,
            recoveryKeyEnabled = true,
            deviceName = "Motorola moto g54",
            eventSink = {},
        ),
        onBackClick = {},
        onRowClick = {},
    )
}
