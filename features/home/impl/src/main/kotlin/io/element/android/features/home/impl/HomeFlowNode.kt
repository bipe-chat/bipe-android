/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import android.app.Activity
import android.os.Parcelable
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.node.node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.MobileScreen
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.home.api.HomeEntryPoint
import io.element.android.features.home.impl.components.RoomListMenuAction
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.invite.api.InviteData
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteView
import io.element.android.features.invite.api.declineandblock.DeclineInviteAndBlockEntryPoint
import io.element.android.features.leaveroom.api.LeaveRoomRenderer
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.features.preferences.impl.root.PreferencesRootPresenter
import io.element.android.features.preferences.impl.root.PreferencesRootView
import io.element.android.features.reportroom.api.ReportRoomEntryPoint
import io.element.android.features.rolesandpermissions.api.ChangeRoomMemberRolesEntryPoint
import io.element.android.features.rolesandpermissions.api.ChangeRoomMemberRolesListType
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.appyx.launchMolecule
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.deeplink.api.usecase.InviteFriendsUseCase
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.utils.DelayedVisibility
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.emoji.api.picker.EmojiPickerRenderer
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.proseio.selfchat.ProseioSelfChat
import io.element.android.services.analytics.api.AnalyticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import io.element.android.libraries.proseio.R as ProseioR

@ContributesNode(SessionScope::class)
@AssistedInject
class HomeFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val matrixClient: MatrixClient,
    private val presenter: HomePresenter,
    private val inviteFriendsUseCase: InviteFriendsUseCase,
    private val analyticsService: AnalyticsService,
    private val acceptDeclineInviteView: AcceptDeclineInviteView,
    private val reportRoomEntryPoint: ReportRoomEntryPoint,
    private val declineInviteAndBlockUserEntryPoint: DeclineInviteAndBlockEntryPoint,
    private val changeRoomMemberRolesEntryPoint: ChangeRoomMemberRolesEntryPoint,
    private val leaveRoomRenderer: LeaveRoomRenderer,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    // Proseio: the Você tab (ADR 0053)
    private val preferencesRootPresenter: Lazy<PreferencesRootPresenter>,
    private val emojiPickerRenderer: EmojiPickerRenderer,
    private val proseioSelfChat: ProseioSelfChat,
    private val snackbarDispatcher: SnackbarDispatcher,
) : BaseFlowNode<HomeFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = NavTarget.Root,
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins
) {
    private val callback: HomeEntryPoint.Callback = callback()
    private val stateFlow = launchMolecule { presenter.present() }

    override fun onBuilt() {
        super.onBuilt()
        lifecycle.subscribe(
            onResume = {
                analyticsService.screen(MobileScreen(screenName = MobileScreen.ScreenName.Home))
            }
        )
        whenChildAttached {
            commonLifecycle: Lifecycle,
            changeRoomMemberRolesNode: ChangeRoomMemberRolesEntryPoint.NodeProxy,
            ->
            commonLifecycle.coroutineScope.launch {
                val isNewOwnerSelected = changeRoomMemberRolesNode.waitForCompletion()
                withContext(NonCancellable) {
                    backstack.pop()
                    if (isNewOwnerSelected) {
                        onNewOwnersSelected(changeRoomMemberRolesNode.roomId)
                    }
                }
            }
        }
    }

    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object Root : NavTarget

        @Parcelize
        data class ReportRoom(val roomId: RoomId) : NavTarget

        @Parcelize
        data class DeclineInviteAndBlockUser(val inviteData: InviteData) : NavTarget

        @Parcelize
        data class SelectNewOwnersWhenLeavingRoom(val roomId: RoomId) : NavTarget
    }

    private fun navigateToReportRoom(roomId: RoomId) {
        backstack.push(NavTarget.ReportRoom(roomId))
    }

    private fun navigateToDeclineInviteAndBlockUser(roomSummary: RoomListRoomSummary) {
        backstack.push(NavTarget.DeclineInviteAndBlockUser(roomSummary.toInviteData()))
    }

    private fun onMenuActionClick(activity: Activity, roomListMenuAction: RoomListMenuAction) {
        when (roomListMenuAction) {
            RoomListMenuAction.InviteFriends -> {
                inviteFriendsUseCase.execute(activity)
            }
            RoomListMenuAction.ReportBug -> {
                callback.navigateToBugReport()
            }
        }
    }

    // Proseio: opens the self-chat, or creates it on first use (ADR 0053).
    private var proseioSelfChatJob: Job? = null

    private fun openProseioSelfChat(name: String, navigateToRoom: (RoomId, EventId?) -> Unit) {
        if (proseioSelfChatJob?.isActive == true) return
        proseioSelfChatJob = sessionCoroutineScope.launch {
            proseioSelfChat.openOrCreate(name).fold(
                onSuccess = { roomId -> navigateToRoom(roomId, null) },
                onFailure = {
                    Timber.e(it, "Proseio self-chat: open failed")
                    snackbarDispatcher.post(SnackbarMessage(ProseioR.string.proseio_self_chat_error))
                },
            )
        }
    }

    private fun openProseioSettings(screen: PreferencesEntryPoint.ProseioSettingsScreen) {
        callback.proseioNavigateToSettings(PreferencesEntryPoint.InitialTarget.ProseioScreen(screen))
    }

    @Composable
    private fun ProseioYouTab(
        contentPadding: PaddingValues,
        modifier: Modifier,
        navigateToRoom: (RoomId, EventId?) -> Unit,
    ) {
        val youState = preferencesRootPresenter.value.present()
        val activity = requireNotNull(LocalActivity.current)
        val isDark = !ElementTheme.isLightTheme
        val selfChatName = stringResource(ProseioR.string.proseio_self_chat_title)
        PreferencesRootView(
            state = youState,
            emojiPickerRenderer = emojiPickerRenderer,
            modifier = modifier,
            onBackClick = {},
            onAddAccountClick = callback::navigateToAddAccount,
            onOpenAnalytics = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.Analytics) },
            onOpenLockScreenSettings = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.LockScreen) },
            onOpenAbout = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.About) },
            onOpenDeveloperSettings = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.DeveloperSettings) },
            onOpenMediaSettings = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.Media) },
            onOpenLocationSettings = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.Location) },
            onOpenLabs = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.Labs) },
            onEditProfileClick = { callback.proseioNavigateToSettings(PreferencesEntryPoint.InitialTarget.ProseioEditProfile(it)) },
            onSecureBackupClick = callback::navigateToSetUpRecovery,
            onManageAccountClick = { url -> activity.openUrlInChromeCustomTab(null, darkTheme = isDark, url = url) },
            onLinkNewDeviceClick = callback::proseioNavigateToLinkNewDevice,
            onOpenRageShake = callback::navigateToBugReport,
            onModerationAndSafetyClick = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.ModerationAndSafety) },
            onOpenNotificationSettings = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.Notifications) },
            onSignOutClick = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.SignOut) },
            onDeactivateClick = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.AccountDeactivation) },
            onProseioHandleClick = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.Handle) },
            onProseioInvitesClick = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.Invites) },
            onProseioPublicProfileClick = { openProseioSettings(PreferencesEntryPoint.ProseioSettingsScreen.PublicProfile) },
            proseioTabContentPadding = contentPadding,
            onProseioSelfChatClick = { openProseioSelfChat(selfChatName, navigateToRoom) },
        )
    }

    private fun navigateToSelectNewOwnersWhenLeavingRoom(roomId: RoomId) {
        backstack.push(NavTarget.SelectNewOwnersWhenLeavingRoom(roomId))
    }

    private fun onNewOwnersSelected(roomId: RoomId) {
        stateFlow.value.roomListState.eventSink(RoomListEvent.LeaveRoom(roomId, needsConfirmation = false))
    }

    private fun rootNode(buildContext: BuildContext): Node {
        return node(buildContext) { modifier ->
            val state by stateFlow.collectAsState()
            val activity = requireNotNull(LocalActivity.current)

            val loadingJoinedRoomJob = remember { mutableStateOf<AsyncData<Job>>(AsyncData.Uninitialized) }
            if (loadingJoinedRoomJob.value.isLoading()) {
                DelayedVisibility(duration = 400.milliseconds) {
                    ProgressDialog(
                        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
                        onDismissRequest = {
                            loadingJoinedRoomJob.value.dataOrNull()?.cancel()
                            loadingJoinedRoomJob.value = AsyncData.Uninitialized
                        }
                    )
                }
            }

            fun navigateToRoom(
                roomId: RoomId,
                eventId: EventId?,
            ) {
                if (!loadingJoinedRoomJob.value.isUninitialized()) {
                    Timber.w("Already loading a room, ignoring navigateToRoom for $roomId")
                    return
                }

                val job = sessionCoroutineScope.launch {
                    runCatchingExceptions {
                        matrixClient.getJoinedRoom(roomId)
                    }.fold(
                        onSuccess = { joinedRoom ->
                            if (isActive) {
                                callback.navigateToRoom(roomId = roomId, eventId = eventId, joinedRoom = joinedRoom)
                                loadingJoinedRoomJob.value = AsyncData.Success(coroutineContext.job)
                                // Wait a bit before resetting the state to avoid allowing to open several rooms
                                delay(200.milliseconds)
                                loadingJoinedRoomJob.value = AsyncData.Uninitialized
                            }
                        },
                        onFailure = {
                            // If the operation wasn't cancelled, navigate without the room, using the room id
                            if (it !is CancellationException) {
                                callback.navigateToRoom(roomId = roomId, eventId = null, joinedRoom = null)
                            }
                            loadingJoinedRoomJob.value = AsyncData.Failure(error = it, prevData = coroutineContext.job)
                            // Wait a bit before resetting the state to avoid allowing to open several rooms
                            delay(200.milliseconds)
                            loadingJoinedRoomJob.value = AsyncData.Uninitialized
                        }
                    )
                }
                loadingJoinedRoomJob.value = AsyncData.Loading(job)
            }

            HomeView(
                homeState = state,
                onRoomClick = ::navigateToRoom,
                // Proseio: the avatar opens the Você tab, which holds the settings (ADR 0053).
                onSettingsClick = { state.eventSink(HomeEvent.SelectHomeNavigationBarItem(HomeNavigationBarItem.You)) },
                onAddAccountClick = callback::navigateToAddAccount,
                onStartChatClick = callback::navigateToCreateRoom,
                onCreateSpaceClick = callback::navigateToCreateSpace,
                onSetUpRecoveryClick = callback::navigateToSetUpRecovery,
                onConfirmRecoveryKeyClick = callback::navigateToEnterRecoveryKey,
                onRoomSettingsClick = callback::navigateToRoomSettings,
                onMenuActionClick = { onMenuActionClick(activity, it) },
                onReportRoomClick = ::navigateToReportRoom,
                onDeclineInviteAndBlockUser = ::navigateToDeclineInviteAndBlockUser,
                modifier = modifier,
                acceptDeclineInviteView = {
                    acceptDeclineInviteView.Render(
                        state = state.roomListState.acceptDeclineInviteState,
                        onAcceptInviteSuccess = { navigateToRoom(roomId = it, eventId = null) },
                        onDeclineInviteSuccess = { },
                        modifier = Modifier
                    )
                },
                leaveRoomView = {
                    leaveRoomRenderer.Render(
                        state = state.roomListState.leaveRoomState,
                        onSelectNewOwners = ::navigateToSelectNewOwnersWhenLeavingRoom,
                        modifier = Modifier
                    )
                },
                youTabView = { contentPadding, tabModifier ->
                    ProseioYouTab(contentPadding = contentPadding, modifier = tabModifier, navigateToRoom = ::navigateToRoom)
                },
            )
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView()
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            is NavTarget.ReportRoom -> {
                reportRoomEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    roomId = navTarget.roomId,
                )
            }
            is NavTarget.DeclineInviteAndBlockUser -> {
                declineInviteAndBlockUserEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    inviteData = navTarget.inviteData,
                    callback = object : DeclineInviteAndBlockEntryPoint.Callback {
                        override fun onDeclineSuccess() {
                            backstack.pop()
                        }
                    },
                )
            }
            is NavTarget.SelectNewOwnersWhenLeavingRoom -> {
                val room = runBlocking { matrixClient.getJoinedRoom(navTarget.roomId) } ?: error("Room ${navTarget.roomId} not found")
                changeRoomMemberRolesEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    room = room,
                    listType = ChangeRoomMemberRolesListType.SelectNewOwnersWhenLeaving,
                )
            }
            NavTarget.Root -> rootNode(buildContext)
        }
    }
}
