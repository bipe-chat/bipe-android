/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.weblink

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.linknewdevice.ContinuationMessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant

/**
 * The private web view that approves a web sign-in, or ends the session of a linked device, on the MAS pages
 * (ADR 0116, ADR 0118). Same steps as iOS `BipeWebApprovalModel`.
 *
 * - Approve: the full-access warning and the cooldown, the biometric check, then the MAS page. The user approves on
 *   the MAS consent page; the place of the browser IP shows above it.
 * - Sign out: the biometric check, then the MAS account page that ends the session of one device.
 *
 * The web view has its own WebView profile (or, on an old WebView, cleared cookies and storage) and loads only the MAS
 * host. It fills the MAS login form once with the hidden password and never shows it. When the flow ends, bipe-api ends
 * the MAS browser session it made.
 */
class BipeWebApprovalController(
    val purpose: Purpose,
    private val webLink: BipeWebLinkSession,
    private val scope: CoroutineScope,
) {
    sealed interface Purpose {
        data class ApproveSignIn(val verificationUri: String, val continuation: ContinuationMessageSender) : Purpose
        data class SignOut(val deviceId: String) : Purpose
    }

    enum class Step {
        /** The full-access warning. The continue button waits until the cooldown ends. */
        WARNING,

        /** The biometric check, or a MAS page that the user must not see (the login form). */
        LOADING,

        /** A MAS page that the user can see. */
        PAGE,

        /** The user left the consent page. The SDK sends the secrets to the browser. */
        FINISHING,
        FAILED,
    }

    private val _step = MutableStateFlow(if (purpose is Purpose.ApproveSignIn) Step.WARNING else Step.LOADING)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val _secondsLeft = MutableStateFlow(BipeRegistries.WEB_LINK_COOLDOWN_SECONDS)
    val secondsLeft: StateFlow<Int> = _secondsLeft.asStateFlow()

    /** `true` on the MAS consent page of the grant. */
    private val _isConsentPage = MutableStateFlow(false)
    val isConsentPage: StateFlow<Boolean> = _isConsentPage.asStateFlow()

    /** The approximate place of the browser IP. `null` while unknown; an empty string when the server does not know it. */
    private val _place = MutableStateFlow<String?>(null)
    val place: StateFlow<String?> = _place.asStateFlow()

    /** Called when the sign-out page closes or cannot start. The approval flow ends through the SDK. */
    var onFinished: (() -> Unit)? = null

    private var webView: WebView? = null
    private var masHost: String? = null
    private var startedAt: Instant? = null
    private var password: String? = null
    private var hasSubmittedLogin = false
    private var hasSeenConsentPage = false
    private var hasEnded = false
    private var countdownJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (purpose is Purpose.ApproveSignIn) {
            countdownJob = scope.launch {
                while (_secondsLeft.value > 0) {
                    delay(1_000)
                    _secondsLeft.value -= 1
                }
            }
        }
    }

    /** The view gives its WebView before anything loads. */
    @SuppressLint("SetJavaScriptEnabled")
    fun attach(webView: WebView) {
        if (this.webView === webView) return
        this.webView = webView
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.setProfile(webView, PROFILE_NAME)
        }
        clearWebData()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // No password manager may save the hidden password.
            webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        webView.webViewClient = Client()
    }

    /** After the biometric check (or its failure). The hidden password is read only after it passes (ADR 0118). */
    fun onOwnerVerified(passed: Boolean) {
        if (!passed) {
            if (purpose is Purpose.SignOut) {
                end()
                onFinished?.invoke()
            } else {
                _step.value = Step.WARNING
            }
            return
        }
        _step.value = Step.LOADING
        scope.launch { open() }
    }

    /** The close button. Before the consent page the grant is cancelled; after it the SDK continues. */
    fun close() {
        val purpose = purpose
        scope.launch {
            if (purpose is Purpose.ApproveSignIn) {
                if (hasSeenConsentPage) purpose.continuation.confirm() else purpose.continuation.cancel()
            }
            end()
            onFinished?.invoke()
        }
    }

    /** Ends the MAS browser session that this web view made. Safe to call more than once. */
    fun end() {
        if (hasEnded) return
        hasEnded = true
        countdownJob?.cancel()
        password = null
        webView?.let {
            it.stopLoading()
            it.webViewClient = WebViewClient()
        }
        clearWebData()
        val since = startedAt ?: return
        scope.launch { webLink.finish(since) }
    }

    private suspend fun open() {
        val metadata = webLink.authMetadata().getOrNull()
        val host = metadata?.host
        val password = webLink.hiddenPassword()
        val url = when (purpose) {
            is Purpose.ApproveSignIn -> Uri.parse(purpose.verificationUri)
            is Purpose.SignOut -> metadata?.accountManagementUri?.let { BipeWebLink.sessionEndUrl(it, purpose.deviceId) }
        }
        val webView = webView
        if (password == null || webView == null) {
            Timber.e("Bipe web link: no hidden password on this device")
            fail()
            return
        }
        if (host == null || url == null || !BipeWebLink.isAllowed(url, host)) {
            Timber.e("Bipe web link: cannot open the MAS page (no metadata or another host)")
            fail()
            return
        }
        this.password = password
        masHost = host
        startedAt = Instant.now()
        webView.loadUrl(url.toString())
    }

    private fun fail() {
        _step.value = Step.FAILED
        val purpose = purpose
        if (purpose is Purpose.ApproveSignIn) {
            scope.launch { purpose.continuation.cancel() }
        }
        end()
    }

    private fun clearWebData() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profile = ProfileStore.getInstance().getOrCreateProfile(PROFILE_NAME)
            profile.cookieManager.removeAllCookies(null)
            profile.webStorage.deleteAllData()
        } else {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    private fun inspectPage(view: WebView, url: Uri) {
        view.evaluateJavascript(INSPECT_SCRIPT) { result ->
            val page = runCatchingExceptions { json.parseToJsonElement(result).jsonObject }.getOrNull()
            if (page?.get("hasPasswordForm")?.jsonPrimitive?.booleanOrNull == true) {
                submitLoginForm(view)
            } else {
                showPage(
                    url = url,
                    hasForm = page?.get("hasForm")?.jsonPrimitive?.booleanOrNull == true,
                    text = page?.get("text")?.jsonPrimitive?.content.orEmpty(),
                )
            }
        }
    }

    /** Fills and submits the MAS login form once. A second login form means the password did not work. */
    private fun submitLoginForm(view: WebView) {
        val password = password
        val username = BipeWebLink.localpart(webLink.userId)
        if (hasSubmittedLogin || password == null || username == null) {
            Timber.e("Bipe web link: the MAS login form showed again")
            fail()
            return
        }
        hasSubmittedLogin = true
        this.password = null
        // JSONObject.quote makes each value a JS string literal. Never log this script.
        val script = SUBMIT_SCRIPT.replace("__USERNAME__", JSONObject.quote(username)).replace("__PASSWORD__", JSONObject.quote(password))
        view.evaluateJavascript(script) { result ->
            if (result != "true") {
                Timber.e("Bipe web link: the MAS login form has no username field")
                fail()
            }
        }
    }

    private fun showPage(url: Uri, hasForm: Boolean, text: String) {
        val purpose = purpose
        if (purpose !is Purpose.ApproveSignIn) {
            _step.value = Step.PAGE
            return
        }
        val isConsent = BipeWebLink.isDeviceConsentPath(url) && hasForm
        when {
            isConsent -> {
                _isConsentPage.value = true
                _step.value = Step.PAGE
                if (!hasSeenConsentPage) {
                    hasSeenConsentPage = true
                    scope.launch { loadPlace(text) }
                }
            }
            hasSeenConsentPage -> {
                // The user approved or declined on the consent page. The SDK continues (ADR 0118).
                _isConsentPage.value = false
                _step.value = Step.FINISHING
                scope.launch { purpose.continuation.confirm() }
                end()
            }
            else -> _step.value = Step.PAGE
        }
    }

    private suspend fun loadPlace(text: String) {
        val ip = BipeWebLink.firstIpAddress(text)
        _place.value = ip?.let { webLink.place(it)?.label() }.orEmpty()
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val host = masHost ?: return true
            val blocked = !BipeWebLink.isAllowed(request.url, host)
            if (blocked) Timber.i("Bipe web link: blocked a navigation off the MAS host")
            return blocked
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            val host = masHost
            // A form post does not pass shouldOverrideUrlLoading, so the host is checked here too.
            if (host == null || url == null || !BipeWebLink.isAllowed(Uri.parse(url), host)) {
                view.stopLoading()
                return
            }
            if (_step.value == Step.PAGE) _step.value = Step.LOADING
        }

        override fun onPageFinished(view: WebView, url: String?) {
            val host = masHost ?: return
            val uri = url?.let(Uri::parse) ?: return
            if (hasEnded || !BipeWebLink.isAllowed(uri, host)) return
            inspectPage(view, uri)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame || hasEnded) return
            Timber.e("Bipe web link: page load failed: ${error.errorCode}")
            fail()
        }
    }

    private companion object {
        const val PROFILE_NAME = "bipe-web-link"

        const val INSPECT_SCRIPT = """
            (function() {
                return {
                    hasPasswordForm: document.querySelector('form input[name="password"]') !== null,
                    hasForm: document.querySelector('form') !== null,
                    text: document.body ? document.body.innerText : ''
                };
            })()
        """

        const val SUBMIT_SCRIPT = """
            (function() {
                const field = document.querySelector('form input[name="password"]');
                const form = field.form;
                const user = form.querySelector('input[name="username"]');
                if (!user) { return false; }
                user.value = __USERNAME__;
                field.value = __PASSWORD__;
                if (form.requestSubmit) { form.requestSubmit(); } else { form.submit(); }
                return true;
            })()
        """
    }
}
