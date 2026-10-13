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
import io.element.android.appconfig.BipeConfig
import io.element.android.features.login.api.LoginIntentResolver
import io.element.android.features.login.api.LoginParams
import io.element.android.libraries.bipe.BipeInviteLink
import io.element.android.libraries.bipe.BipeRecoveryLink

@ContributesBinding(AppScope::class)
class DefaultLoginIntentResolver : LoginIntentResolver {
    override fun parse(uriString: String): LoginParams? {
        // Bipe: recovery links from the e-mail (ADR 0057) open the recovery screen.
        BipeRecoveryLink.token(uriString)?.let { token ->
            return LoginParams(accountProvider = BipeConfig.SERVER_NAME, loginHint = null, bipeRecoveryToken = token)
        }
        // Bipe: invite links open the sign-up screen with the code filled in.
        BipeInviteLink.code(uriString)?.let { code ->
            return LoginParams(accountProvider = BipeConfig.SERVER_NAME, loginHint = null, bipeInviteCode = code)
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
