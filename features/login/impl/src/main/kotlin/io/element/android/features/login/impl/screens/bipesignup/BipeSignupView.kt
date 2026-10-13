/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.bipesignup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.login.LoginModeView
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.modifiers.clearFocusOnTap
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.bipe.BipeHandleStatus
import io.element.android.libraries.bipe.BipeSignupRules
import io.element.android.libraries.bipe.R
import io.element.android.libraries.ui.strings.CommonStrings

/** Port of BipeSignupScreen on iOS. */
@Composable
fun BipeSignupView(
    state: BipeSignupState,
    onBackClick: () -> Unit,
    onOAuthDetails: (OAuthDetails) -> Unit,
    onNeedLoginPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val eventSink = state.eventSink
    fun submit() {
        if (!state.canSubmit) return
        focusManager.clearFocus()
        eventSink(BipeSignupEvent.Submit)
    }
    val fieldsEnabled = !state.isAccountCreated

    Scaffold(
        modifier = modifier.clearFocusOnTap(focusManager),
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.proseio_signup_title),
                navigationIcon = { BackButton(onClick = onBackClick) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                iconStyle = BigIcon.Style.Default(CompoundIcons.UserProfileSolid()),
                title = stringResource(R.string.proseio_signup_title),
                subTitle = state.inviterHandle?.let { stringResource(R.string.proseio_signup_invited_by, it) }
                    ?: stringResource(R.string.proseio_signup_header),
            )
            TextField(
                label = stringResource(R.string.proseio_signup_display_name),
                value = state.displayName,
                supportingText = stringResource(R.string.proseio_signup_display_name_footer),
                enabled = fieldsEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                onValueChange = { eventSink(BipeSignupEvent.UpdateDisplayName(it)) },
            )
            TextField(
                label = stringResource(R.string.proseio_signup_email),
                value = state.email,
                supportingText = state.emailError ?: stringResource(R.string.proseio_signup_email_footer),
                validity = if (state.emailError != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                enabled = fieldsEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                onValueChange = { eventSink(BipeSignupEvent.UpdateEmail(it)) },
            )
            TextField(
                label = stringResource(R.string.proseio_signup_password),
                value = state.password,
                supportingText = state.passwordError
                    ?: stringResource(R.string.proseio_signup_password_footer, BipeSignupRules.PASSWORD_MIN_LENGTH.toString()),
                validity = if (state.passwordError != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                enabled = fieldsEnabled,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                onValueChange = { eventSink(BipeSignupEvent.UpdatePassword(it)) },
            )
            TextField(
                label = stringResource(R.string.proseio_signup_password_confirm),
                value = state.passwordConfirmation,
                supportingText = if (state.passwordMismatch) stringResource(R.string.proseio_signup_password_mismatch) else null,
                validity = if (state.passwordMismatch) TextFieldValidity.Invalid else TextFieldValidity.None,
                enabled = fieldsEnabled,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                onValueChange = { eventSink(BipeSignupEvent.UpdatePasswordConfirmation(it)) },
            )
            TextField(
                label = stringResource(R.string.proseio_signup_handle),
                value = state.handle,
                placeholder = stringResource(R.string.proseio_handle_placeholder),
                supportingText = state.handleError ?: handleFooter(state.handleStatus),
                validity = when {
                    state.handleError != null || state.handleStatus is BipeHandleStatus.Problem -> TextFieldValidity.Invalid
                    state.handleStatus == BipeHandleStatus.Available -> TextFieldValidity.Valid
                    else -> TextFieldValidity.None
                },
                enabled = fieldsEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                onValueChange = { eventSink(BipeSignupEvent.UpdateHandle(it)) },
            )
            val inviteCodeFooter = state.inviteCodeError
                ?: if (state.isInviteCodeAcceptable) null else stringResource(R.string.proseio_error_invite_invalid)
            TextField(
                label = stringResource(R.string.proseio_signup_invite_code),
                value = state.inviteCodeInput,
                placeholder = "XXXX-XXXX-XXXX",
                supportingText = inviteCodeFooter,
                validity = if (inviteCodeFooter != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                enabled = fieldsEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                onValueChange = { eventSink(BipeSignupEvent.UpdateInviteCode(it)) },
            )
            Button(
                text = if (state.isAccountCreated) {
                    stringResource(CommonStrings.action_continue)
                } else {
                    stringResource(R.string.proseio_signup_submit)
                },
                enabled = state.canSubmit,
                onClick = ::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
            )
        }

        AsyncActionView(
            async = state.createAction,
            onSuccess = {},
            errorMessage = { it.message.orEmpty() },
            onErrorDismiss = { eventSink(BipeSignupEvent.ClearError) },
        )
        LoginModeView(
            loginMode = state.loginModeState.loginMode,
            onClearError = { eventSink(BipeSignupEvent.ClearError) },
            onLearnMoreClick = {},
            onOAuthDetails = onOAuthDetails,
            onNeedLoginPassword = onNeedLoginPassword,
        )
    }
}

@Composable
private fun handleFooter(status: BipeHandleStatus): String = when (status) {
    BipeHandleStatus.Empty, BipeHandleStatus.Unknown -> stringResource(R.string.proseio_signup_handle_footer)
    BipeHandleStatus.Checking -> stringResource(R.string.proseio_handle_checking)
    BipeHandleStatus.Available -> stringResource(R.string.proseio_handle_available)
    is BipeHandleStatus.Problem -> stringResource(status.problem.messageRes, *status.problem.messageArgs)
}
