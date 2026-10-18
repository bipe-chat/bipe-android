/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.bipesignup

import android.widget.Toast
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.BipeSignupRules
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.bipe.account.ui.BipeNewPinFields
import io.element.android.libraries.bipe.generated.BipeOnboarding
import io.element.android.libraries.bipe.generated.BipeOnboardingStep
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.libraries.bipe.terms.BipeTermsRow
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.modifiers.clearFocusOnTap
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar

/**
 * Port of BipeSignupScreen on iOS: device-first sign-up (ADR 0057) in the steps of bipe-shared `registries/onboarding.json`
 * (bipe-chat/bipe#415). The user never sees or types a password.
 */
@Composable
fun BipeSignupView(
    state: BipeSignupState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val eventSink = state.eventSink
    val isLoading = state.createAction is AsyncAction.Loading
    fun back() {
        if (state.canGoBack) eventSink(BipeSignupEvent.Back) else onBackClick()
    }
    BackHandler(enabled = state.canGoBack && !isLoading) { back() }

    Scaffold(
        modifier = modifier.clearFocusOnTap(focusManager),
        topBar = {
            TopAppBar(
                titleStr = "",
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
            Header(state)
            when (state.step) {
                BipeOnboardingStep.INVITE_CODE -> InviteCodeStep(state)
                BipeOnboardingStep.NAME -> NameStep(state, isLoading)
                BipeOnboardingStep.PIN -> PinStep(state, isLoading)
                BipeOnboardingStep.FALLBACK -> FallbackStep(state, isLoading)
            }
        }

        AsyncActionView(
            async = state.createAction,
            onSuccess = {
                Toast.makeText(context, context.getString(BipeOnboarding.created), Toast.LENGTH_LONG).show()
            },
            errorMessage = { it.message.orEmpty() },
            onErrorDismiss = { eventSink(BipeSignupEvent.ClearError) },
        )
        if (state.isWaitlistOpen) {
            WaitlistSheet(state)
        }
    }
}

@Composable
private fun Header(state: BipeSignupState) {
    val (icon, subTitle) = when (state.step) {
        BipeOnboardingStep.INVITE_CODE -> CompoundIcons.UserAddSolid() to stringResource(R.string.bipe_invite_code_body)
        BipeOnboardingStep.NAME -> CompoundIcons.UserProfileSolid() to state.inviterHandle?.let { stringResource(R.string.bipe_signup_invited_by, it) }
        BipeOnboardingStep.PIN -> CompoundIcons.LockSolid() to stringResource(R.string.bipe_pin_create_body, BipePin.LENGTH.toString())
        BipeOnboardingStep.FALLBACK -> CompoundIcons.KeySolid() to stringResource(R.string.bipe_fallback_body)
    }
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        iconStyle = BigIcon.Style.Default(icon),
        title = stringResource(state.step.title),
        subTitle = subTitle,
    )
}

@Composable
private fun ColumnScope.InviteCodeStep(state: BipeSignupState) {
    val eventSink = state.eventSink
    val isError = state.inviteCodeError != null || state.isInviteCodeMalformed
    val footer = state.inviteCodeError
        ?: when {
            state.isInviteCodeMalformed -> stringResource(R.string.bipe_error_invite_invalid)
            state.inviterHandle != null -> stringResource(R.string.bipe_signup_invited_by, state.inviterHandle)
            else -> null
        }
    TextField(
        label = stringResource(R.string.bipe_invite_code_title),
        value = state.inviteCodeInput,
        placeholder = "XXXX-XXXX-XXXX",
        supportingText = footer,
        validity = when {
            isError -> TextFieldValidity.Invalid
            state.inviterHandle != null -> TextFieldValidity.Valid
            else -> TextFieldValidity.None
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        onValueChange = { eventSink(BipeSignupEvent.UpdateInviteCode(it)) },
    )
    StepButtons(state, isPrimaryEnabled = state.canContinueInviteCode, isLoading = false)
}

@Composable
private fun ColumnScope.NameStep(state: BipeSignupState, isLoading: Boolean) {
    val eventSink = state.eventSink
    TextField(
        label = stringResource(R.string.bipe_signup_display_name),
        value = state.displayName,
        supportingText = stringResource(R.string.bipe_signup_display_name_footer),
        singleLine = true,
        enabled = !isLoading,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        onValueChange = { eventSink(BipeSignupEvent.UpdateDisplayName(it)) },
    )
    BipeTermsRow(
        checked = state.isTermsAccepted,
        onCheckedChange = { eventSink(BipeSignupEvent.SetTermsAccepted(it)) },
    )
    StepButtons(state, isPrimaryEnabled = state.canContinueName, isLoading = isLoading)
}

@Composable
private fun ColumnScope.PinStep(state: BipeSignupState, isLoading: Boolean) {
    BipeNewPinFields(state.pinState, enabled = !isLoading)
    StepButtons(state, isPrimaryEnabled = state.pinState.isValid, isLoading = isLoading)
}

@Composable
private fun ColumnScope.FallbackStep(state: BipeSignupState, isLoading: Boolean) {
    val eventSink = state.eventSink
    if (state.isFallbackEmailOpen) {
        Text(
            text = stringResource(R.string.bipe_fallback_email_body, BipeRegistries.RECOVERY_DELAY_HOURS_EMAIL.toString()),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        val isEmailError = state.emailError != null || state.email.isNotBlank() && !BipeSignupRules.isPlausibleEmail(state.email.trim())
        TextField(
            label = stringResource(R.string.bipe_signup_email),
            value = state.email,
            supportingText = state.emailError ?: if (isEmailError) stringResource(R.string.bipe_signup_email_invalid) else null,
            validity = if (isEmailError) TextFieldValidity.Invalid else TextFieldValidity.None,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            onValueChange = { eventSink(BipeSignupEvent.UpdateEmail(it)) },
        )
        Button(
            text = stringResource(R.string.bipe_signup_submit),
            enabled = state.canSubmitFallbackEmail && !isLoading,
            showProgress = isLoading,
            onClick = { eventSink(BipeSignupEvent.Primary) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButtons(state, isLoading)
    } else {
        StepButtons(state, isPrimaryEnabled = true, isLoading = isLoading)
    }
}

/** The step's buttons from the registry: one filled button, then its text buttons. */
@Composable
private fun StepButtons(state: BipeSignupState, isPrimaryEnabled: Boolean, isLoading: Boolean) {
    Button(
        text = stringResource(state.step.primary.title),
        enabled = isPrimaryEnabled && !isLoading,
        showProgress = isLoading,
        onClick = { state.eventSink(BipeSignupEvent.Primary) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    TextButtons(state, isLoading)
}

@Composable
private fun TextButtons(state: BipeSignupState, isLoading: Boolean) {
    state.step.text.forEach { button ->
        TextButton(
            text = stringResource(button.title),
            enabled = !isLoading,
            onClick = { state.eventSink(BipeSignupEvent.Text(button.target)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaitlistSheet(state: BipeSignupState) {
    val eventSink = state.eventSink
    ModalBottomSheet(onDismissRequest = { eventSink(BipeSignupEvent.CloseWaitlist) }, scrollable = true) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(BipeOnboarding.waitlistBody),
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
            )
            if (state.waitlistAction is AsyncAction.Success) {
                Text(
                    text = stringResource(BipeOnboarding.waitlistDone),
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = ElementTheme.colors.textSuccessPrimary,
                )
            } else {
                val failure = state.waitlistAction as? AsyncAction.Failure
                TextField(
                    label = stringResource(R.string.bipe_signup_email),
                    value = state.waitlistEmail,
                    supportingText = failure?.error?.message,
                    validity = if (failure != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                    onValueChange = { eventSink(BipeSignupEvent.UpdateWaitlistEmail(it)) },
                )
                Button(
                    text = stringResource(BipeOnboarding.waitlistPrimary.title),
                    enabled = state.canJoinWaitlist && state.waitlistAction !is AsyncAction.Loading,
                    showProgress = state.waitlistAction is AsyncAction.Loading,
                    onClick = { eventSink(BipeSignupEvent.JoinWaitlist) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
