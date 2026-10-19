/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.weblink

import kotlinx.collections.immutable.ImmutableList

data class BipeLinkedDevicesState(
    val isLoading: Boolean,
    val isCheckingSupport: Boolean,
    val showsLinkComputer: Boolean,
    /** The other devices of the account, newest use first. This phone is not in it. */
    val devices: ImmutableList<BipeMatrixDevice>,
    val loadFailed: Boolean,
    val notSupported: Boolean,
    /** This phone has no hidden password for the account (a beta account): the PIN comes first (ADR 0057). */
    val needsPin: Boolean,
    val showsCreatePin: Boolean,
    val createPinError: String?,
    /** The device that the sign-out dialog asks about. */
    val signOutCandidate: BipeMatrixDevice?,
    val eventSink: (BipeLinkedDevicesEvent) -> Unit,
)

sealed interface BipeLinkedDevicesEvent {
    data object LinkComputer : BipeLinkedDevicesEvent
    data class CreatePin(val pin: String) : BipeLinkedDevicesEvent
    data class AskSignOut(val device: BipeMatrixDevice) : BipeLinkedDevicesEvent
    data object ConfirmSignOut : BipeLinkedDevicesEvent
    data object DismissDialog : BipeLinkedDevicesEvent
}
