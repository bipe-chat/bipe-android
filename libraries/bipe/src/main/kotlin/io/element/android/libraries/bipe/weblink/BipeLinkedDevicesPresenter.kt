/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.weblink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.element.android.libraries.bipe.di.BipeSession
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Settings > Linked devices (ADR 0116): the other devices of the account with their last use and a sign-out, and the
 * only entry point to the QR scanner that links a computer.
 */
class BipeLinkedDevicesPresenter(
    private val bipeSession: BipeSession,
    /** [io.element.android.libraries.featureflag.api.FeatureFlags.QrCodeLogin], on by default. */
    private val showsLinkComputer: Flow<Boolean>,
    private val onLinkComputer: () -> Unit,
    private val onSignOut: (deviceId: String) -> Unit,
) {
    @Composable
    fun present(): BipeLinkedDevicesState {
        val scope = rememberCoroutineScope()
        val webLink = bipeSession.webLink
        val changes by webLink.changes.collectAsState()
        val showsLinkComputer by showsLinkComputer.collectAsState(initial = false)
        var isLoading by remember { mutableStateOf(true) }
        var loadFailed by remember { mutableStateOf(false) }
        var devices by remember { mutableStateOf<ImmutableList<BipeMatrixDevice>>(persistentListOf()) }
        var isCheckingSupport by remember { mutableStateOf(false) }
        var notSupported by remember { mutableStateOf(false) }
        var signOutCandidate by remember { mutableStateOf<BipeMatrixDevice?>(null) }

        LaunchedEffect(changes) {
            webLink.otherDevices()
                .onSuccess { devices = it.toImmutableList() }
                .onFailure { loadFailed = true }
            isLoading = false
        }

        fun handleEvent(event: BipeLinkedDevicesEvent) {
            when (event) {
                BipeLinkedDevicesEvent.LinkComputer -> {
                    if (isCheckingSupport) return
                    isCheckingSupport = true
                    scope.launch {
                        val supported = webLink.isSupported()
                        isCheckingSupport = false
                        if (supported) onLinkComputer() else notSupported = true
                    }
                }
                is BipeLinkedDevicesEvent.AskSignOut -> signOutCandidate = event.device
                BipeLinkedDevicesEvent.ConfirmSignOut -> {
                    signOutCandidate?.let { onSignOut(it.deviceId) }
                    signOutCandidate = null
                }
                BipeLinkedDevicesEvent.DismissDialog -> {
                    signOutCandidate = null
                    notSupported = false
                    loadFailed = false
                }
            }
        }

        return BipeLinkedDevicesState(
            isLoading = isLoading,
            isCheckingSupport = isCheckingSupport,
            showsLinkComputer = showsLinkComputer,
            devices = devices,
            loadFailed = loadFailed,
            notSupported = notSupported,
            signOutCandidate = signOutCandidate,
            eventSink = ::handleEvent,
        )
    }
}
