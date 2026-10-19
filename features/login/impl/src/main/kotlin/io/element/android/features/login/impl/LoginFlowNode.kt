/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import android.app.Activity
import android.os.Parcelable
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import com.bumble.appyx.navmodel.backstack.operation.replace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.appconfig.OnBoardingConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.login.api.LoginEntryPoint
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.classic.ElementClassicConnection
import io.element.android.features.login.impl.qrcode.QrCodeLoginFlowNode
import io.element.android.features.login.impl.screens.biperecovery.BipeRecoveryNode
import io.element.android.features.login.impl.screens.bipesignup.BipeSignupNode
import io.element.android.features.login.impl.screens.chooseaccountprovider.ChooseAccountProviderNode
import io.element.android.features.login.impl.screens.classic.ClassicFlowNode
import io.element.android.features.login.impl.screens.confirmaccountprovider.ConfirmAccountProviderNode
import io.element.android.features.login.impl.screens.loginpassword.LoginPasswordNode
import io.element.android.features.login.impl.screens.onboarding.OnBoardingNode
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.bipe.account.BipeDeviceAccount
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.oauth.api.OAuthAction
import io.element.android.libraries.oauth.api.OAuthActionFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@ContributesNode(AppScope::class)
@AssistedInject
class LoginFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val accountProviderDataSource: AccountProviderDataSource,
    private val oAuthActionFlow: OAuthActionFlow,
    @AppCoroutineScope
    private val appCoroutineScope: CoroutineScope,
    private val elementClassicConnection: ElementClassicConnection,
    private val preferencesEntryPoint: PreferencesEntryPoint,
    private val bipeDeviceAccount: BipeDeviceAccount, // Bipe
    private val authenticationService: MatrixAuthenticationService, // Bipe: "Conta do beta"
) : BaseFlowNode<LoginFlowNode.NavTarget>(
    backstack = BackStack(
        // Bipe (bipe-chat/bipe#415): no Element Classic check, so no spinner before the start screen.
        initialElement = if (OnBoardingConfig.USES_BIPE_SIGNUP) NavTarget.OnBoarding(showBackButton = false) else NavTarget.CheckClassicFlow,
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    data class Params(
        val accountProvider: String?,
        val loginHint: String?,
        val bipeInviteCode: String? = null, // Bipe
        val bipeRecoveryToken: String? = null, // Bipe: e-mail recovery link (ADR 0057)
        val bipeStartsSignup: Boolean = false, // Bipe: "Nova conta" (bipe#445)
    ) : NodeInputs {
        // Bipe: the recovery token is a secret.
        override fun toString(): String = "Params(accountProvider=$accountProvider, hasRecoveryToken=${bipeRecoveryToken != null})"
    }

    private val callback: LoginEntryPoint.Callback = callback()
    private var activity: Activity? = null
    private var darkTheme: Boolean = false

    private var externalAppStarted = false

    // Bipe: a restored back stack already holds the screens the links opened.
    private val isRestored = buildContext.savedStateMap != null

    override fun onBuilt() {
        super.onBuilt()
        if (OnBoardingConfig.USES_BIPE_SIGNUP && !isRestored) {
            // An invite link shows its start screen variant (bipe-chat/bipe#415); a recovery link opens the recovery screen.
            // "Nova conta" (bipe#445) opens the sign-up; with an invite code from the account on screen it starts at the name step.
            val params = inputs<Params>()
            params.bipeRecoveryToken?.let { backstack.push(NavTarget.BipeRecovery(linkToken = it)) }
            if (params.bipeStartsSignup) backstack.push(NavTarget.BipeSignup(inviteCode = params.bipeInviteCode))
            if (params.bipeInviteCode == null && params.bipeRecoveryToken == null && !params.bipeStartsSignup) {
                bipeStartDeviceAccount()
            }
        }
        lifecycle.subscribe(
            onResume = {
                if (externalAppStarted) {
                    externalAppStarted = false
                    // Workaround to detect that the Custom Chrome Tab has been closed
                    // If there is no coming OidcAction (that would end this Node),
                    // consider that the user has cancelled the login
                    // by pressing back or by closing the Custom Chrome Tab.
                    lifecycleScope.launch {
                        delay(5000)
                        oAuthActionFlow.post(OAuthAction.GoBack(toUnblock = true))
                    }
                }
            }
        )
    }

    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object CheckClassicFlow : NavTarget

        @Parcelize
        data class OnBoarding(
            val showBackButton: Boolean,
        ) : NavTarget

        @Parcelize
        data object QrCode : NavTarget

        @Parcelize
        data object AppDeveloperSettings : NavTarget

        @Parcelize
        data class ConfirmAccountProvider(
            val isAccountCreation: Boolean,
        ) : NavTarget

        @Parcelize
        data object ChooseAccountProvider : NavTarget

        // Bipe: invite sign-up. inviteCode is a normalised code from an invite link.
        @Parcelize
        data class BipeSignup(val inviteCode: String? = null, val startsWithInviteCode: Boolean = true) : NavTarget

        // Bipe: get back into a device-first account. linkToken is the token of an e-mail recovery link.
        @Parcelize
        data class BipeRecovery(val linkToken: String? = null) : NavTarget {
            override fun toString(): String = "BipeRecovery(hasLinkToken=${linkToken != null})"
        }

        @Parcelize
        data class LoginPassword(
            val initialLogin: String = "",
        ) : NavTarget
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.CheckClassicFlow -> {
                val callback = object : ClassicFlowNode.Callback {
                    override fun navigateToOnBoarding(allowBackNavigation: Boolean) {
                        if (allowBackNavigation) {
                            backstack.push(NavTarget.OnBoarding(showBackButton = true))
                        } else {
                            backstack.replace(NavTarget.OnBoarding(showBackButton = false))
                        }
                    }

                    override fun navigateToLoginPassword() {
                        backstack.push(NavTarget.LoginPassword())
                    }

                    override fun navigateToOAuth(oAuthDetails: OAuthDetails) {
                        navigateToMas(oAuthDetails)
                    }
                }
                createNode<ClassicFlowNode>(buildContext, listOf(callback))
            }
            is NavTarget.OnBoarding -> {
                val callback = object : OnBoardingNode.Callback {
                    override fun navigateToSignUpFlow() {
                        backstack.push(
                            if (OnBoardingConfig.USES_BIPE_SIGNUP) {
                                NavTarget.BipeSignup()
                            } else {
                                NavTarget.ConfirmAccountProvider(isAccountCreation = true)
                            }
                        )
                    }

                    override fun navigateToSignInFlow(mustChooseAccountProvider: Boolean) {
                        backstack.push(
                            if (mustChooseAccountProvider) {
                                NavTarget.ChooseAccountProvider
                            } else {
                                NavTarget.ConfirmAccountProvider(isAccountCreation = false)
                            }
                        )
                    }

                    override fun navigateToQrCode() {
                        backstack.push(NavTarget.QrCode)
                    }

                    override fun navigateToBipeRecovery() {
                        backstack.push(NavTarget.BipeRecovery())
                    }

                    override fun navigateToBipeSignup(startsWithInviteCode: Boolean) {
                        backstack.push(NavTarget.BipeSignup(inviteCode = inputs<Params>().bipeInviteCode, startsWithInviteCode = startsWithInviteCode))
                    }

                    override fun navigateToBugReport() {
                        callback.navigateToBugReport()
                    }

                    override fun navigateToOAuth(oAuthDetails: OAuthDetails) {
                        navigateToMas(oAuthDetails)
                    }

                    override fun navigateToDeveloperSettings() {
                        backstack.push(NavTarget.AppDeveloperSettings)
                    }

                    override fun navigateToLoginPassword() {
                        backstack.push(NavTarget.LoginPassword())
                    }

                    override fun onDone() {
                        if (navTarget.showBackButton) {
                            backstack.pop()
                        } else {
                            callback.onDone()
                        }
                    }
                }
                val params = inputs<Params>()
                val inputs = OnBoardingNode.Params(
                    accountProvider = params.accountProvider,
                    loginHint = params.loginHint,
                    showBackButton = navTarget.showBackButton,
                    // The code from "Nova conta" is the user's own invite, not an invitation to show on the start screen.
                    bipeInviteCode = params.bipeInviteCode.takeUnless { params.bipeStartsSignup },
                )
                createNode<OnBoardingNode>(buildContext, listOf(callback, inputs))
            }
            NavTarget.AppDeveloperSettings -> {
                val callback = object : PreferencesEntryPoint.DeveloperSettingsCallback {
                    override fun onDone() {
                        backstack.pop()
                    }
                }
                preferencesEntryPoint.createAppDeveloperSettingsNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = callback,
                )
            }
            NavTarget.ChooseAccountProvider -> {
                val callback = object : ChooseAccountProviderNode.Callback {
                    override fun navigateToOAuth(oAuthDetails: OAuthDetails) {
                        navigateToMas(oAuthDetails)
                    }

                    override fun navigateToLoginPassword() {
                        backstack.push(NavTarget.LoginPassword())
                    }
                }
                createNode<ChooseAccountProviderNode>(buildContext, listOf(callback))
            }
            NavTarget.QrCode -> {
                val callback = object : QrCodeLoginFlowNode.Callback {
                    override fun navigateBack() {
                        backstack.pop()
                    }
                }
                createNode<QrCodeLoginFlowNode>(buildContext, listOf(callback))
            }
            is NavTarget.ConfirmAccountProvider -> {
                val inputs = ConfirmAccountProviderNode.Inputs(
                    isAccountCreation = navTarget.isAccountCreation,
                )
                val callback = object : ConfirmAccountProviderNode.Callback {
                    override fun navigateToOAuth(oAuthDetails: OAuthDetails) {
                        navigateToMas(oAuthDetails)
                    }

                    override fun navigateToLoginPassword() {
                        backstack.push(NavTarget.LoginPassword())
                    }
                }
                createNode<ConfirmAccountProviderNode>(buildContext, plugins = listOf(inputs, callback))
            }
            is NavTarget.BipeSignup -> {
                val inputs = BipeSignupNode.Inputs(inviteCode = navTarget.inviteCode, startsWithInviteCode = navTarget.startsWithInviteCode)
                createNode<BipeSignupNode>(buildContext, plugins = listOf(inputs))
            }
            is NavTarget.BipeRecovery -> {
                val callback = object : BipeRecoveryNode.Callback {
                    override fun navigateToQrCode() {
                        backstack.push(NavTarget.QrCode)
                    }

                    override fun navigateToBetaLogin() {
                        // The password screen signs in on the configured server, so set it first (the old start button did).
                        lifecycleScope.launch {
                            accountProviderDataSource.reset()
                            authenticationService.setHomeserver(accountProviderDataSource.flow.value.serverNameOrBaseUrl())
                                .onSuccess { backstack.push(NavTarget.LoginPassword()) }
                                .onFailure { Timber.e(it, "Bipe beta login: the server did not answer") }
                        }
                    }
                }
                val inputs = BipeRecoveryNode.Inputs(linkToken = navTarget.linkToken)
                createNode<BipeRecoveryNode>(buildContext, plugins = listOf(inputs, callback))
            }
            is NavTarget.LoginPassword -> {
                val inputs = LoginPasswordNode.Inputs(
                    initialLogin = navTarget.initialLogin,
                )
                createNode<LoginPasswordNode>(buildContext, plugins = listOf(inputs))
            }
        }
    }

    /**
     * Bipe: app start without a session (ADR 0057, ADR 0059). With the hidden password on this device, sign in with no screen.
     * With a waiting e-mail recovery or a synced recovery token, open the recovery screen (the token needs the PIN).
     */
    private fun bipeStartDeviceAccount() = lifecycleScope.launch {
        val silent = bipeDeviceAccount.signInSilently()
        if (silent?.isSuccess == true) return@launch
        // A sign-up whose answer never arrived (ADR 0094): the account exists, so finish it instead of showing the form.
        if (bipeDeviceAccount.resumePendingSignup()?.isSuccess == true) return@launch
        if (bipeDeviceAccount.pendingClaim() != null || bipeDeviceAccount.hasRecoveryToken()) {
            backstack.push(NavTarget.BipeRecovery())
        }
    }

    private fun navigateToMas(oAuthDetails: OAuthDetails) {
        activity?.let {
            externalAppStarted = true
            it.openUrlInChromeCustomTab(null, darkTheme, oAuthDetails.url)
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        activity = requireNotNull(LocalActivity.current)
        darkTheme = !ElementTheme.isLightTheme

        DisposableEffect(Unit) {
            elementClassicConnection.start()
            onDispose {
                elementClassicConnection.stop()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                activity = null
                appCoroutineScope.launch {
                    accountProviderDataSource.reset()
                }
            }
        }
        BackstackView(transitionHandler = rememberLoginFlowTransitionHandler())
    }
}
