/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.bipesignup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.bipe.account.ui.BipePinField
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
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

/**
 * Port of BipeSignupScreen on iOS: device-first sign-up (ADR 0057), one question per screen (bipe-chat/bipe#196).
 * The user never sees or types a password.
 */
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
        if (state.hasPreviousStep) eventSink(BipeSignupEvent.Back) else onBackClick()
    }
    BackHandler(enabled = state.hasPreviousStep && !isLoading) { back() }

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
                BipeSignupStep.INVITE -> InviteStep(state)
                BipeSignupStep.NAME -> NameStep(state)
                BipeSignupStep.PIN -> PinStep(state)
                BipeSignupStep.PIN_CONFIRMATION -> PinConfirmationStep(state, isLoading)
            }
        }

        AsyncActionView(
            async = state.createAction,
            onSuccess = {},
            progressDialog = { AsyncActionViewDefaults.ProgressDialog(stringResource(R.string.bipe_signup_creating)) },
            errorMessage = { it.message.orEmpty() },
            onErrorDismiss = { eventSink(BipeSignupEvent.ClearError) },
        )
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, subTitle: String?) {
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        iconStyle = BigIcon.Style.Default(icon),
        title = title,
        subTitle = subTitle,
    )
}

@Composable
private fun InviteStep(state: BipeSignupState) {
    val eventSink = state.eventSink
    StepHeader(
        icon = CompoundIcons.Email(),
        title = stringResource(R.string.bipe_signup_invite_title),
        subTitle = stringResource(R.string.bipe_signup_invite_body),
    )
    val inviteCodeFooter = state.inviterHandle?.let { stringResource(R.string.bipe_signup_invited_by, it) }
        ?: state.inviteCodeError
        ?: if (state.inviteCodeFooterIsInvalid) stringResource(R.string.bipe_error_invite_invalid) else null
    TextField(
        label = stringResource(R.string.bipe_signup_invite_code),
        value = state.inviteCodeInput,
        placeholder = "XXXX-XXXX-XXXX",
        supportingText = inviteCodeFooter,
        validity = if (state.inviteCodeFooterIsInvalid) TextFieldValidity.Invalid else TextFieldValidity.None,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { eventSink(BipeSignupEvent.ContinueFromInvite) }),
        onValueChange = { eventSink(BipeSignupEvent.UpdateInviteCode(it)) },
    )
    Button(
        text = stringResource(CommonStrings.action_continue),
        enabled = state.canContinueFromInvite,
        onClick = { eventSink(BipeSignupEvent.ContinueFromInvite) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    if (state.showsNoInviteHelp) {
        Text(
            text = stringResource(R.string.bipe_signup_invite_none_body),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    } else {
        TextButton(
            text = stringResource(R.string.bipe_signup_invite_none),
            onClick = { eventSink(BipeSignupEvent.ShowNoInviteHelp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun NameStep(state: BipeSignupState) {
    val eventSink = state.eventSink
    StepHeader(
        icon = CompoundIcons.UserProfileSolid(),
        title = stringResource(R.string.bipe_signup_name_title),
        subTitle = state.inviterHandle?.let { stringResource(R.string.bipe_signup_invited_by, it) },
    )
    TextField(
        label = stringResource(R.string.bipe_signup_display_name),
        value = state.displayName,
        supportingText = stringResource(R.string.bipe_signup_display_name_footer),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { eventSink(BipeSignupEvent.ContinueFromName) }),
        onValueChange = { eventSink(BipeSignupEvent.UpdateDisplayName(it)) },
    )
    Button(
        text = stringResource(CommonStrings.action_continue),
        enabled = state.canContinueFromName,
        onClick = { eventSink(BipeSignupEvent.ContinueFromName) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
    )
}

@Composable
private fun PinStep(state: BipeSignupState) {
    StepHeader(
        icon = CompoundIcons.LockSolid(),
        title = stringResource(R.string.bipe_pin_create_title),
        subTitle = stringResource(R.string.bipe_pin_create_body, BipePin.LENGTH.toString()),
    )
    BipePinField(
        value = state.pinState.pin,
        onValueChange = { state.eventSink(BipeSignupEvent.UpdatePin(it)) },
        label = stringResource(R.string.bipe_pin_create_title),
        error = state.pinError ?: state.pinState.problemRes?.let { stringResource(it, BipePin.LENGTH.toString()) },
        hint = stringResource(R.string.bipe_pin_never_asked),
    )
}

@Composable
private fun PinConfirmationStep(state: BipeSignupState, isLoading: Boolean) {
    StepHeader(
        icon = CompoundIcons.LockSolid(),
        title = stringResource(R.string.bipe_pin_confirm_title),
        subTitle = null,
    )
    BipePinField(
        value = state.pinState.confirmation,
        onValueChange = { state.eventSink(BipeSignupEvent.UpdatePinConfirmation(it)) },
        label = stringResource(R.string.bipe_pin_confirm_title),
        error = if (state.pinMismatch) stringResource(R.string.bipe_pin_mismatch) else null,
        enabled = !isLoading,
        hint = stringResource(R.string.bipe_pin_never_asked),
    )
}
