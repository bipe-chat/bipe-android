/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint

interface LoginEntryPoint : FeatureEntryPoint {
    data class Params(
        val accountProvider: String?,
        val loginHint: String?,
        val bipeInviteCode: String? = null, // Bipe
        val bipeRecoveryToken: String? = null, // Bipe: e-mail recovery link (ADR 0057)
    ) {
        // Bipe: the recovery token is a secret.
        override fun toString(): String = "Params(accountProvider=$accountProvider, hasRecoveryToken=${bipeRecoveryToken != null})"
    }

    interface Callback : Plugin {
        fun navigateToBugReport()
        fun onDone()
    }

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node
}
