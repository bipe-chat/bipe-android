/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.terms

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncAction

open class BipeTermsReacceptStatePreviewParam : PreviewParameterProvider<BipeTermsReacceptState> {
    override val values: Sequence<BipeTermsReacceptState>
        get() = sequenceOf(
            aBipeTermsReacceptState(),
            aBipeTermsReacceptState(isAccepted = true),
            aBipeTermsReacceptState(isAccepted = true, saveAction = AsyncAction.Loading),
            aBipeTermsReacceptState(isAccepted = true, saveAction = AsyncAction.Failure(Exception("network"))),
        )
}

fun aBipeTermsReacceptState(
    isAccepted: Boolean = false,
    saveAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (BipeTermsReacceptEvent) -> Unit = {},
) = BipeTermsReacceptState(
    isAccepted = isAccepted,
    saveAction = saveAction,
    eventSink = eventSink,
)
