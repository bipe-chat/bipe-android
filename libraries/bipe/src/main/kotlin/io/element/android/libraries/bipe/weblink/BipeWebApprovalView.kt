/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.weblink

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.ui.BipeBiometricCheck
import io.element.android.libraries.designsystem.atomic.pages.FlowStepPage
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

/** The full-access warning with its cooldown, then the private MAS web view (ADR 0118). */
@Composable
fun BipeWebApprovalView(
    controller: BipeWebApprovalController,
    modifier: Modifier = Modifier,
) {
    val step by controller.step.collectAsState()
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val biometricTitle = stringResource(
        if (controller.purpose is BipeWebApprovalController.Purpose.ApproveSignIn) R.string.bipe_link_computer_biometric else R.string.bipe_linked_devices_sign_out_biometric
    )

    fun verifyOwner() {
        scope.launch {
            val passed = activity != null && BipeBiometricCheck.isAvailable(activity) &&
                BipeBiometricCheck.authenticate(activity, title = biometricTitle, subtitle = "")
            controller.onOwnerVerified(passed)
        }
    }

    // Sign-out has no warning: the biometric check comes first.
    DisposableEffect(controller) {
        if (controller.purpose is BipeWebApprovalController.Purpose.SignOut) verifyOwner()
        onDispose { controller.end() }
    }
    BackHandler(enabled = step != BipeWebApprovalController.Step.FINISHING) { controller.close() }

    when (step) {
        BipeWebApprovalController.Step.WARNING -> Warning(controller = controller, onContinue = ::verifyOwner, modifier = modifier)
        BipeWebApprovalController.Step.FAILED -> FlowStepPage(
            modifier = modifier,
            iconStyle = BigIcon.Style.AlertSolid,
            title = stringResource(R.string.bipe_link_computer_error_title),
            subTitle = stringResource(R.string.bipe_link_computer_error_sign_in),
            onBackClick = controller::close,
            buttons = { Button(text = stringResource(CommonStrings.action_close), onClick = controller::close, modifier = Modifier.fillMaxWidth()) },
        )
        else -> Page(controller = controller, step = step, modifier = modifier)
    }
}

@Composable
private fun Warning(
    controller: BipeWebApprovalController,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondsLeft by controller.secondsLeft.collectAsState()
    FlowStepPage(
        modifier = modifier,
        iconStyle = BigIcon.Style.AlertSolid,
        title = stringResource(R.string.bipe_link_computer_warning_title),
        subTitle = stringResource(R.string.bipe_link_computer_warning_body),
        onBackClick = controller::close,
        buttons = {
            Button(
                text = if (secondsLeft > 0) {
                    stringResource(R.string.bipe_pause_countdown, secondsLeft.toString())
                } else {
                    stringResource(R.string.bipe_link_computer_continue)
                },
                enabled = secondsLeft == 0,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(text = stringResource(CommonStrings.action_cancel), onClick = controller::close, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Text(
            text = stringResource(R.string.bipe_link_computer_warning_scam),
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textCriticalPrimary,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

@Composable
private fun Page(
    controller: BipeWebApprovalController,
    step: BipeWebApprovalController.Step,
    modifier: Modifier = Modifier,
) {
    val isConsentPage by controller.isConsentPage.collectAsState()
    val place by controller.place.collectAsState()
    val isSignOut = controller.purpose is BipeWebApprovalController.Purpose.SignOut
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault)
            .systemBarsPadding(),
    ) {
        TextButton(
            text = stringResource(if (isSignOut) CommonStrings.action_done else CommonStrings.action_cancel),
            onClick = controller::close,
            enabled = step != BipeWebApprovalController.Step.FINISHING,
        )
        if (isConsentPage) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElementTheme.colors.bgSubtleSecondary)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = place?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.bipe_link_computer_place, it) }
                        ?: stringResource(R.string.bipe_link_computer_place_unknown),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.bipe_link_computer_consent_hint),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
                Text(
                    text = stringResource(R.string.bipe_link_computer_place_source),
                    style = ElementTheme.typography.fontBodyXsRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context -> WebView(context).also(controller::attach) },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (step == BipeWebApprovalController.Step.PAGE) 1f else 0f),
            )
            if (step != BipeWebApprovalController.Step.PAGE) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.bipe_link_computer_loading),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}
