/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

// Bipe: no help pages yet, so these are null and the "Learn more" links they feed are hidden (bipe#205, as iOS).
object LearnMoreConfig {
    val ENCRYPTION_URL: String? = null
    val DEVICE_VERIFICATION_URL: String? = null
    val SECURE_BACKUP_URL: String? = null
    val IDENTITY_CHANGE_URL: String? = null
    val HISTORY_VISIBLE_URL: String? = null
}
