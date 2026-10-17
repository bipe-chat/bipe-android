/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.element.android.compound.theme.Theme
import io.element.android.compound.theme.mapToTheme
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Bipe: the appearance setting, read by the root and by Conversas e mídia (ADR 0112). Moved out of [PreferencesRootPresenter]. */
internal data class ThemeOptions(
    val selected: ThemeOption,
    val available: ImmutableList<ThemeOption>,
)

@Composable
internal fun rememberThemeOptions(
    featureFlagService: FeatureFlagService,
    appPreferencesStore: AppPreferencesStore,
): ThemeOptions {
    val isBlackThemeAllowed by remember {
        featureFlagService.isFeatureEnabledFlow(FeatureFlags.AllowBlackTheme)
    }.collectAsState(initial = false)
    val theme by remember(isBlackThemeAllowed) {
        appPreferencesStore.getThemeFlow().mapToTheme(isBlackThemeAllowed)
    }.collectAsState(initial = Theme.System)
    val available = remember(isBlackThemeAllowed) {
        if (isBlackThemeAllowed) {
            ThemeOption.entries
        } else {
            ThemeOption.entries.filterNot { it == ThemeOption.Black }
        }.toImmutableList()
    }
    val selected = when (theme) {
        Theme.System -> ThemeOption.System
        Theme.Dark -> ThemeOption.Dark
        Theme.Black -> ThemeOption.Black
        Theme.Light -> ThemeOption.Light
    }
    return ThemeOptions(selected = selected, available = available)
}

internal suspend fun AppPreferencesStore.setTheme(option: ThemeOption) {
    when (option) {
        ThemeOption.System -> setTheme(Theme.System.name)
        ThemeOption.Dark -> setTheme(Theme.Dark.name)
        ThemeOption.Black -> setTheme(Theme.Black.name)
        ThemeOption.Light -> setTheme(Theme.Light.name)
    }
}
