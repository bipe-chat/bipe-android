/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.bipe

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.bipe.account.BipeBiometricUnlock
import io.element.android.libraries.bipe.generated.BipeSettingsGroup
import io.element.android.libraries.bipe.generated.BipeSettingsRow
import io.element.android.libraries.bipe.generated.BipeSettingsRowKind
import io.element.android.libraries.bipe.settings.localizedUrl
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.user.MatrixUser

/** Bipe: a group screen of ADR 0112 (Seu perfil, Privacidade, Sua conta, Conversas e mídia, Ajuda e sobre). */
@ContributesNode(SessionScope::class)
@AssistedInject
class BipeSettingsGroupNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: BipeSettingsGroupPresenter.Factory,
    private val biometricUnlock: BipeBiometricUnlock,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val group: BipeSettingsGroup) : NodeInputs

    interface Callback : Plugin {
        fun navigateToEditProfile(matrixUser: MatrixUser)
        fun navigateToHandle()
        fun navigateToPublicProfile()
        fun navigateToMediaSettings()
        fun navigateToLocationSettings()
        fun navigateToSecureBackup()
        fun navigateToLinkNewDevice()
        fun navigateToSignOut()
        fun navigateToMessaging()
        fun navigateToBlockedUsers()
        fun navigateToOssLicenses()
    }

    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(inputs<Inputs>().group)

    @Composable
    override fun View(modifier: Modifier) {
        val activity = requireNotNull(LocalActivity.current)
        val isDark = !ElementTheme.isLightTheme
        val state = presenter.present()
        BipeSettingsGroupView(
            state = state,
            onBackClick = ::navigateUp,
            onRowClick = { row ->
                when (row) {
                    BipeSettingsRow.PROFILE_NAME_PHOTO -> callback.navigateToEditProfile(state.myUser)
                    BipeSettingsRow.PROFILE_HANDLE -> callback.navigateToHandle()
                    BipeSettingsRow.PROFILE_PUBLIC_PROFILE -> callback.navigateToPublicProfile()
                    BipeSettingsRow.PRIVACY_MESSAGING -> callback.navigateToMessaging()
                    BipeSettingsRow.PRIVACY_BLOCKED -> callback.navigateToBlockedUsers()
                    BipeSettingsRow.ACCOUNT_RECOVERY_KEY -> callback.navigateToSecureBackup()
                    BipeSettingsRow.ACCOUNT_LINKED_DEVICES -> callback.navigateToLinkNewDevice()
                    BipeSettingsRow.ACCOUNT_SIGN_OUT -> callback.navigateToSignOut()
                    BipeSettingsRow.CHATS_MEDIA_QUALITY -> callback.navigateToMediaSettings()
                    BipeSettingsRow.CHATS_LIVE_LOCATION -> callback.navigateToLocationSettings()
                    BipeSettingsRow.HELP_LICENSES -> callback.navigateToOssLicenses()
                    else -> if (row.kind == BipeSettingsRowKind.URL) {
                        row.localizedUrl()?.let { activity.openUrlInChromeCustomTab(null, isDark, it) }
                    }
                }
            },
            modifier = modifier,
            biometricUnlockRow = { biometricUnlock.Row() },
        )
    }
}
