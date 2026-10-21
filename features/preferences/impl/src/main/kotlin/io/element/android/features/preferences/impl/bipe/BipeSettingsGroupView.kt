/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.bipe

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.root.ThemeOption
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.bipe.account.ui.BipeAppLockRow
import io.element.android.libraries.bipe.account.ui.BipePinRow
import io.element.android.libraries.bipe.account.ui.BipeRecoveryEmailRows
import io.element.android.libraries.bipe.account.ui.LocalBipeAccount
import io.element.android.libraries.bipe.api.BipeWhoCanMessage
import io.element.android.libraries.bipe.generated.BipeSettingsGroup
import io.element.android.libraries.bipe.generated.BipeSettingsRow
import io.element.android.libraries.bipe.generated.BipeSettingsRowKind
import io.element.android.libraries.bipe.settings.BipeDataExportRow
import io.element.android.libraries.bipe.settings.BipeSettingsGate
import io.element.android.libraries.bipe.settings.BipeWayBack
import io.element.android.libraries.bipe.settings.titleRes
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.DropdownOption
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferenceDropdown
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import io.element.android.libraries.bipe.R as BipeR

/**
 * A group screen of ADR 0112. The sections, rows, order and gates come from the settings registry in bipe-shared;
 * this view only says how each row looks on Android. [onRowClick] gets the link, url, action and destructive rows, and
 * "Bloquear o app" when the user turns it on.
 */
@Composable
fun BipeSettingsGroupView(
    state: BipeSettingsGroupState,
    onBackClick: () -> Unit,
    onRowClick: (BipeSettingsRow) -> Unit,
    modifier: Modifier = Modifier,
    biometricUnlockRow: @Composable () -> Unit = {},
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
                    SettingsRow(row = row, state = state, onClick = { onRowClick(row) }, biometricUnlockRow = biometricUnlockRow)
                    row.footer?.let { ListSupportingText(text = stringResource(it)) }
                }
                section.footer?.let { ListSupportingText(text = footerText(it, state.hasSeparateDevicePin)) }
            }
            first = false
        }
        if (state.group == BipeSettingsGroup.HELP) {
            BipeVersionFooter(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                version = state.version,
                deviceId = state.deviceId,
            )
        }
    }
}

/** Rows that need something this build does not have yet. */
private fun isAvailable(row: BipeSettingsRow): Boolean = when (row) {
    // Android has no "Contatos no Bipe" screen (address-book matching) yet.
    BipeSettingsRow.PRIVACY_CONTACTS -> false
    else -> true
}

/** Footers with a value to fill in. With more than one account, the PIN footer says the device PIN stays (ADR 0086). */
@Composable
private fun footerText(@StringRes footer: Int, hasSeparateDevicePin: Boolean): String = when (footer) {
    BipeR.string.bipe_account_pin_footer -> if (hasSeparateDevicePin) {
        stringResource(BipeR.string.bipe_account_pin_footer_device)
    } else {
        stringResource(footer, BipePin.LENGTH.toString())
    }
    else -> stringResource(footer)
}

@Composable
private fun SettingsRow(
    row: BipeSettingsRow,
    state: BipeSettingsGroupState,
    onClick: () -> Unit,
    biometricUnlockRow: @Composable () -> Unit,
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
        BipeSettingsRow.PRIVACY_MESSAGING -> LinkRow(title, state.whoCanMessage?.let { stringResource(it.titleRes) }, onClick)
        BipeSettingsRow.PRIVACY_REQUEST_PHOTOS -> PreferenceSwitch(
            title = title,
            isChecked = state.hideInviteAvatars,
            onCheckedChange = { state.eventSink(BipeSettingsGroupEvent.SetHideInviteAvatars(it)) },
        )
        BipeSettingsRow.PRIVACY_BLOCKED -> ListItem(
            content = { Text(title) },
            trailingContent = ListItemContent.Text(
                if (state.blockedUsersCount == 0) stringResource(BipeR.string.bipe_privacy_blocked_none) else state.blockedUsersCount.toString()
            ),
            onClick = onClick,
        )
        BipeSettingsRow.PRIVACY_NUMBER -> InfoRow(
            title = title,
            value = state.phoneLinked?.let {
                stringResource(if (it) BipeR.string.bipe_privacy_number_linked else BipeR.string.bipe_privacy_number_not_linked)
            },
        )
        BipeSettingsRow.PRIVACY_DISCOVERY -> PreferenceSwitch(
            title = title,
            isChecked = state.discoverableByPhone == true,
            enabled = state.discoverableByPhone != null,
            onCheckedChange = { state.eventSink(BipeSettingsGroupEvent.SetDiscoverableByPhone(it)) },
        )
        BipeSettingsRow.PRIVACY_PRESENCE -> PreferenceSwitch(
            title = title,
            isChecked = state.sharePresence,
            onCheckedChange = { state.eventSink(BipeSettingsGroupEvent.SetSharePresence(it)) },
        )
        BipeSettingsRow.PRIVACY_READ_RECEIPTS -> InfoRow(title, stringResource(BipeR.string.bipe_privacy_read_receipts_value))
        BipeSettingsRow.PRIVACY_EXPORT -> LocalBipeAccount.current?.let { BipeDataExportRow(account = it, title = title) }
        BipeSettingsRow.ACCOUNT_WAYS_BACK_STATUS -> state.waysBack?.let { WaysBackStatus(it.size) }
        // Off by default (mz, 2026-10-20). On opens the lock screen setup through [onClick].
        BipeSettingsRow.ACCOUNT_APP_LOCK -> LocalBipeAccount.current?.let { BipeAppLockRow(account = it, title = title, onTurnOn = onClick) }
        // The biometric row shows only while the lock is on (DefaultBipeBiometricUnlock).
        BipeSettingsRow.ACCOUNT_BIOMETRIC_UNLOCK -> biometricUnlockRow()
        BipeSettingsRow.ACCOUNT_CHANGE_PIN -> LocalBipeAccount.current?.let { BipePinRow(it) }
        BipeSettingsRow.ACCOUNT_EMAIL -> LocalBipeAccount.current?.let { BipeRecoveryEmailRows(it) }
        BipeSettingsRow.ACCOUNT_RECOVERY_KEY -> LinkRow(
            title = title,
            subtitle = state.recoveryKeyEnabled?.let {
                stringResource(if (it) BipeR.string.bipe_account_recovery_key_saved else BipeR.string.bipe_account_recovery_key_missing)
            },
            onClick = onClick,
        )
        BipeSettingsRow.ACCOUNT_THIS_DEVICE -> InfoRow(title, state.deviceName)
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

/**
 * The status card of Sua conta (ADR 0112): how many ways back the account has. The only warning in Settings shows at one
 * or none (bipe/docs/security.md).
 */
@Composable
private fun WaysBackStatus(count: Int) {
    val warning = count <= 1
    val warningRes = when (count) {
        0 -> BipeR.string.bipe_fallback_reminder
        1 -> BipeR.string.bipe_account_paths_warning
        else -> null
    }
    ListItem(
        content = {
            Text(
                if (count == 0) {
                    stringResource(BipeR.string.bipe_settings_account_paths_none)
                } else {
                    stringResource(BipeR.string.bipe_settings_account_paths, count.toString())
                }
            )
        },
        supportingContent = warningRes?.let { { Text(stringResource(it)) } },
        leadingContent = if (warning) {
            ListItemContent.Icon(IconSource.Vector(CompoundIcons.Warning()), tintColor = ElementTheme.colors.iconCriticalPrimary)
        } else {
            ListItemContent.Icon(IconSource.Vector(CompoundIcons.CheckCircleSolid()), tintColor = ElementTheme.colors.iconSuccessPrimary)
        },
    )
}

/** A value with no action: a fixed rule or a state the user changes elsewhere. */
@Composable
private fun InfoRow(title: String, value: String?) {
    ListItem(
        content = { Text(title) },
        trailingContent = value?.let { ListItemContent.Text(it) },
    )
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
            waysBack = persistentListOf(BipeWayBack.EMAIL),
            whoCanMessage = BipeWhoCanMessage.EVERYONE,
            hideInviteAvatars = false,
            blockedUsersCount = 0,
            phoneLinked = false,
            discoverableByPhone = true,
            sharePresence = true,
            version = "Versão 26.10.2 (6)",
            deviceId = "ABCDEFGHIJ",
            eventSink = {},
            hasSeparateDevicePin = false,
        ),
        onBackClick = {},
        onRowClick = {},
    )
}
