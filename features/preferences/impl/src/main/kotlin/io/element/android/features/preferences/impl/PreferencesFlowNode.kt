/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.licenses.api.OpenSourceLicensesEntryPoint
import io.element.android.features.lockscreen.api.LockScreenEntryPoint
import io.element.android.features.logout.api.LogoutEntryPoint
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.features.preferences.impl.about.AboutNode
import io.element.android.features.preferences.impl.analytics.AnalyticsSettingsNode
import io.element.android.features.preferences.impl.bipe.BipeSettingsGroupNode
import io.element.android.features.preferences.impl.blockedusers.BlockedUsersNode
import io.element.android.features.preferences.impl.developer.DeveloperSettingsNode
import io.element.android.features.preferences.impl.labs.LabsNode
import io.element.android.features.preferences.impl.location.LocationSettingsNode
import io.element.android.features.preferences.impl.media.MediaSettingsNode
import io.element.android.features.preferences.impl.notifications.NotificationSettingsNode
import io.element.android.features.preferences.impl.notifications.edit.EditDefaultNotificationSettingNode
import io.element.android.features.preferences.impl.root.PreferencesRootNode
import io.element.android.features.preferences.impl.user.editprofile.EditUserProfileNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.appyx.canPop
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.bipe.generated.BipeSettingsGroup
import io.element.android.libraries.bipe.handle.BipeHandleNode
import io.element.android.libraries.bipe.invites.BipeInvitesNode
import io.element.android.libraries.bipe.profile.BipePublicProfileNode
import io.element.android.libraries.bipe.settings.BipeMessagingNode
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.mediaviewer.api.FileViewerEntryPoint
import io.element.android.libraries.troubleshoot.api.NotificationTroubleShootEntryPoint
import io.element.android.libraries.troubleshoot.api.PushHistoryEntryPoint
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class PreferencesFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val notificationTroubleShootEntryPoint: NotificationTroubleShootEntryPoint,
    private val pushHistoryEntryPoint: PushHistoryEntryPoint,
    private val fileViewerEntryPoint: FileViewerEntryPoint,
    private val logoutEntryPoint: LogoutEntryPoint,
    private val openSourceLicensesEntryPoint: OpenSourceLicensesEntryPoint,
    private val lockScreenEntryPoint: LockScreenEntryPoint,
) : BaseFlowNode<PreferencesFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = plugins.filterIsInstance<PreferencesEntryPoint.Params>().first().initialElement.toNavTarget(),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object Root : NavTarget

        @Parcelize
        data object DeveloperSettings : NavTarget

        @Parcelize
        data object MediaSettings : NavTarget

        @Parcelize
        data object LocationSettings : NavTarget

        @Parcelize
        data object Labs : NavTarget

        @Parcelize
        data object AnalyticsSettings : NavTarget

        @Parcelize
        data object About : NavTarget

        @Parcelize
        data object NotificationSettings : NavTarget

        @Parcelize
        data object TroubleshootNotifications : NavTarget

        @Parcelize
        data object PushHistory : NavTarget

        @Parcelize
        data class PushRules(val filename: String, val content: String) : NavTarget

        @Parcelize
        data class EditDefaultNotificationSetting(val isOneToOne: Boolean) : NavTarget

        @Parcelize
        data class UserProfile(val matrixUser: MatrixUser) : NavTarget

        @Parcelize
        data object BlockedUsers : NavTarget

        @Parcelize
        data object SignOut : NavTarget

        @Parcelize
        data object OssLicenses : NavTarget

        @Parcelize
        data object BipeHandle : NavTarget

        @Parcelize
        data object BipeInvites : NavTarget

        @Parcelize
        data object BipePublicProfile : NavTarget

        /** Bipe: Seu perfil and the group screens of ADR 0112. */
        @Parcelize
        data class BipeGroup(val group: BipeSettingsGroup) : NavTarget

        /** Bipe: "Quem pode me mandar mensagem" (ADR 0016). */
        @Parcelize
        data object BipeMessaging : NavTarget

        /** Bipe: "Bloquear o app" turned on in Sua conta. Element's lock screen setup: new PIN, confirm, biometric offer. */
        @Parcelize
        data object BipeAppLockSetup : NavTarget
    }

    private val callback: PreferencesEntryPoint.Callback = callback()

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.Root -> {
                val callback = object : PreferencesRootNode.Callback {
                    override fun navigateToAddAccount() {
                        callback.navigateToAddAccount()
                    }

                    override fun navigateToAnalyticsSettings() {
                        backstack.push(NavTarget.AnalyticsSettings)
                    }

                    override fun navigateToAbout() {
                        backstack.push(BipeSettingsGroup.HELP.toNavTarget()) // Bipe: Ajuda e sobre (ADR 0112)
                    }

                    override fun navigateToDeveloperSettings() {
                        backstack.push(NavTarget.DeveloperSettings)
                    }

                    override fun navigateToLockScreenSettings() {
                        // Bipe: the app lock is the Senha section of Sua conta; no "Remover PIN" (ADR 0112).
                        backstack.push(BipeSettingsGroup.ACCOUNT.toNavTarget())
                    }

                    override fun navigateToMediaSettings() {
                        backstack.push(NavTarget.MediaSettings)
                    }

                    override fun navigateToLocationSettings() {
                        backstack.push(NavTarget.LocationSettings)
                    }

                    override fun navigateToLabs() {
                        backstack.push(NavTarget.Labs)
                    }

                    override fun navigateToBipeHandle() {
                        backstack.push(NavTarget.BipeHandle)
                    }

                    override fun navigateToBipeInvites() {
                        backstack.push(NavTarget.BipeInvites)
                    }

                    override fun navigateToBipePublicProfile() {
                        backstack.push(NavTarget.BipePublicProfile)
                    }

                    override fun navigateToBipeGroup(group: BipeSettingsGroup) {
                        backstack.push(group.toNavTarget())
                    }

                    override fun navigateToBugReport() {
                        callback.navigateToBugReport()
                    }

                    override fun navigateToSecureBackup() {
                        callback.navigateToSecureBackup()
                    }

                    override fun navigateToModerationAndSafety() {
                        // Bipe: Moderation & safety is gone; its rows are in Privacidade and Conversas e mídia (ADR 0112).
                        backstack.push(BipeSettingsGroup.PRIVACY.toNavTarget())
                    }

                    override fun navigateToNotificationSettings() {
                        backstack.push(NavTarget.NotificationSettings)
                    }

                    override fun navigateToLinkNewDevice() {
                        callback.navigateToLinkNewDevice()
                    }

                    override fun navigateToUserProfile(matrixUser: MatrixUser) {
                        backstack.push(NavTarget.UserProfile(matrixUser))
                    }

                    override fun startSignOutFlow() {
                        backstack.push(NavTarget.SignOut)
                    }

                    override fun startAccountDeactivationFlow() {
                        // Bipe: Element's "Delete account" asks for a password the account does not have (ADR 0057).
                        // The Bipe flow comes with #363.
                        backstack.push(BipeSettingsGroup.ACCOUNT.toNavTarget())
                    }
                }
                createNode<PreferencesRootNode>(buildContext, plugins = listOf(callback))
            }
            NavTarget.DeveloperSettings -> {
                val developerSettingsCallback = object : DeveloperSettingsNode.Callback {
                    override fun navigateToPushRules(filename: String, content: String) {
                        backstack.push(NavTarget.PushRules(filename = filename, content = content))
                    }

                    override fun navigateToPushHistory() {
                        backstack.push(NavTarget.PushHistory)
                    }

                    override fun onDone() {
                        if (backstack.canPop()) {
                            backstack.pop()
                        } else {
                            navigateUp()
                        }
                    }
                }
                createNode<DeveloperSettingsNode>(buildContext, listOf(developerSettingsCallback))
            }
            NavTarget.Labs -> {
                val callback = object : LabsNode.Callback {
                    override fun onDone() {
                        // Bipe: the Você tab can open this screen as the first one (ADR 0053).
                        if (backstack.canPop()) backstack.pop() else navigateUp()
                    }
                }
                createNode<LabsNode>(buildContext, listOf(callback))
            }
            NavTarget.LocationSettings -> {
                createNode<LocationSettingsNode>(buildContext)
            }
            NavTarget.BipeHandle -> {
                createNode<BipeHandleNode>(buildContext)
            }
            NavTarget.BipeInvites -> {
                val callback = object : BipeInvitesNode.Callback {
                    override fun navigateToUserProfile(userId: String) {
                        callback.navigateToUserProfile(UserId(userId))
                    }
                }
                createNode<BipeInvitesNode>(buildContext, listOf(callback))
            }
            NavTarget.BipePublicProfile -> {
                val callback = object : BipePublicProfileNode.Callback {
                    override fun navigateToHandle() {
                        backstack.push(NavTarget.BipeHandle)
                    }
                }
                createNode<BipePublicProfileNode>(buildContext, listOf(callback))
            }
            is NavTarget.BipeGroup -> {
                val callback = object : BipeSettingsGroupNode.Callback {
                    override fun navigateToEditProfile(matrixUser: MatrixUser) {
                        backstack.push(NavTarget.UserProfile(matrixUser))
                    }

                    override fun navigateToHandle() {
                        backstack.push(NavTarget.BipeHandle)
                    }

                    override fun navigateToPublicProfile() {
                        backstack.push(NavTarget.BipePublicProfile)
                    }

                    override fun navigateToMediaSettings() {
                        backstack.push(NavTarget.MediaSettings)
                    }

                    override fun navigateToLocationSettings() {
                        backstack.push(NavTarget.LocationSettings)
                    }

                    override fun navigateToSecureBackup() {
                        callback.navigateToSecureBackup()
                    }

                    override fun navigateToAppLockSetup() {
                        backstack.push(NavTarget.BipeAppLockSetup)
                    }

                    override fun navigateToLinkNewDevice() {
                        callback.navigateToLinkNewDevice()
                    }

                    override fun navigateToSignOut() {
                        backstack.push(NavTarget.SignOut)
                    }

                    override fun navigateToMessaging() {
                        backstack.push(NavTarget.BipeMessaging)
                    }

                    override fun navigateToBlockedUsers() {
                        backstack.push(NavTarget.BlockedUsers)
                    }

                    override fun navigateToOssLicenses() {
                        backstack.push(NavTarget.OssLicenses)
                    }

                    override fun navigateToSupport() {
                        callback.navigateToBipeSupport()
                    }

                    override fun navigateToAddAccount() {
                        callback.navigateToAddAccount()
                    }

                    override fun bipeNavigateToSignup(inviteCode: String?) {
                        callback.bipeNavigateToSignup(inviteCode)
                    }

                    override fun navigateToBugReport() {
                        callback.navigateToBugReport()
                    }
                }
                createNode<BipeSettingsGroupNode>(buildContext, listOf(BipeSettingsGroupNode.Inputs(navTarget.group), callback))
            }
            NavTarget.BipeMessaging -> {
                createNode<BipeMessagingNode>(buildContext)
            }
            NavTarget.BipeAppLockSetup -> {
                // Back from the setup leaves no PIN, so the switch in Sua conta shows off again.
                lockScreenEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    navTarget = LockScreenEntryPoint.Target.Setup,
                    callback = object : LockScreenEntryPoint.Callback {
                        override fun onSetupDone() {
                            backstack.pop()
                        }
                    },
                )
            }
            NavTarget.About -> {
                val callback = object : AboutNode.Callback {
                    override fun navigateToOssLicenses() {
                        backstack.push(NavTarget.OssLicenses)
                    }
                }
                createNode<AboutNode>(buildContext, listOf(callback))
            }
            NavTarget.AnalyticsSettings -> {
                createNode<AnalyticsSettingsNode>(buildContext)
            }
            NavTarget.NotificationSettings -> {
                val notificationSettingsCallback = object : NotificationSettingsNode.Callback {
                    override fun navigateToEditDefaultNotificationSetting(isOneToOne: Boolean) {
                        backstack.push(NavTarget.EditDefaultNotificationSetting(isOneToOne))
                    }

                    override fun navigateToTroubleshootNotifications() {
                        backstack.push(NavTarget.TroubleshootNotifications)
                    }
                }
                createNode<NotificationSettingsNode>(buildContext, listOf(notificationSettingsCallback))
            }
            NavTarget.TroubleshootNotifications -> {
                notificationTroubleShootEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = object : NotificationTroubleShootEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) {
                                backstack.pop()
                            } else {
                                navigateUp()
                            }
                        }

                        override fun navigateToBlockedUsers() {
                            backstack.push(NavTarget.BlockedUsers)
                        }
                    },
                )
            }
            NavTarget.PushHistory -> {
                pushHistoryEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = object : PushHistoryEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) {
                                backstack.pop()
                            } else {
                                navigateUp()
                            }
                        }

                        override fun navigateToEvent(roomId: RoomId, eventId: EventId) {
                            callback.navigateToEvent(roomId, eventId)
                        }
                    },
                )
            }
            is NavTarget.PushRules -> {
                fileViewerEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = FileViewerEntryPoint.Params(
                        filename = navTarget.filename,
                        mimeType = MimeTypes.Json,
                        content = navTarget.content,
                    ),
                    callback = object : FileViewerEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) {
                                backstack.pop()
                            } else {
                                navigateUp()
                            }
                        }
                    },
                )
            }
            is NavTarget.EditDefaultNotificationSetting -> {
                val callback = object : EditDefaultNotificationSettingNode.Callback {
                    override fun navigateToRoomNotificationSettings(roomId: RoomId) {
                        callback.navigateToRoomNotificationSettings(roomId)
                    }
                }
                val input = EditDefaultNotificationSettingNode.Inputs(navTarget.isOneToOne)
                createNode<EditDefaultNotificationSettingNode>(buildContext, plugins = listOf(input, callback))
            }
            NavTarget.MediaSettings -> {
                createNode<MediaSettingsNode>(buildContext)
            }
            is NavTarget.UserProfile -> {
                val inputs = EditUserProfileNode.Inputs(navTarget.matrixUser)
                val callback = object : EditUserProfileNode.Callback {
                    override fun onDone() {
                        // Bipe: the Você tab can open this screen as the first one (ADR 0053).
                        if (backstack.canPop()) backstack.pop() else navigateUp()
                    }
                }
                createNode<EditUserProfileNode>(buildContext, listOf(inputs, callback))
            }
            NavTarget.BlockedUsers -> {
                createNode<BlockedUsersNode>(buildContext)
            }
            NavTarget.SignOut -> {
                val callBack: LogoutEntryPoint.Callback = object : LogoutEntryPoint.Callback {
                    override fun navigateToSecureBackup() {
                        callback.navigateToSecureBackup()
                    }
                }
                logoutEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = callBack,
                )
            }
            is NavTarget.OssLicenses -> {
                openSourceLicensesEntryPoint.createNode(this, buildContext)
            }
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView()
    }
}

/** Bipe: where a group row of the Você root goes (ADR 0112). Notificações keeps the Element screen. */
internal fun BipeSettingsGroup.toNavTarget(): PreferencesFlowNode.NavTarget = when (this) {
    BipeSettingsGroup.NOTIFICATIONS -> PreferencesFlowNode.NavTarget.NotificationSettings
    BipeSettingsGroup.PROFILE, BipeSettingsGroup.PRIVACY, BipeSettingsGroup.ACCOUNT, BipeSettingsGroup.CHATS, BipeSettingsGroup.HELP ->
        PreferencesFlowNode.NavTarget.BipeGroup(this)
}
