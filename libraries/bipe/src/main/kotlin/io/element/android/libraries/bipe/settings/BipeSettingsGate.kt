/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.settings

import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.generated.BipeSettingsRow
import io.element.android.libraries.bipe.generated.BipeSettingsSection

/** The `gate` of a row in bipe-shared/registries/settings.json, read on Android (ADR 0112). */
object BipeSettingsGate {
    /**
     * `null` and `android`: shown. `ios`: hidden. `flag:<name>`: shown when [isFlagEnabled] says so.
     * `planned:<issue>`: hidden. An unknown gate hides the row (fail closed).
     */
    fun isShown(gate: String?, isFlagEnabled: (String) -> Boolean): Boolean = when {
        gate == null || gate == "android" -> true
        gate == "ios" -> false
        gate.startsWith(FLAG_PREFIX) -> isFlagEnabled(gate.removePrefix(FLAG_PREFIX))
        else -> false
    }

    /** The rows of [section] that this build shows. */
    fun visibleRows(section: BipeSettingsSection, isFlagEnabled: (String) -> Boolean): List<BipeSettingsRow> =
        section.rows.filter { isShown(it.gate, isFlagEnabled) }

    private const val FLAG_PREFIX = "flag:"
}

/** The page a url row opens, in the app language. */
fun BipeSettingsRow.localizedUrl(): String? = url(BipeApiClient.appLanguage())
