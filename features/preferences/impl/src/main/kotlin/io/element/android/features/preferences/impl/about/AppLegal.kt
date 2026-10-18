/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2021-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import androidx.annotation.StringRes
import io.element.android.features.preferences.impl.BuildConfig
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private const val COPYRIGHT_URL = BuildConfig.URL_COPYRIGHT
private const val USE_POLICY_URL = BuildConfig.URL_ACCEPTABLE_USE
private const val PRIVACY_URL = BuildConfig.URL_PRIVACY

/** Bipe: was ElementLegal. The links are bipe.chat pages from BuildTimeConfig, with no element.io fallback (ADR 0112). */
sealed class AppLegal(
    @StringRes val titleRes: Int,
    val url: String,
) {
    data object Copyright : AppLegal(CommonStrings.common_copyright, COPYRIGHT_URL)
    data object AcceptableUsePolicy : AppLegal(CommonStrings.common_acceptable_use_policy, USE_POLICY_URL)
    data object PrivacyPolicy : AppLegal(CommonStrings.common_privacy_policy, PRIVACY_URL)
}

fun getAllLegals(): ImmutableList<AppLegal> {
    return persistentListOf(
        AppLegal.Copyright,
        AppLegal.AcceptableUsePolicy,
        AppLegal.PrivacyPolicy,
    )
}
