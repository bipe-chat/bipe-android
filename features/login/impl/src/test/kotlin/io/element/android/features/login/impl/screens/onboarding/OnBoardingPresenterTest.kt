/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.onboarding

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.OnBoardingConfig
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.features.enterprise.api.IsEnterpriseBuild
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.features.login.impl.accesscontrol.DefaultAccountProviderAccessControl
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.accountprovider.SaveAccountProviderToHistory
import io.element.android.features.login.impl.accountprovider.anAccountProviderDataSource
import io.element.android.features.login.impl.localnetwork.LocalNetworkPermissionGate
import io.element.android.features.login.impl.login.LoginModePresenter
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.bipe.generated.BipeOnboardingButton
import io.element.android.libraries.bipe.generated.BipeOnboardingStart
import io.element.android.libraries.bipe.generated.BipeOnboardingTarget
import io.element.android.libraries.bipe.onboarding.BipeStartScreenInfo
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.api.accountprovider.matrixOrgAccountProvider
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER_2
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER_3
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.A_HOMESERVER_URL
import io.element.android.libraries.matrix.test.A_HOMESERVER_URL_2
import io.element.android.libraries.matrix.test.A_LOGIN_HINT
import io.element.android.libraries.matrix.test.accountprovider.anAccountProviderManaged
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.oauth.api.OAuthActionFlow
import io.element.android.libraries.oauth.test.FakeOAuthActionFlow
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionAdvisor
import io.element.android.libraries.permissions.test.FakeLocalNetworkPermissionAdvisor
import io.element.android.libraries.permissions.test.FakePermissionsPresenterFactory
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import io.element.android.libraries.bipe.R as BipeR

class OnBoardingPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    companion object {
        private const val ACCOUNT_PROVIDER_FROM_LINK = AN_ACCOUNT_PROVIDER
        private const val ACCOUNT_PROVIDER_FROM_CONFIG = AN_ACCOUNT_PROVIDER_2
        private const val ACCOUNT_PROVIDER_FROM_CONFIG_2 = AN_ACCOUNT_PROVIDER_3
    }

    @Test
    fun `present - ensure initial conditions`() {
        assertThat(
            setOf(
                ACCOUNT_PROVIDER_FROM_LINK,
                ACCOUNT_PROVIDER_FROM_CONFIG,
                ACCOUNT_PROVIDER_FROM_CONFIG_2,
            ).size
        ).isEqualTo(3)
    }

    @Test
    fun `present - initial state`() = runTest {
        val buildMeta = aBuildMeta(
            applicationName = "A",
            productionApplicationName = "B",
            desktopApplicationName = "C",
        )
        val presenter = createPresenter(
            buildMeta = buildMeta,
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = { listOf(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG)) },
                canConnectToAnyAccountProviderResult = { true },
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.showBackButton).isFalse()
            assertThat(initialState.defaultAccountProvider).isNull()
            assertThat(initialState.canLoginWithQrCode).isFalse()
            assertThat(initialState.productionApplicationName).isEqualTo("B")
            assertThat(initialState.canCreateAccount).isEqualTo(OnBoardingConfig.CAN_CREATE_ACCOUNT)
            assertThat(initialState.canReportBug).isFalse()
            assertThat(initialState.isAddingAccount).isFalse()
            val finalState = awaitItem()
            assertThat(finalState.canLoginWithQrCode).isTrue()
        }
    }

    // Bipe (bipe-chat/bipe#415): the start screen buttons come from bipe-shared registries/onboarding.json. iOS asserts the same
    // variants in AuthenticationStartScreenViewModelTests, so a start screen that drifts from the registry fails on both.
    @Test
    fun `present - bipe start screen is invite first when sign-up needs an invite`() = runTest {
        val presenter = createPresenter(bipeStartScreenInfo = FakeBipeStartScreenInfo(inviteRequired = true))
        presenter.test {
            val state = awaitItem()
            assertThat(state.bipeStart).isEqualTo(BipeOnboardingStart.INVITE_ONLY)
            assertThat(state.bipeStart?.primary).isEqualTo(BipeOnboardingButton(BipeR.string.bipe_onboarding_have_invite, BipeOnboardingTarget.INVITE_CODE))
            assertThat(state.bipeStart?.text).containsExactly(BipeOnboardingButton(BipeR.string.bipe_onboarding_have_account, BipeOnboardingTarget.RECOVERY))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - bipe start screen with an invite link names the inviter`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(accountProvider = null, loginHint = null, showBackButton = false, bipeInviteCode = "ABCD-EFGH-JKMN"),
            bipeStartScreenInfo = FakeBipeStartScreenInfo(inviterHandle = "ana"),
        )
        presenter.test {
            var state = awaitItem()
            while (state.bipeInviterHandle == null) state = awaitItem()
            assertThat(state.bipeStart).isEqualTo(BipeOnboardingStart.INVITED)
            assertThat(state.bipeInviterHandle).isEqualTo("ana")
            assertThat(state.bipeStart?.primary).isEqualTo(BipeOnboardingButton(BipeR.string.bipe_onboarding_join_with_invite, BipeOnboardingTarget.NAME))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - bipe start screen goes to the name when sign-up is open`() = runTest {
        val presenter = createPresenter(bipeStartScreenInfo = FakeBipeStartScreenInfo(inviteRequired = false))
        presenter.test {
            var state = awaitItem()
            while (state.bipeStart != BipeOnboardingStart.OPEN) state = awaitItem()
            assertThat(state.bipeStart?.primary).isEqualTo(BipeOnboardingButton(BipeR.string.bipe_signup_title, BipeOnboardingTarget.NAME))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - initial state with back button`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = null,
                loginHint = null,
                showBackButton = true,
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.showBackButton).isTrue()
            skipItems(1)
        }
    }

    @Test
    fun `present - initial state adding account`() = runTest {
        val presenter = createPresenter(
            sessionStore = InMemorySessionStore(
                initialList = listOf(
                    aSessionData()
                )
            )
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.isAddingAccount).isTrue()
        }
    }

    @Test
    fun `present - on boarding logo`() = runTest {
        val presenter = createPresenter(
            onBoardingLogoResIdProvider = OnBoardingLogoResIdProvider { 42 },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.onBoardingLogoResId).isEqualTo(42)
        }
    }

    @Test
    fun `present - clicking on version 7 times has no effect if rageshake not available`() = runTest {
        val presenter = createPresenter(
            rageshakeFeatureAvailability = { flowOf(false) },
        )
        presenter.test {
            skipItems(1)
            awaitItem().also { state ->
                assertThat(state.canReportBug).isFalse()
                repeat(7) {
                    state.eventSink(OnBoardingEvent.OnVersionClick)
                }
            }
            expectNoEvents()
        }
    }

    @Test
    fun `present - clicking on version 7 times will reveal the report a problem button`() = runTest {
        val presenter = createPresenter()
        presenter.test {
            skipItems(1)
            awaitItem().also { state ->
                assertThat(state.canReportBug).isFalse()
                repeat(7) {
                    state.eventSink(OnBoardingEvent.OnVersionClick)
                }
            }
            assertThat(awaitItem().canReportBug).isTrue()
        }
    }

    @Test
    fun `present - opening the app using link with allowed account provider, and the app does not force account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = { listOf(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG)) },
                canConnectToAnyAccountProviderResult = { true },
                isAllowedToConnectToAccountProviderResult = { true },
                isElementProEnforcedResult = { false },
            ),
        )
        presenter.test {
            skipItems(3)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isEqualTo(AccountProvider.Generic(ACCOUNT_PROVIDER_FROM_LINK))
                assertThat(it.canLoginWithQrCode).isFalse()
                assertThat(it.canCreateAccount).isTrue() // Bipe: USES_BIPE_SIGNUP keeps account creation on (OnBoardingPresenter)
            }
        }
    }

    @Test
    fun `present - opening the app using link with not allowed account provider, and the app does not force account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = {
                    listOf(
                        anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG),
                        anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG_2)
                    )
                },
                canConnectToAnyAccountProviderResult = { false },
                isAllowedToConnectToAccountProviderResult = { false },
            ),
        )
        presenter.test {
            skipItems(1)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isNull()
                assertThat(it.canLoginWithQrCode).isTrue()
                assertThat(it.canCreateAccount).isTrue() // Bipe: USES_BIPE_SIGNUP keeps account creation on (OnBoardingPresenter)
            }
        }
    }

    @Test
    fun `present - opening the app using link, and the app forces account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = { listOf(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG)) },
                canConnectToAnyAccountProviderResult = { false },
            )
        )
        presenter.test {
            skipItems(1)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isEqualTo(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG))
                assertThat(it.canLoginWithQrCode).isTrue()
                assertThat(it.canCreateAccount).isTrue() // Bipe: USES_BIPE_SIGNUP keeps account creation on (OnBoardingPresenter)
            }
        }
    }

    @Test
    fun `present - a single configured account provider is not forced when the user may use another one`() = runTest {
        // A singleton allow list only forces the account provider when the user is not free to use another one.
        val presenter = createPresenter(
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = { listOf(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG)) },
                canConnectToAnyAccountProviderResult = { true },
            ),
        )
        presenter.test {
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isNull()
                assertThat(it.mustChooseAccountProvider).isFalse()
            }
            skipItems(1)
        }
    }

    @Test
    fun `present - the user must choose when several account providers are configured and enforced`() = runTest {
        val presenter = createPresenter(
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = {
                    listOf(
                        anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG),
                        anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG_2)
                    )
                },
                canConnectToAnyAccountProviderResult = { false },
            ),
        )
        presenter.test {
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isNull()
                assertThat(it.mustChooseAccountProvider).isTrue()
            }
            skipItems(1)
        }
    }

    @Test
    fun `present - default account provider - login and clear error`() = runTest {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                Result.failure(AN_EXCEPTION)
            },
        )
        val accountProviderDataSource = anAccountProviderDataSource()
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = A_HOMESERVER_URL,
                loginHint = A_LOGIN_HINT,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                isAllowedToConnectToAccountProviderResult = { true },
                isElementProEnforcedResult = { false },
            ),
            loginModePresenter = createLoginModePresenter(
                authenticationService = authenticationService,
            ),
            accountProviderDataSource = accountProviderDataSource,
        )
        presenter.test {
            skipItems(3)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isEqualTo(AccountProvider.Generic(A_HOMESERVER_URL))
                assertThat(accountProviderDataSource.flow.first()).isEqualTo(matrixOrgAccountProvider)
                it.eventSink(OnBoardingEvent.OnSignIn(AccountProvider.Generic(A_HOMESERVER_URL_2)))
                skipItems(1) // Loading
                // Account data source has been updated
                assertThat(accountProviderDataSource.flow.first()).isEqualTo(AccountProvider.Generic(A_HOMESERVER_URL_2))
                // Check an error was returned
                val submittedState = awaitItem()
                assertThat(submittedState.loginModeState.loginMode).isInstanceOf(AsyncData.Failure::class.java)

                // Assert the error is then cleared
                submittedState.eventSink(OnBoardingEvent.ClearError)
                val clearedState = awaitItem()
                assertThat(clearedState.loginModeState.loginMode).isEqualTo(AsyncData.Uninitialized)
            }
        }
    }
}

private fun createPresenter(
    params: OnBoardingNode.Params = OnBoardingNode.Params(
        accountProvider = null,
        loginHint = null,
        showBackButton = false,
    ),
    buildMeta: BuildMeta = aBuildMeta(),
    enterpriseService: EnterpriseService = FakeEnterpriseService(),
    isEnterpriseBuild: IsEnterpriseBuild = { false },
    rageshakeFeatureAvailability: () -> Flow<Boolean> = { flowOf(true) },
    loginModePresenter: LoginModePresenter = createLoginModePresenter(),
    onBoardingLogoResIdProvider: OnBoardingLogoResIdProvider = OnBoardingLogoResIdProvider { null },
    sessionStore: SessionStore = InMemorySessionStore(),
    accountProviderDataSource: AccountProviderDataSource = anAccountProviderDataSource(),
    bipeStartScreenInfo: BipeStartScreenInfo = FakeBipeStartScreenInfo(),
) = OnBoardingPresenter(
    params = params,
    buildMeta = buildMeta,
    enterpriseService = enterpriseService,
    defaultAccountProviderAccessControl = DefaultAccountProviderAccessControl(
        enterpriseService = enterpriseService,
        isEnterpriseBuild = isEnterpriseBuild,
    ),
    rageshakeFeatureAvailability = rageshakeFeatureAvailability,
    loginModePresenter = loginModePresenter,
    onBoardingLogoResIdProvider = onBoardingLogoResIdProvider,
    sessionStore = sessionStore,
    accountProviderDataSource = accountProviderDataSource,
    bipeStartScreenInfo = bipeStartScreenInfo,
)

/** Null reads keep the defaults, so tests that do not care see no extra states. */
private class FakeBipeStartScreenInfo(
    private val inviteRequired: Boolean? = null,
    private val inviterHandle: String? = null,
) : BipeStartScreenInfo {
    override suspend fun isInviteRequired(): Boolean? = inviteRequired

    override suspend fun inviterHandle(code: String): String? = inviterHandle

    override suspend fun installReferrerInviteCode(): String? = null

    override fun clipboardHasText(): Boolean = false

    override fun clipboardInviteCode(): String? = null
}

fun createLoginModePresenter(
    oAuthActionFlow: OAuthActionFlow = FakeOAuthActionFlow(),
    authenticationService: MatrixAuthenticationService = FakeMatrixAuthenticationService(),
    localNetworkPermissionAdvisor: LocalNetworkPermissionAdvisor =
        FakeLocalNetworkPermissionAdvisor(),
    permissionsPresenterFactory: PermissionsPresenter.Factory =
        FakePermissionsPresenterFactory(),
    saveAccountProviderToHistory: SaveAccountProviderToHistory =
        SaveAccountProviderToHistory(anAccountProviderDataSource(), InMemoryAppPreferencesStore()),
): LoginModePresenter = LoginModePresenter(
    oAuthActionFlow = oAuthActionFlow,
    authenticationService = authenticationService,
    localNetworkPermissionGate = LocalNetworkPermissionGate(
        advisor = localNetworkPermissionAdvisor,
        permissionsPresenterFactory = permissionsPresenterFactory,
    ),
    saveAccountProviderToHistory = saveAccountProviderToHistory,
)
