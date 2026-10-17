/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.bipesignup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.BipeHandleStatus
import io.element.android.libraries.bipe.BipeSignupRules
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.bipe.account.ui.BipeNewPinFields
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.libraries.bipe.terms.BipeTermsRow
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.modifiers.clearFocusOnTap
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

/** Port of BipeSignupScreen on iOS: device-first sign-up (ADR 0057). The user never sees or types a password. */
@Composable
fun BipeSignupView(
    state: BipeSignupState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val eventSink = state.eventSink
    val isLoading = state.createAction is AsyncAction.Loading
    fun back() {
        if (state.step == BipeSignupStep.FORM) onBackClick() else eventSink(BipeSignupEvent.Back)
    }
    BackHandler(enabled = state.step != BipeSignupStep.FORM && !isLoading) { back() }

    Scaffold(
        modifier = modifier.clearFocusOnTap(focusManager),
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.bipe_signup_title),
                navigationIcon = { BackButton(onClick = ::back) },
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
            when (state.step) {
                BipeSignupStep.FORM -> FormStep(state)
                BipeSignupStep.PIN -> PinStep(state, isLoading)
                BipeSignupStep.FALLBACK -> FallbackStep(state, isLoading)
            }
        }

        AsyncActionView(
            async = state.createAction,
            onSuccess = {},
            errorMessage = { it.message.orEmpty() },
            onErrorDismiss = { eventSink(BipeSignupEvent.ClearError) },
        )
    }
}

@Composable
private fun ColumnScope.FormStep(state: BipeSignupState) {
    val eventSink = state.eventSink
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        iconStyle = BigIcon.Style.Default(CompoundIcons.UserProfileSolid()),
        title = stringResource(R.string.bipe_signup_title),
        subTitle = state.inviterHandle?.let { stringResource(R.string.bipe_signup_invited_by, it) }
            ?: stringResource(R.string.bipe_signup_device_header),
    )
    TextField(
        label = stringResource(R.string.bipe_signup_display_name),
        value = state.displayName,
        supportingText = stringResource(R.string.bipe_signup_display_name_footer),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        onValueChange = { eventSink(BipeSignupEvent.UpdateDisplayName(it)) },
    )
    TextField(
        label = stringResource(R.string.bipe_signup_handle),
        value = state.handle,
        placeholder = stringResource(R.string.bipe_handle_placeholder),
        supportingText = state.handleError ?: handleFooter(state.handleStatus),
        validity = when {
            state.handleError != null || state.handleStatus is BipeHandleStatus.Problem -> TextFieldValidity.Invalid
            state.handleStatus == BipeHandleStatus.Available -> TextFieldValidity.Valid
            else -> TextFieldValidity.None
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        onValueChange = { eventSink(BipeSignupEvent.UpdateHandle(it)) },
    )
    // Never "opcional" when the server requires an invite (ADR 0072): an empty field says why a code is needed.
    val inviteCodeIsError = state.inviteCodeError != null || state.isInviteCodeMalformed
    val inviteCodeFooter = state.inviteCodeError
        ?: when {
            state.isInviteCodeMalformed -> stringResource(R.string.bipe_error_invite_invalid)
            state.inviteCodeInput.isBlank() && state.isInviteRequired -> stringResource(R.string.bipe_signup_invite_required)
            else -> null
        }
    TextField(
        label = stringResource(if (state.isInviteRequired) R.string.bipe_signup_invite_code else R.string.bipe_signup_invite_code_optional),
        value = state.inviteCodeInput,
        placeholder = "XXXX-XXXX-XXXX",
        supportingText = inviteCodeFooter,
        validity = if (inviteCodeIsError) TextFieldValidity.Invalid else TextFieldValidity.None,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        onValueChange = { eventSink(BipeSignupEvent.UpdateInviteCode(it)) },
    )
    EmailField(state)
    BipeTermsRow(
        checked = state.isTermsAccepted,
        onCheckedChange = { eventSink(BipeSignupEvent.SetTermsAccepted(it)) },
    )
    Button(
        text = stringResource(CommonStrings.action_continue),
        enabled = state.canContinueForm,
        onClick = { eventSink(BipeSignupEvent.Continue) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
    )
}

@Composable
private fun EmailField(state: BipeSignupState) {
    val emailFooter = state.emailError
        ?: if (state.isEmailAcceptable) stringResource(R.string.bipe_signup_email_optional_footer) else stringResource(R.string.bipe_signup_email_invalid)
    TextField(
        label = stringResource(R.string.bipe_signup_email_optional),
        value = state.email,
        supportingText = emailFooter,
        validity = if (state.emailError != null || !state.isEmailAcceptable) TextFieldValidity.Invalid else TextFieldValidity.None,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        onValueChange = { state.eventSink(BipeSignupEvent.UpdateEmail(it)) },
    )
}

@Composable
private fun ColumnScope.PinStep(state: BipeSignupState, isLoading: Boolean) {
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        iconStyle = BigIcon.Style.Default(CompoundIcons.LockSolid()),
        title = stringResource(R.string.bipe_pin_create_title),
        subTitle = stringResource(R.string.bipe_pin_create_body, BipePin.LENGTH.toString()),
    )
    BipeNewPinFields(state.pinState, enabled = !isLoading)
    Button(
        text = stringResource(CommonStrings.action_continue),
        enabled = state.pinState.isValid && !isLoading,
        showProgress = isLoading,
        onClick = { state.eventSink(BipeSignupEvent.Continue) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
    )
}

@Composable
private fun ColumnScope.FallbackStep(state: BipeSignupState, isLoading: Boolean) {
    val eventSink = state.eventSink
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        iconStyle = BigIcon.Style.Default(CompoundIcons.KeySolid()),
        title = stringResource(R.string.bipe_fallback_title),
        subTitle = stringResource(R.string.bipe_fallback_body),
    )
    if (state.isFallbackEmailOpen) {
        Text(
            text = stringResource(R.string.bipe_fallback_email_body, BipeRegistries.RECOVERY_DELAY_HOURS_EMAIL.toString()),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        EmailField(state)
        Button(
            text = stringResource(R.string.bipe_signup_submit),
            enabled = state.email.isNotBlank() && BipeSignupRules.isPlausibleEmail(state.email.trim()) && !isLoading,
            showProgress = isLoading,
            onClick = { eventSink(BipeSignupEvent.Continue) },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Button(
            text = stringResource(R.string.bipe_fallback_email),
            enabled = !isLoading,
            onClick = { eventSink(BipeSignupEvent.OpenFallbackEmail) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    TextButton(
        text = stringResource(R.string.bipe_fallback_skip),
        enabled = !isLoading,
        showProgress = isLoading && !state.isFallbackEmailOpen,
        onClick = { eventSink(BipeSignupEvent.SkipFallback) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    )
}

@Composable
private fun handleFooter(status: BipeHandleStatus): String = when (status) {
    BipeHandleStatus.Empty, BipeHandleStatus.Unknown -> stringResource(R.string.bipe_signup_handle_footer)
    BipeHandleStatus.Checking -> stringResource(R.string.bipe_handle_checking)
    BipeHandleStatus.Available -> stringResource(R.string.bipe_handle_available)
    is BipeHandleStatus.Problem -> stringResource(status.problem.messageRes, *status.problem.messageArgs)
}
