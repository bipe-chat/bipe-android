/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

object BuildTimeConfig {
    const val APPLICATION_ID = "chat.bipe.app" // Bipe. Debug builds add ".debug". Do not upload this ID before the Bipe organisation owns the Play account (ADR 0061).
    const val APPLICATION_NAME = "Bipe" // Bipe: product name Bipe (ADR 0054)
    // Bipe: OAuth redirect scheme. MAS requires it to be the reverse of the client URI host (bipe.chat) or below it.
    val METADATA_HOST_REVERSED: String? = "chat.bipe.app"
    val OAUTH_CLIENT_URL_PATH: String? = "apps/android"
    val URL_WEBSITE: String? = "https://bipe.chat" // Bipe: MAS requires the OAuth URLs on the client URI host
    val URL_LOGO: String? = "https://bipe.chat/icone.png" // Bipe
    val URL_COPYRIGHT: String? = "https://bipe.chat/sobre" // Bipe: the About page names the steward (ADR 0040)
    val URL_ACCEPTABLE_USE: String? = "https://bipe.chat/termos" // Bipe
    val URL_PRIVACY: String? = "https://bipe.chat/privacidade" // Bipe
    val URL_POLICY: String? = "https://bipe.chat/privacidade" // Bipe
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
    // Bipe: Sygnal app IDs, same scheme as iOS (<base id>.ios.prod / .ios.dev). Sygnal must list them.
    val PUSHER_APP_ID_RELEASE: String? = "chat.bipe.app.android.prod"
    val PUSHER_APP_ID_DEBUG: String? = "chat.bipe.app.android.dev"
    val PUSHER_APP_ID_NIGHTLY: String? = "chat.bipe.app.android.dev"
}
