/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.terms

import io.element.android.libraries.architecture.AsyncAction

/** The blocking re-accept screen (ADR 0113, bipe#366). */
data class BipeTermsReacceptState(
    val isAccepted: Boolean,
    val saveAction: AsyncAction<Unit>,
    val eventSink: (BipeTermsReacceptEvent) -> Unit,
) {
    val isSaving: Boolean get() = saveAction is AsyncAction.Loading
    val canContinue: Boolean get() = isAccepted && !isSaving
}
