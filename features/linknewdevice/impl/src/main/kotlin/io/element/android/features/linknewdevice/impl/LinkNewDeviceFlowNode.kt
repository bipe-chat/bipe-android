/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl

import android.app.Activity
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.composable.PermanentChild
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.navigation.model.permanent.PermanentNavModel
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.newRoot
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import com.bumble.appyx.navmodel.backstack.operation.replace
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.features.linknewdevice.api.LinkNewDeviceEntryPoint
import io.element.android.features.linknewdevice.impl.screens.confirmation.CodeConfirmationNode
import io.element.android.features.linknewdevice.impl.screens.desktop.DesktopNoticeNode
import io.element.android.features.linknewdevice.impl.screens.error.ErrorNode
import io.element.android.features.linknewdevice.impl.screens.error.ErrorScreenType
import io.element.android.features.linknewdevice.impl.screens.number.EnterNumberNode
import io.element.android.features.linknewdevice.impl.screens.qrcode.ShowQrCodeNode
import io.element.android.features.linknewdevice.impl.screens.scan.ScanQrCodeNode
import io.element.android.features.lockscreen.api.DeviceUnlockEntryPoint
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.bipe.weblink.BipeLinkedDevicesNode
import io.element.android.libraries.bipe.weblink.BipeWebApprovalController
import io.element.android.libraries.bipe.weblink.BipeWebApprovalNode
import io.element.android.libraries.core.log.logger.LoggerTag
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.linknewdevice.ErrorType
import io.element.android.libraries.matrix.api.linknewdevice.LinkDesktopStep
import io.element.android.libraries.matrix.api.linknewdevice.LinkMobileStep
import io.element.android.libraries.matrix.api.logs.LoggerTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import io.element.android.libraries.bipe.R as BipeR

private val tag = LoggerTag("LinkNewDeviceFlowNode", LoggerTags.linkNewDevice)

@ContributesNode(SessionScope::class)
@AssistedInject
class LinkNewDeviceFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val linkNewMobileHandler: LinkNewMobileHandler,
    private val linkNewDesktopHandler: LinkNewDesktopHandler,
    private val sessionEnterpriseService: SessionEnterpriseService,
    private val deviceUnlockEntryPoint: DeviceUnlockEntryPoint,
) : BaseFlowNode<LinkNewDeviceFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = NavTarget.Root,
        savedStateMap = buildContext.savedStateMap,
    ),
    permanentNavModel = PermanentNavModel(
        navTargets = setOf(NavTarget.LockScreen),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    private val callback: LinkNewDeviceEntryPoint.Callback = callback()
    private var activity: Activity? = null
    private var darkTheme: Boolean = false

    override fun onBuilt() {
        super.onBuilt()
        var linkMobileHandlerJob: Job? = null
        var linkDesktopHandlerJob: Job? = null

        lifecycle.subscribe(
            onCreate = {
                linkNewMobileHandler.reset()
                linkNewDesktopHandler.reset()
                @Suppress("AssignedValueIsNeverRead")
                linkMobileHandlerJob = observeLinkNewMobileHandler()
                @Suppress("AssignedValueIsNeverRead")
                linkDesktopHandlerJob = observeLinkNewDesktopHandler()
            },
            onResume = ::onResume,
            onDestroy = {
                linkMobileHandlerJob?.cancel()
                linkDesktopHandlerJob?.cancel()
            }
        )
    }

    sealed interface NavTarget : Parcelable {
        // Will display the not supported state or the device type selection
        @Parcelize
        data object Root : NavTarget

        @Parcelize
        data class MobileShowQrCode(
            val data: String,
        ) : NavTarget

        @Parcelize
        data class CodeConfirmation(
            val code: String,
        ) : NavTarget

        @Parcelize
        data object MobileEnterNumber : NavTarget

        @Parcelize
        data object DesktopNotice : NavTarget

        @Parcelize
        data object DesktopScanQrCode : NavTarget

        @Parcelize
        data object LockScreen : NavTarget

        /** Bipe: the warning, cooldown, biometric check and private MAS web view (ADR 0118). */
        @Parcelize
        data object BipeWebApproval : NavTarget

        /** Bipe: ends the session of a linked device on the MAS account page. */
        @Parcelize
        data class BipeSignOut(val deviceId: String) : NavTarget

        @Parcelize
        data class Error(
            val errorScreenType: ErrorScreenType,
        ) : NavTarget
    }

    private fun observeLinkNewMobileHandler(): Job {
        Timber.tag(tag.value).d("startObservingLinkNewMobileHandler")
        return linkNewMobileHandler.stepFlow
            .onEach { linkMobileStep ->
                Timber.tag(tag.value).d("step: ${linkMobileStep::class.java.simpleName}")
                when (linkMobileStep) {
                    LinkMobileStep.Uninitialized -> Unit
                    LinkMobileStep.CreatingQrCode -> {
                        // This step is handled in LinkNewDeviceRootPresenter
                    }
                    LinkMobileStep.Done -> {
                        callback.onDone()
                    }
                    is LinkMobileStep.Error -> {
                        navigateToError(linkMobileStep.errorType)
                    }
                    is LinkMobileStep.QrReady -> {
                        // The QrCode is ready, navigate to its display, if not already there
                        val navTarget = backstack.elements.value.last().key.navTarget
                        if (navTarget !is NavTarget.MobileShowQrCode) {
                            backstack.push(NavTarget.MobileShowQrCode(linkMobileStep.data))
                        }
                    }
                    LinkMobileStep.QrRotating -> {
                        // This step is handled in ShowQrCodePresenter
                    }
                    is LinkMobileStep.QrScanned -> {
                        backstack.replace(NavTarget.MobileEnterNumber)
                    }
                    LinkMobileStep.Starting -> {
                        // This step is not received at the moment, so do nothing
                    }
                    LinkMobileStep.SyncingSecrets -> Unit
                    is LinkMobileStep.WaitingForAuth -> {
                        navigateToBrowser(linkMobileStep.verificationUri)
                    }
                }
            }
            .launchIn(sessionCoroutineScope)
    }

    private fun observeLinkNewDesktopHandler(): Job {
        Timber.tag(tag.value).d("startObservingLinkNewDesktopHandler")
        return linkNewDesktopHandler.stepFlow.onEach { linkDesktopStep ->
            Timber.tag(tag.value).d("step: ${linkDesktopStep::class.java.simpleName}")
            when (linkDesktopStep) {
                LinkDesktopStep.Done -> {
                    // Bipe: back to a new Linked devices screen, which loads the list again and shows the computer.
                    backstack.newRoot(NavTarget.Root)
                    activity?.let { Toast.makeText(it, BipeR.string.bipe_link_computer_done, Toast.LENGTH_SHORT).show() }
                }
                is LinkDesktopStep.Error -> {
                    navigateToError(linkDesktopStep.errorType)
                }
                is LinkDesktopStep.EstablishingSecureChannel -> {
                    backstack.push(NavTarget.CodeConfirmation(linkDesktopStep.checkCodeString))
                }
                is LinkDesktopStep.InvalidQrCode -> {
                    // This error will be handled by the ScanQrCodeNode
                }
                LinkDesktopStep.Starting -> Unit
                LinkDesktopStep.SyncingSecrets -> Unit
                LinkDesktopStep.Uninitialized -> Unit
                is LinkDesktopStep.WaitingForAuth -> {
                    // Bipe: no browser, it has no MAS session for this account (ADR 0118).
                    if (backstack.elements.value.last().key.navTarget != NavTarget.BipeWebApproval) {
                        backstack.push(NavTarget.BipeWebApproval)
                    }
                }
            }
        }
            .launchIn(sessionCoroutineScope)
    }

    private fun onResume() = sessionCoroutineScope.launch {
        // Application is resumed, if the step is waiting for auth, send the confirmation
        (linkNewMobileHandler.stepFlow.value as? LinkMobileStep.WaitingForAuth)?.let {
            Timber.tag(tag.value).d("Resuming while waiting for auth on mobile, sending confirmation")
            it.continuationMessageSender.confirm()
        }
        // Bipe: the desktop direction confirms from the private web view, after the consent page (ADR 0118).
    }

    private fun navigateToError(errorType: ErrorType) {
        // Map the error to an error screen
        val error = when (errorType) {
            is ErrorType.InvalidCheckCode -> ErrorScreenType.Mismatch2Digits
            is ErrorType.UnsupportedProtocol -> ErrorScreenType.ProtocolNotSupported
            is ErrorType.Cancelled -> ErrorScreenType.Cancelled
            is ErrorType.ConnectionInsecure -> ErrorScreenType.InsecureChannelDetected
            is ErrorType.Expired,
            is ErrorType.NotFound,
            is ErrorType.DeviceNotFound -> ErrorScreenType.Expired
            is ErrorType.OtherDeviceAlreadySignedIn -> ErrorScreenType.OtherDeviceAlreadySignedIn
            // TODO check if we expect to hit this here or if it should be caught earlier on
            is ErrorType.UnsupportedQrCodeType -> ErrorScreenType.UnknownError
            is ErrorType.MissingSecretsBackup,
            is ErrorType.DeviceIdAlreadyInUse,
            is ErrorType.Unknown -> ErrorScreenType.UnknownError
        }
        // It is OK to push on backstack, since when user leaves the error screen, a new root will be set,
        // or the whole flow will be popped.
        backstack.push(NavTarget.Error(error))
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.Root -> {
                // Bipe: Settings > Linked devices is the root. Only the desktop direction: the phone scans the browser's code.
                val callback = object : BipeLinkedDevicesNode.Callback {
                    override fun onLinkComputer() {
                        linkNewDesktopHandler.reset()
                        linkNewDesktopHandler.startTimer()
                        backstack.push(NavTarget.DesktopScanQrCode)
                    }

                    override fun onSignOut(deviceId: String) {
                        backstack.push(NavTarget.BipeSignOut(deviceId))
                    }

                    override fun onDone() {
                        callback.onDone()
                    }
                }
                createNode<BipeLinkedDevicesNode>(buildContext, listOf(callback))
            }
            NavTarget.BipeWebApproval -> {
                val step = linkNewDesktopHandler.stepFlow.value as? LinkDesktopStep.WaitingForAuth
                if (step == null) {
                    createNode<ErrorNode>(buildContext, listOf(errorCallback(), ErrorScreenType.UnknownError))
                } else {
                    val purpose = BipeWebApprovalController.Purpose.ApproveSignIn(step.verificationUri, step.continuationMessageSender)
                    createNode<BipeWebApprovalNode>(buildContext, listOf(BipeWebApprovalNode.Inputs(purpose), bipeWebApprovalCallback()))
                }
            }
            is NavTarget.BipeSignOut -> {
                val purpose = BipeWebApprovalController.Purpose.SignOut(navTarget.deviceId)
                createNode<BipeWebApprovalNode>(buildContext, listOf(BipeWebApprovalNode.Inputs(purpose), bipeWebApprovalCallback()))
            }
            is NavTarget.LockScreen -> {
                deviceUnlockEntryPoint.createNode(this, buildContext)
            }
            NavTarget.DesktopNotice -> {
                val callback = object : DesktopNoticeNode.Callback {
                    override fun navigateBack() {
                        // Stop the timer when the user leaves the screen (user will have to authenticate again)
                        linkNewDesktopHandler.reset()
                        backstack.pop()
                    }

                    override fun navigateToQrCodeScanner() {
                        // Start again the timer when the user enters the next screen (they clicked on "Ready to scan")
                        linkNewDesktopHandler.startTimer()
                        backstack.push(NavTarget.DesktopScanQrCode)
                    }
                }
                createNode<DesktopNoticeNode>(buildContext, listOf(callback))
            }
            NavTarget.DesktopScanQrCode -> {
                val callback = object : ScanQrCodeNode.Callback {
                    override fun cancel() {
                        // start again the timer when the user goes back to the DesktopNoticeNode
                        linkNewDesktopHandler.startTimer()
                        backstack.pop()
                    }
                }
                createNode<ScanQrCodeNode>(buildContext, listOf(callback))
            }
            NavTarget.MobileEnterNumber -> {
                val callback = object : EnterNumberNode.Callback {
                    override fun navigateToWrongNumberError() {
                        backstack.push(NavTarget.Error(ErrorScreenType.Mismatch2Digits))
                    }

                    override fun navigateBack() {
                        backstack.pop()
                    }
                }
                createNode<EnterNumberNode>(buildContext, listOf(callback))
            }
            is NavTarget.CodeConfirmation -> {
                val callback = object : CodeConfirmationNode.Callback {
                    override fun onCancel() {
                        // Push error
                        backstack.push(NavTarget.Error(ErrorScreenType.Cancelled))
                    }
                }
                val inputs = CodeConfirmationNode.Inputs(
                    code = navTarget.code,
                )
                createNode<CodeConfirmationNode>(buildContext, listOf(inputs, callback))
            }
            is NavTarget.MobileShowQrCode -> {
                val callback = object : ShowQrCodeNode.Callback {
                    override fun navigateBack() {
                        linkNewMobileHandler.reset()
                        backstack.pop()
                    }
                }
                val inputs = ShowQrCodeNode.Inputs(
                    data = navTarget.data,
                )
                createNode<ShowQrCodeNode>(buildContext, listOf(inputs, callback))
            }
            is NavTarget.Error -> {
                createNode<ErrorNode>(buildContext, listOf(errorCallback(), navTarget.errorScreenType))
            }
        }
    }

    private fun errorCallback() = object : ErrorNode.Callback {
        override fun onRetry() {
            linkNewMobileHandler.reset()
            linkNewDesktopHandler.reset()
            backstack.newRoot(NavTarget.Root)
        }

        override fun onCancel() {
            linkNewMobileHandler.reset()
            linkNewDesktopHandler.reset()
            callback.onDone()
        }
    }

    /** The web view closed. An approval ends through the SDK steps; the list shows again meanwhile. */
    private fun bipeWebApprovalCallback() = object : BipeWebApprovalNode.Callback {
        override fun onFinished() {
            backstack.newRoot(NavTarget.Root)
        }
    }

    private suspend fun navigateToBrowser(url: String) {
        activity?.openUrlInChromeCustomTab(
            session = null,
            darkTheme = darkTheme,
            url = sessionEnterpriseService.tweakMasUrl(url),
        )
    }

    @Composable
    override fun View(modifier: Modifier) {
        activity = requireNotNull(LocalActivity.current)
        darkTheme = !ElementTheme.isLightTheme
        DisposableEffect(Unit) {
            onDispose {
                activity = null
            }
        }
        BackstackView()
        PermanentChild(permanentNavModel = permanentNavModel, navTarget = NavTarget.LockScreen)
    }
}
