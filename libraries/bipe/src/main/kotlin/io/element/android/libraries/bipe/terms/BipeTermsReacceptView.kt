/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.terms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Blocking: no close button, no sign-out, Back does nothing. The only way past it is to tick the box and continue.
 * The links in the row open the pages in the browser.
 */
@Composable
fun BipeTermsReacceptView(
    state: BipeTermsReacceptState,
    modifier: Modifier = Modifier,
) {
    BackHandler {}
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                iconStyle = BigIcon.Style.Default(CompoundIcons.Document()),
                title = stringResource(R.string.bipe_terms_reaccept_title),
                subTitle = stringResource(R.string.bipe_terms_reaccept_body),
            )
            BipeTermsRow(
                checked = state.isAccepted,
                onCheckedChange = { state.eventSink(BipeTermsReacceptEvent.SetAccepted(it)) },
                enabled = !state.isSaving,
            )
            if (state.saveAction is AsyncAction.Failure) {
                Text(
                    text = stringResource(R.string.bipe_terms_reaccept_failed),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textCriticalPrimary,
                )
            }
            Button(
                text = stringResource(CommonStrings.action_continue),
                enabled = state.canContinue,
                showProgress = state.isSaving,
                onClick = { state.eventSink(BipeTermsReacceptEvent.Continue) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun BipeTermsReacceptViewPreview(@PreviewParameter(BipeTermsReacceptStatePreviewParam::class) state: BipeTermsReacceptState) = ElementPreview {
    BipeTermsReacceptView(state = state)
}
