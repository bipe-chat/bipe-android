/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.appconfig

import io.element.android.libraries.matrix.api.accountprovider.AccountProvider

/** Bipe servers. Same values as AppSettings on iOS. */
object BipeConfig {
    const val SERVER_NAME = "proseio.com.br"
    const val HOMESERVER_BASE_URL = "https://matrix.proseio.com.br"

    /** Bipe API (sign-up, handles, invites). Contract: proseio-shared/api/openapi.yaml. */
    const val API_BASE_URL = "https://api.proseio.com.br"

    /** The only account provider. Closed federation: the server picker is hidden. */
    val accountProvider = AccountProvider.Managed(
        serverName = SERVER_NAME,
        baseUrl = HOMESERVER_BASE_URL,
        canUseServerName = true,
    )
}
