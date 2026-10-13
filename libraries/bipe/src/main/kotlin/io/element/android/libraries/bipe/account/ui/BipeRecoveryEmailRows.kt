/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.BipeSignupRules
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipeAccountSession
import io.element.android.libraries.bipe.api.BipeAccountStatus
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

private sealed interface EmailDialog {
    data object Edit : EmailDialog
    data object ConfirmRemove : EmailDialog
    data class Error(val message: String) : EmailDialog
}

/**
 * Recovery email rows of the device-first account (ADR 0057). Port of the email section of BipeAccountScreen on iOS:
 * the confirmed and the pending address, "add or change" (`PUT /v1/me/email`, then a link to confirm) and
 * "Remover e-mail" (`DELETE /v1/me/email`) after a confirmation.
 */
@Composable
internal fun BipeRecoveryEmailRows(account: BipeAccountSession, status: BipeAccountStatus) {
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<EmailDialog?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sentTo by remember { mutableStateOf<String?>(null) }
    val hasEmail = status.email != null || status.emailPending != null

    val lines = buildList {
        status.email?.let { add(it); add(stringResource(R.string.bipe_email_confirmed)) }
        status.emailPending?.let { add(it); add(stringResource(R.string.bipe_email_pending)) }
        sentTo?.let { add(stringResource(R.string.bipe_email_sent, it)) }
        if (!hasEmail && sentTo == null) add(stringResource(R.string.bipe_fallback_reminder))
    }
    ListItem(
        content = { Text(stringResource(R.string.bipe_email_title)) },
        supportingContent = { Text(lines.joinToString("\n")) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Email())),
        enabled = !busy,
        onClick = { dialog = EmailDialog.Edit },
    )
    if (hasEmail) {
        ListItem(
            content = { Text(stringResource(R.string.bipe_email_remove)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Delete())),
            style = ListItemStyle.Destructive,
            enabled = !busy,
            onClick = { dialog = EmailDialog.ConfirmRemove },
        )
    }

    fun send(request: suspend () -> Result<Unit>, onSuccess: () -> Unit) {
        dialog = null
        busy = true
        scope.launch {
            val result = request()
            busy = false
            result
                .onSuccess { onSuccess() }
                .onFailure { dialog = EmailDialog.Error(account.messageOf(it)) }
        }
    }

    when (val shown = dialog) {
        null -> Unit
        EmailDialog.Edit -> BipeRecoveryEmailDialog(
            footer = stringResource(if (hasEmail) R.string.bipe_email_footer else R.string.bipe_fallback_reminder),
            onSubmit = { email -> send({ account.setRecoveryEmail(email) }) { sentTo = email } },
            onDismiss = { dialog = null },
        )
        EmailDialog.ConfirmRemove -> ConfirmationDialog(
            title = stringResource(R.string.bipe_email_remove),
            content = stringResource(R.string.bipe_email_footer),
            submitText = stringResource(R.string.bipe_email_remove),
            destructiveSubmit = true,
            onSubmitClick = { send({ account.removeRecoveryEmail() }) { sentTo = null } },
            onDismiss = { dialog = null },
        )
        is EmailDialog.Error -> ErrorDialog(content = shown.message, onSubmit = { dialog = null })
    }
}

/** Dialog: the new recovery email. Save is on when the address has a plausible shape. */
@Composable
private fun BipeRecoveryEmailDialog(
    footer: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    val trimmed = email.trim()
    val canSave = BipeSignupRules.isPlausibleEmail(trimmed)
    ListDialog(
        title = stringResource(R.string.bipe_email_title),
        subtitle = footer,
        submitText = stringResource(CommonStrings.action_save),
        enabled = canSave,
        onSubmit = { if (canSave) onSubmit(trimmed) },
        onDismissRequest = onDismiss,
    ) {
        item {
            TextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.bipe_recovery_email_field),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            )
        }
    }
}
