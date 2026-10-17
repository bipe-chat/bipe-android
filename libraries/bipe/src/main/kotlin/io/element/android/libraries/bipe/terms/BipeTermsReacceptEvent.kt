/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.terms

sealed interface BipeTermsReacceptEvent {
    data class SetAccepted(val accepted: Boolean) : BipeTermsReacceptEvent
    data object Continue : BipeTermsReacceptEvent
}
