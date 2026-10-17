/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.account.PreferencesAccountState
import io.element.android.features.preferences.impl.bipe.BipeSettingsValues
import io.element.android.features.preferences.impl.notifications.SystemNotificationsEnabledProvider
import io.element.android.features.preferences.impl.userstatus.UserStatusState
import io.element.android.features.preferences.impl.utils.ShowDeveloperSettingsProvider
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.user.toMatrixUser
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.pushstore.api.UserPushStoreFactory
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.services.analytics.api.AnalyticsService
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Inject
class PreferencesRootPresenter(
    private val matrixClient: MatrixClient,
    private val analyticsService: AnalyticsService,
    private val versionFormatter: VersionFormatter,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val showDeveloperSettingsProvider: ShowDeveloperSettingsProvider,
    private val featureFlagService: FeatureFlagService,
    private val sessionStore: SessionStore,
    private val appPreferencesStore: AppPreferencesStore,
    private val preferencesAccountPresenter: Presenter<PreferencesAccountState>,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val bipeSession: BipeSession,
    private val userPushStoreFactory: UserPushStoreFactory,
    private val systemNotificationsEnabledProvider: SystemNotificationsEnabledProvider,
    private val buildMeta: BuildMeta,
) : Presenter<PreferencesRootState> {
    @Composable
    override fun present(): PreferencesRootState {
        val coroutineScope = rememberCoroutineScope()
        val matrixUser = matrixClient.userProfile.collectAsState()
        LaunchedEffect(Unit) {
            // Force a refresh of the profile
            matrixClient.getUserProfile()
        }
        val preferencesAccountState = preferencesAccountPresenter.present()
        val isMultiAccountEnabled by remember {
            featureFlagService.isFeatureEnabledFlow(FeatureFlags.MultiAccount)
        }.collectAsState(initial = false)
        val isOtherAccountsSectionExpanded by remember {
            appPreferencesStore.isOtherAccountsExpandedFlow()
        }.collectAsState(initial = true)
        // Bipe: no status row. Status is frozen (ADR 0030, ADR 0112).
        val userStatusState: UserStatusState? = null
        val themeOptions = rememberThemeOptions(featureFlagService, appPreferencesStore)

        val otherSessions by remember {
            sessionStore.sessionsFlow().map { list ->
                list
                    .filter { it.userId != matrixClient.sessionId.value }
                    .map { it.toMatrixUser() }
                    .toImmutableList()
            }
        }.collectAsState(initial = persistentListOf())

        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
        val hasAnalyticsProviders = remember { analyticsService.getAvailableAnalyticsProviders().isNotEmpty() }

        val showLabsItem = remember { featureFlagService.getAvailableFeatures(isInLabs = true).isNotEmpty() }

        val showDeveloperSettings by showDeveloperSettingsProvider.showDeveloperSettings.collectAsState()

        fun handleEvent(event: PreferencesRootEvent) {
            when (event) {
                is PreferencesRootEvent.OnVersionInfoClick -> {
                    showDeveloperSettingsProvider.unlockDeveloperSettings(coroutineScope)
                }
                is PreferencesRootEvent.SwitchToSession -> coroutineScope.launch {
                    sessionStore.setLatestSession(event.sessionId.value)
                }
                PreferencesRootEvent.ToggleOtherAccountsExpanded -> coroutineScope.launch {
                    appPreferencesStore.setOtherAccountsExpanded(!isOtherAccountsSectionExpanded)
                }
                is PreferencesRootEvent.SetTheme -> sessionCoroutineScope.launch {
                    appPreferencesStore.setTheme(event.theme)
                }
            }
        }

        // Bipe: the handle row. The resolver looks the handle up and emits when it arrives.
        val bipeHandles by bipeSession.handleResolver.handles.collectAsState()
        val bipeHandle = remember(bipeHandles) { bipeSession.handleResolver.handle(matrixClient.sessionId.value) }
        // Bipe: on or off for the public profile row. The profile screen stores every change it saves.
        val bipeMyProfile by bipeSession.publicProfiles.myProfile.collectAsState()
        LaunchedEffect(Unit) { bipeSession.publicProfiles.refreshMine() }

        // Bipe: the subtitles of the group rows (ADR 0112).
        val bipeWhoCanMessage by bipeSession.privacy.messaging.collectAsState()
        LaunchedEffect(Unit) { bipeSession.privacy.refreshMessaging() }
        val bipeWaysBack = BipeSettingsValues.waysBack(bipeSession, matrixClient)
        val bipeNotificationsOn = BipeSettingsValues.notificationsOn(userPushStoreFactory, systemNotificationsEnabledProvider, matrixClient)

        return PreferencesRootState(
            myUser = matrixUser.value,
            userStatusState = userStatusState,
            preferencesAccountState = preferencesAccountState,
            theme = themeOptions.selected,
            availableThemeOptions = themeOptions.available,
            version = remember { versionFormatter.get() },
            isMultiAccountEnabled = isMultiAccountEnabled,
            isOtherAccountsSectionExpanded = isOtherAccountsSectionExpanded,
            otherSessions = otherSessions,
            showAnalyticsSettings = hasAnalyticsProviders,
            showDeveloperSettings = showDeveloperSettings,
            showLabsItem = showLabsItem,
            snackbarMessage = snackbarMessage,
            bipeHandle = bipeHandle,
            bipePublicProfileEnabled = bipeMyProfile?.enabled,
            bipeWhoCanMessage = bipeWhoCanMessage,
            bipeWaysBackCount = bipeWaysBack?.size,
            bipeNotificationsOn = bipeNotificationsOn,
            bipeDeviceId = matrixClient.deviceId.value,
            bipeIsDeveloperBuild = buildMeta.buildType != BuildType.RELEASE,
            eventSink = ::handleEvent,
        )
    }
}
