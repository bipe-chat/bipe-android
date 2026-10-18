/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.bipe

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.preferences.impl.moderation.MediaPreviewConfigStateStore
import io.element.android.features.preferences.impl.root.VersionFormatter
import io.element.android.features.preferences.impl.root.rememberThemeOptions
import io.element.android.features.preferences.impl.root.setTheme
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.bipe.generated.BipeSettingsGroup
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.element.android.libraries.bipe.R as BipeR

@AssistedInject
class BipeSettingsGroupPresenter(
    @Assisted private val group: BipeSettingsGroup,
    private val matrixClient: MatrixClient,
    private val bipeSession: BipeSession,
    private val featureFlagService: FeatureFlagService,
    private val appPreferencesStore: AppPreferencesStore,
    private val mediaPreviewConfigStateStore: MediaPreviewConfigStateStore,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val versionFormatter: VersionFormatter,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) : Presenter<BipeSettingsGroupState> {
    @AssistedFactory
    interface Factory {
        fun create(group: BipeSettingsGroup): BipeSettingsGroupPresenter
    }

    @Composable
    override fun present(): BipeSettingsGroupState {
        val myUser by matrixClient.userProfile.collectAsState()
        val handles by bipeSession.handleResolver.handles.collectAsState()
        val handle = remember(handles) { bipeSession.handleResolver.handle(matrixClient.sessionId.value) }
        val myProfile by bipeSession.publicProfiles.myProfile.collectAsState()
        LaunchedEffect(Unit) {
            if (group == BipeSettingsGroup.PROFILE) bipeSession.publicProfiles.refreshMine()
        }

        // The registry flags (`flag:<name>`) and the app feature flags they stand for.
        val isLinkNewDeviceEnabled by remember { featureFlagService.isFeatureEnabledFlow(FeatureFlags.QrCodeLogin) }.collectAsState(initial = false)
        val enabledFlags = remember(isLinkNewDeviceEnabled) {
            buildSet { if (isLinkNewDeviceEnabled) add(FLAG_LINK_NEW_DEVICE) }.toImmutableSet()
        }

        val themeOptions = rememberThemeOptions(featureFlagService, appPreferencesStore)
        val mediaPreviewConfig = mediaPreviewConfigStateStore.state()
        val recoveryState by matrixClient.encryptionService.recoveryStateStateFlow.collectAsState()

        // Sua conta (#360)
        val waysBack = if (group == BipeSettingsGroup.ACCOUNT) BipeSettingsValues.waysBack(bipeSession, matrixClient) else null
        var hasSeparateDevicePin by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (group == BipeSettingsGroup.ACCOUNT) hasSeparateDevicePin = bipeSession.account.hasSeparateDevicePin()
        }

        // Privacidade (#359)
        val whoCanMessage by bipeSession.privacy.messaging.collectAsState()
        val discoverableByPhone by bipeSession.privacy.discoverableByPhone.collectAsState()
        val phone by bipeSession.privacy.phone.collectAsState()
        val blockedUsers by matrixClient.ignoredUsersFlow.collectAsState()
        val sharePresence by remember { sessionPreferencesStore.isSharePresenceEnabled() }.collectAsState(initial = true)
        LaunchedEffect(Unit) {
            if (group == BipeSettingsGroup.PRIVACY) {
                bipeSession.privacy.refreshMessaging()
                bipeSession.privacy.refreshDiscovery()
            }
        }

        fun handleEvent(event: BipeSettingsGroupEvent) {
            when (event) {
                is BipeSettingsGroupEvent.SetTheme -> sessionCoroutineScope.launch { appPreferencesStore.setTheme(event.theme) }
                is BipeSettingsGroupEvent.SetMediaPreview -> mediaPreviewConfigStateStore.setTimelineMediaPreviewValue(event.value)
                is BipeSettingsGroupEvent.SetHideInviteAvatars -> mediaPreviewConfigStateStore.setHideInviteAvatars(event.hide)
                is BipeSettingsGroupEvent.SetDiscoverableByPhone -> sessionCoroutineScope.launch {
                    bipeSession.privacy.setDiscoverableByPhone(event.value)
                        .onFailure { snackbarDispatcher.post(SnackbarMessage(BipeR.string.bipe_error_generic)) }
                }
                is BipeSettingsGroupEvent.SetSharePresence -> sessionCoroutineScope.launch { sessionPreferencesStore.setSharePresence(event.value) }
            }
        }

        return BipeSettingsGroupState(
            group = group,
            myUser = myUser,
            handle = handle,
            publicProfileEnabled = myProfile?.enabled,
            enabledFlags = enabledFlags,
            theme = themeOptions.selected,
            availableThemeOptions = themeOptions.available,
            mediaPreviewValue = mediaPreviewConfig.timelineMediaPreviewValue,
            recoveryKeyEnabled = when (recoveryState) {
                RecoveryState.WAITING_FOR_SYNC, RecoveryState.UNKNOWN -> null
                RecoveryState.ENABLED -> true
                RecoveryState.DISABLED, RecoveryState.INCOMPLETE -> false
            },
            deviceName = remember { listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ") },
            waysBack = waysBack?.toImmutableList(),
            whoCanMessage = whoCanMessage,
            hideInviteAvatars = mediaPreviewConfig.hideInviteAvatars,
            blockedUsersCount = blockedUsers.size,
            phoneLinked = phone?.linked,
            discoverableByPhone = discoverableByPhone,
            sharePresence = sharePresence,
            version = remember { versionFormatter.get() },
            deviceId = matrixClient.deviceId.value,
            eventSink = ::handleEvent,
            hasSeparateDevicePin = hasSeparateDevicePin,
        )
    }

    companion object {
        /** `flag:linkNewDevice` in the settings registry: [FeatureFlags.QrCodeLogin]. */
        const val FLAG_LINK_NEW_DEVICE = "linkNewDevice"
    }
}
