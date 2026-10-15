/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.call.impl.BuildConfig
import io.element.android.libraries.matrix.api.widget.CallAnalyticCredentialsProvider

@ContributesBinding(AppScope::class)
class DefaultCallAnalyticCredentialsProvider : CallAnalyticCredentialsProvider {
    // Bipe: no Sentry or PostHog in Element Call (bipe#55).
    override val posthogUserId: String? = null
    override val posthogApiHost: String? = null
    override val posthogApiKey: String? = null
    override val rageshakeSubmitUrl: String? = BuildConfig.RAGESHAKE_URL.takeIf { it.isNotBlank() }
    override val sentryDsn: String? = null
}
