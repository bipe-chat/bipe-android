/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.rageshake.impl.rageshake

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Bipe: bug reports are off (empty rageshake URL), so shake detection is off too.
 * This removes the seismic dependency.
 */
@SingleIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, binding = binding<RageShake>())
class DefaultRageShake : RageShake {
    override fun setInterceptor(interceptor: (() -> Unit)?) = Unit

    override fun isAvailable(): Boolean = false

    override fun start(sensitivity: Float) = Unit

    override fun stop() = Unit

    override fun setSensitivity(sensitivity: Float) = Unit
}
