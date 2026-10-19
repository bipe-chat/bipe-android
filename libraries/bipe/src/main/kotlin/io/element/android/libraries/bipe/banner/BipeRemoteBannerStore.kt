/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.banner

import android.content.Context
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeRemoteBanner
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** What the chat list shows for the remote critical banner (ADR 0117, bipe-chat/bipe#386). */
data class BipeRemoteBannerShown(
    val title: String,
    val text: String,
    val url: String,
    /** An update banner opens the update link and has no close button. */
    val isUpdate: Boolean,
)

/**
 * The remote critical banner above the chat list, the same as iOS: an outage, a forced update or a change of terms.
 * Read on launch and on each return to the foreground. A failed read keeps the banner that is on screen.
 */
@SingleIn(SessionScope::class)
@Inject
class BipeRemoteBannerStore(
    private val bipeSession: BipeSession,
    @ApplicationContext private val context: Context,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val buildMeta: BuildMeta,
) {
    private var banner: BipeRemoteBanner? = null
    private val _shown = MutableStateFlow<BipeRemoteBannerShown?>(null)

    val shown: StateFlow<BipeRemoteBannerShown?> = _shown.asStateFlow()

    fun refresh() {
        sessionCoroutineScope.launch {
            try {
                banner = bipeSession.apiClient.remoteBanner()
            } catch (error: BipeApiException) {
                Timber.w(error, "Bipe remote banner: read failed")
            }
            update()
        }
    }

    /** A notice stays closed until a banner with a new id. An update banner cannot be dismissed. */
    fun dismiss() {
        val current = banner ?: return
        if (_shown.value?.isUpdate != false) return
        preferences().edit().putString(KEY_DISMISSED_ID, current.id).apply()
        _shown.value = null
    }

    private fun update() {
        val current = banner
        _shown.value = when (val state = current?.state(buildMeta.versionName)) {
            null, BipeRemoteBanner.State.Hidden -> null
            BipeRemoteBanner.State.Notice -> if (preferences().getString(KEY_DISMISSED_ID, null) == current.id) {
                null
            } else {
                BipeRemoteBannerShown(current.title, current.text, current.link, isUpdate = false)
            }
            is BipeRemoteBanner.State.Update -> BipeRemoteBannerShown(current.title, current.text, state.url, isUpdate = true)
        }
    }

    private fun preferences() = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val FILE_NAME = "bipe_remote_banner"
        const val KEY_DISMISSED_ID = "dismissed_id"
    }
}
