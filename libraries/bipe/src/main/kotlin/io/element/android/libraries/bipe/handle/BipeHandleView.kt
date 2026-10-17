/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.handle

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.BipeHandleStatus
import io.element.android.libraries.bipe.BipeProfileLink
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.qrcode.BipeQrCodeContent
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.modifiers.clearFocusOnTap
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Port of BipeHandleScreen on iOS: set or change the user's handle, and share the profile link. */
@Composable
fun BipeHandleView(
    state: BipeHandleState,
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    fun save() {
        if (!state.canSave) return
        focusManager.clearFocus()
        state.eventSink(BipeHandleEvent.Save)
    }

    Scaffold(
        modifier = modifier.clearFocusOnTap(focusManager),
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.bipe_settings_handle),
                navigationIcon = { BackButton(onClick = onBackClick) },
                actions = {
                    TextButton(
                        text = stringResource(CommonStrings.action_save),
                        enabled = state.canSave,
                        onClick = ::save,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                label = stringResource(R.string.bipe_settings_handle),
                value = state.handleInput,
                placeholder = stringResource(R.string.bipe_handle_placeholder),
                supportingText = footer(state),
                validity = when {
                    state.handleError != null || state.handleStatus is BipeHandleStatus.Problem -> TextFieldValidity.Invalid
                    state.handleStatus == BipeHandleStatus.Available -> TextFieldValidity.Valid
                    else -> TextFieldValidity.None
                },
                enabled = !state.isLoading && !state.isInCooldown,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                onValueChange = { state.eventSink(BipeHandleEvent.UpdateHandle(it)) },
            )
            state.currentHandle?.let { handle ->
                // Bipe ADR 0066: removal is allowed at any time, also during the cooldown.
                TextButton(
                    text = stringResource(R.string.bipe_handle_remove),
                    leadingIcon = IconSource.Vector(CompoundIcons.Delete()),
                    destructive = true,
                    enabled = state.canRemove,
                    onClick = { state.eventSink(BipeHandleEvent.AskRemove) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ProfileLinkSection(handle)
            }
        }

        AsyncActionView(
            async = state.saveAction,
            onSuccess = { onSaveSuccess() },
            errorMessage = { it.message.orEmpty() },
            onErrorDismiss = { state.eventSink(BipeHandleEvent.ClearError) },
        )
        val context = LocalContext.current
        AsyncActionView(
            async = state.removeAction,
            onSuccess = {
                Toast.makeText(context, R.string.bipe_handle_remove_done, Toast.LENGTH_SHORT).show()
                onSaveSuccess()
            },
            confirmationDialog = {
                ConfirmationDialog(
                    title = stringResource(R.string.bipe_handle_remove),
                    content = stringResource(R.string.bipe_handle_remove_body, state.currentHandle.orEmpty()),
                    submitText = stringResource(R.string.bipe_handle_remove),
                    destructiveSubmit = true,
                    onSubmitClick = { state.eventSink(BipeHandleEvent.Remove) },
                    onDismiss = { state.eventSink(BipeHandleEvent.CancelRemove) },
                )
            },
            errorMessage = { it.message.orEmpty() },
            onErrorDismiss = { state.eventSink(BipeHandleEvent.CancelRemove) },
        )
    }
}

@Composable
private fun footer(state: BipeHandleState): String {
    state.handleError?.let { return it }
    val changeableAt = state.changeableAt
    if (state.isInCooldown && changeableAt != null) {
        val formatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(changeableAt)
        return stringResource(R.string.bipe_handle_cooldown_until, formatted)
    }
    val defaultText = stringResource(R.string.bipe_handle_screen_footer)
    return when (val status = state.handleStatus) {
        BipeHandleStatus.Empty, BipeHandleStatus.Unknown -> defaultText
        BipeHandleStatus.Checking -> stringResource(R.string.bipe_handle_checking)
        BipeHandleStatus.Available -> stringResource(R.string.bipe_handle_available)
        is BipeHandleStatus.Problem -> stringResource(status.problem.messageRes, *status.problem.messageArgs)
    }
}

/** The user's own profile link: QR code and share button. */
@Composable
private fun ProfileLinkSection(handle: String) {
    val url = BipeProfileLink.url(handle) ?: return
    BipeQrCodeContent(
        url = url,
        footer = stringResource(R.string.bipe_profile_qr_footer),
        shareText = url,
        shareLabel = stringResource(R.string.bipe_profile_share),
    )
}

@PreviewsDayNight
@Composable
internal fun BipeHandleViewPreview() = ElementPreview {
    BipeHandleView(
        state = BipeHandleState(
            isLoading = false,
            currentHandle = "ana.souza",
            changeableAt = null,
            handleInput = "ana.souza",
            handleStatus = BipeHandleStatus.Empty,
            handleError = null,
            saveAction = AsyncAction.Uninitialized,
            removeAction = AsyncAction.Uninitialized,
            now = Instant.EPOCH,
            eventSink = {},
        ),
        onBackClick = {},
        onSaveSuccess = {},
    )
}
