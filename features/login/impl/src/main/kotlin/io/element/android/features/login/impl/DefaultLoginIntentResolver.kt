/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.appconfig.ProseioConfig
import io.element.android.features.login.api.LoginIntentResolver
import io.element.android.features.login.api.LoginParams
import io.element.android.libraries.proseio.ProseioInviteLink

@ContributesBinding(AppScope::class)
class DefaultLoginIntentResolver : LoginIntentResolver {
    override fun parse(uriString: String): LoginParams? {
        // Proseio: invite links open the sign-up screen with the code filled in.
        ProseioInviteLink.code(uriString)?.let { code ->
            return LoginParams(accountProvider = ProseioConfig.SERVER_NAME, loginHint = null, proseioInviteCode = code)
        }
        val uri = uriString.toUri()
        if (uri.host != "mobile.element.io") return null
        if (uri.path.orEmpty().startsWith("/element").not()) return null
        val accountProvider = uri.getQueryParameter("account_provider") ?: return null
        val loginHint = uri.getQueryParameter("login_hint")
        return LoginParams(
            accountProvider = accountProvider,
            loginHint = loginHint,
        )
    }
}
