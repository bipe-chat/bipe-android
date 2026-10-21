/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.onboarding

import android.content.ClipboardManager
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.element.android.libraries.bipe.BipeInviteCode
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext

/** What the start screen reads before it picks its variant (bipe-chat/bipe#415, ADR 0072). Every read fails soft to null. */
interface BipeStartScreenInfo {
    /** `GET /v1/signup/config`. Null when the server does not answer: the start screen keeps the invite-first variant. */
    suspend fun isInviteRequired(): Boolean?

    /** The inviter's handle (without @) for a valid personal invite, from `GET /v1/invites/{code}`. */
    suspend fun inviterHandle(code: String): String?

    /** bipe-chat/bipe#197: a valid invite code from the Play Install Referrer (`referrer=convite/<code>`). Null in the F-Droid build. */
    suspend fun installReferrerInviteCode(): String?

    /** bipe-chat/bipe#197: the clipboard holds text. Reads only the clip description: no clipboard read, no system toast. */
    fun clipboardHasText(): Boolean

    /** bipe-chat/bipe#197: the invite code in the clipboard. Reads the clipboard: call it only on a tap. */
    fun clipboardInviteCode(): String?
}

/** The Play Install Referrer. gplay flavor only (libraries/bipeinstallreferrer); the F-Droid build has none. */
interface BipeInstallReferrerSource {
    /** The `referrer` value of the Play store link, or null. */
    suspend fun referrer(): String?
}

@ContributesTo(AppScope::class)
interface BipeStartScreenBindings {
    @Multibinds(allowEmpty = true)
    fun bipeInstallReferrerSources(): Set<BipeInstallReferrerSource>
}

@ContributesBinding(AppScope::class)
class DefaultBipeStartScreenInfo(
    @ApplicationContext private val context: Context,
    private val apiClient: BipeApiClient,
    private val installReferrerSources: Set<BipeInstallReferrerSource>,
) : BipeStartScreenInfo {
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    override suspend fun isInviteRequired(): Boolean? = runCatchingExceptions { apiClient.signupConfig().inviteRequired }.getOrNull()

    override suspend fun inviterHandle(code: String): String? =
        runCatchingExceptions { apiClient.invitePreview(code) }.getOrNull()?.takeIf { it.valid }?.inviterHandle

    // The referrer stays for 90 days after the install, also after sign-up: only a code the server still accepts counts.
    override suspend fun installReferrerInviteCode(): String? {
        val referrer = installReferrerSources.firstOrNull()?.referrer() ?: return null
        val code = BipeInviteCode.parse(referrer)?.value ?: return null
        return code.takeIf { runCatchingExceptions { apiClient.invitePreview(it) }.getOrNull()?.valid == true }
    }

    override fun clipboardHasText(): Boolean =
        clipboard?.primaryClipDescription?.hasMimeType("text/*") == true

    override fun clipboardInviteCode(): String? {
        val clip = clipboard?.primaryClip ?: return null
        return (0 until clip.itemCount).firstNotNullOfOrNull { index ->
            clip.getItemAt(index).coerceToText(context)?.toString()?.let(BipeInviteCode::parse)?.value
        }
    }
}
