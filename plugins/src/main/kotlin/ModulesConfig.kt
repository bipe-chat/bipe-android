/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

import config.BuildTimeConfig
import config.PushProvidersConfig

object ModulesConfig {
    val pushProvidersConfig = PushProvidersConfig(
        includeFirebase = BuildTimeConfig.PUSH_CONFIG_INCLUDE_FIREBASE,
        includeUnifiedPush = BuildTimeConfig.PUSH_CONFIG_INCLUDE_UNIFIED_PUSH,
    )
}
