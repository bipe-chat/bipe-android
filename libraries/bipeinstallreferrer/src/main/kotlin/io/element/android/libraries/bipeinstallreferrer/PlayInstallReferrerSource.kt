/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipeinstallreferrer

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import io.element.android.libraries.bipe.onboarding.BipeInstallReferrerSource
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * The `referrer` of the Play store link that installed the app. The invite page puts `convite/<code>` there (bipe-chat/bipe#197).
 * Google's Install Referrer library talks only to the Play Store app on the phone; it sends nothing to a third party.
 */
@ContributesIntoSet(AppScope::class, binding = binding<BipeInstallReferrerSource>())
class PlayInstallReferrerSource(
    @ApplicationContext private val context: Context,
) : BipeInstallReferrerSource {
    override suspend fun referrer(): String? = suspendCancellableCoroutine { continuation ->
        val client = InstallReferrerClient.newBuilder(context).build()
        fun finish(value: String?) {
            client.endConnection()
            if (continuation.isActive) continuation.resume(value)
        }
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val referrer = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        runCatching { client.installReferrer.installReferrer }.getOrNull()
                    } else {
                        null
                    }
                    finish(referrer)
                }

                override fun onInstallReferrerServiceDisconnected() = finish(null)
            })
        } catch (error: Exception) {
            Timber.w(error, "Bipe: install referrer unavailable")
            finish(null)
        }
        continuation.invokeOnCancellation { client.endConnection() }
    }
}
