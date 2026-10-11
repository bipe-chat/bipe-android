/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

object BuildTimeConfig {
    const val APPLICATION_ID = "br.com.proseio.app" // Proseio. Debug builds add ".debug". Do not upload this ID before the Proseio organisation owns the Play account.
    const val APPLICATION_NAME = "Proseio" // Proseio
    // Proseio: OAuth redirect scheme. MAS requires it to be the reverse of the client URI host (proseio.com.br) or below it.
    val METADATA_HOST_REVERSED: String? = "br.com.proseio.app"
    val OAUTH_CLIENT_URL_PATH: String? = "apps/android"
    val URL_WEBSITE: String? = "https://proseio.com.br" // Proseio: MAS requires the OAuth URLs on the client URI host
    val URL_LOGO: String? = "https://proseio.com.br/icone.png" // Proseio
    val URL_COPYRIGHT: String? = "https://proseio.com.br/direitos" // Proseio
    val URL_ACCEPTABLE_USE: String? = "https://proseio.com.br/termos" // Proseio
    val URL_PRIVACY: String? = "https://proseio.com.br/privacidade" // Proseio
    val URL_POLICY: String? = "https://proseio.com.br/privacidade" // Proseio
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = null
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = null
    val SERVICES_MAPTILER_DARK_MAPID: String? = null
    val SERVICES_POSTHOG_HOST: String? = null
    val SERVICES_POSTHOG_APIKEY: String? = null
    val SERVICES_SENTRY_DSN: String? = null
    val SERVICES_SENTRY_DSN_RUST: String? = null
    val BUG_REPORT_URL: String? = null
    val BUG_REPORT_APP_NAME: String? = null
    const val PUSH_CONFIG_INCLUDE_FIREBASE: Boolean = true
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH: Boolean = true
    // Proseio: Sygnal app IDs, same scheme as iOS (<base id>.ios.prod / .ios.dev). Sygnal must list them.
    val PUSHER_APP_ID_RELEASE: String? = "br.com.proseio.app.android.prod"
    val PUSHER_APP_ID_DEBUG: String? = "br.com.proseio.app.android.dev"
    val PUSHER_APP_ID_NIGHTLY: String? = "br.com.proseio.app.android.dev"
}
