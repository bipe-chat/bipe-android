/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.biperecovery

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.BipeSignupRules
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipePin
import io.element.android.libraries.bipe.account.ui.BipeNewPinFields
import io.element.android.libraries.bipe.account.ui.BipePinField
import io.element.android.libraries.bipe.account.ui.formatTime
import io.element.android.libraries.bipe.api.BipeRecoveryPurpose
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.modifiers.clearFocusOnTap
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings
import java.time.Instant

/** Getting back into the account on a new phone (ADR 0057, ADR 0059). */
@Composable
fun BipeRecoveryView(
    state: BipeRecoveryScreenState,
    onBackClick: () -> Unit,
    onSignInWithQrCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val isLoading = state.action is AsyncAction.Loading
    Scaffold(
        modifier = modifier.clearFocusOnTap(focusManager),
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.bipe_recovery_title),
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.mode) {
                BipeRecoveryMode.CHOOSE -> ChooseContent(state, isLoading, onSignInWithQrCode)
                BipeRecoveryMode.LINK_PIN -> LinkPinContent(state, isLoading)
                BipeRecoveryMode.LINK_NEW_PIN -> LinkNewPinContent(state, isLoading)
                BipeRecoveryMode.WAITING -> WaitingContent(state)
            }
        }
        AsyncActionView(
            async = state.action,
            onSuccess = {},
            errorMessage = { it.message.orEmpty() },
            onErrorDismiss = { state.eventSink(BipeRecoveryEvent.ClearError) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = ElementTheme.typography.fontHeadingSmMedium, color = ElementTheme.colors.textPrimary)
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textSecondary)
}

@Composable
private fun ChooseContent(state: BipeRecoveryScreenState, isLoading: Boolean, onSignInWithQrCode: () -> Unit) {
    val eventSink = state.eventSink
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        iconStyle = BigIcon.Style.Default(CompoundIcons.KeySolid()),
        title = stringResource(R.string.bipe_recovery_title),
        subTitle = state.notice,
    )
    if (state.hasToken) {
        Body(stringResource(R.string.bipe_recovery_token_found))
        BipePinField(
            value = state.pin,
            onValueChange = { eventSink(BipeRecoveryEvent.UpdatePin(it)) },
            label = stringResource(R.string.bipe_pin_enter_title),
            error = state.pinError,
            enabled = !isLoading,
            hint = stringResource(R.string.bipe_pin_never_asked),
        )
        Button(
            text = stringResource(CommonStrings.action_continue),
            enabled = state.pin.length == BipePin.LENGTH && !isLoading,
            onClick = { eventSink(BipeRecoveryEvent.SubmitTokenPin) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            text = stringResource(R.string.bipe_pin_forgot),
            onClick = { eventSink(BipeRecoveryEvent.ForgotPin) },
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
    }

    SectionTitle(stringResource(R.string.bipe_recovery_qr))
    Body(stringResource(R.string.bipe_recovery_qr_body))
    Body(stringResource(R.string.bipe_recovery_qr_never_from_others))
    OutlinedButton(
        text = stringResource(R.string.bipe_recovery_qr),
        onClick = onSignInWithQrCode,
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider()

    SectionTitle(stringResource(R.string.bipe_recovery_email))
    Body(stringResource(R.string.bipe_recovery_email_body, BipeRegistries.RECOVERY_DELAY_HOURS_EMAIL.toString()))
    if (state.isEmailOpen || !state.hasToken) {
        TextField(
            label = stringResource(R.string.bipe_recovery_email_field),
            value = state.email,
            supportingText = if (state.isEmailSent) stringResource(R.string.bipe_recovery_email_sent) else null,
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            onValueChange = { eventSink(BipeRecoveryEvent.UpdateEmail(it)) },
        )
        OutlinedButton(
            text = stringResource(CommonStrings.action_send),
            enabled = BipeSignupRules.isPlausibleEmail(state.email.trim()) && !state.isEmailSent && !isLoading,
            onClick = { eventSink(BipeRecoveryEvent.SendEmail) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    } else {
        OutlinedButton(
            text = stringResource(R.string.bipe_recovery_email),
            onClick = { eventSink(BipeRecoveryEvent.OpenEmail) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun LinkPinContent(state: BipeRecoveryScreenState, isLoading: Boolean) {
    val eventSink = state.eventSink
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        iconStyle = BigIcon.Style.Default(CompoundIcons.LockSolid()),
        title = stringResource(R.string.bipe_pin_enter_title),
        subTitle = stringResource(R.string.bipe_recovery_email_body, BipeRegistries.RECOVERY_DELAY_HOURS_EMAIL.toString()),
    )
    BipePinField(
        value = state.pin,
        onValueChange = { eventSink(BipeRecoveryEvent.UpdatePin(it)) },
        label = null,
        error = state.pinError,
        enabled = !isLoading,
        hint = stringResource(R.string.bipe_pin_never_asked),
    )
    Button(
        text = stringResource(CommonStrings.action_continue),
        enabled = state.pin.length == BipePin.LENGTH && !isLoading,
        onClick = { eventSink(BipeRecoveryEvent.SubmitLinkPin) },
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(
        text = stringResource(R.string.bipe_pin_forgot),
        onClick = { eventSink(BipeRecoveryEvent.ForgotPin) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LinkNewPinContent(state: BipeRecoveryScreenState, isLoading: Boolean) {
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        iconStyle = BigIcon.Style.Default(CompoundIcons.LockSolid()),
        title = stringResource(R.string.bipe_recovery_new_pin_title),
        subTitle = stringResource(R.string.bipe_pin_create_body, BipePin.LENGTH.toString()),
    )
    BipeNewPinFields(state.newPinState, enabled = !isLoading)
    state.pinError?.let { Body(it) }
    Button(
        text = stringResource(CommonStrings.action_continue),
        enabled = state.newPinState.isValid && !isLoading,
        onClick = { state.eventSink(BipeRecoveryEvent.SubmitNewPin) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WaitingContent(state: BipeRecoveryScreenState) {
    val claim = state.pendingClaim
    val readyAt = formatTime(claim?.readyAtMillis?.let(Instant::ofEpochMilli))
    val isPinReset = claim?.purposeId == BipeRecoveryPurpose.PIN_RESET.id
    IconTitleSubtitleMolecule(
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        iconStyle = BigIcon.Style.Default(CompoundIcons.Time()),
        title = stringResource(R.string.bipe_recovery_waiting_title),
        subTitle = state.notice ?: if (isPinReset) {
            stringResource(R.string.bipe_recovery_waiting_pin_body, readyAt)
        } else {
            stringResource(R.string.bipe_recovery_waiting_body, readyAt)
        },
    )
    if (!isPinReset) Body(stringResource(R.string.bipe_recovery_history_note))
}
