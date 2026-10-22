/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.bipe.BipeVersionFooter
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.bipe.BipeTrustProfile
import io.element.android.libraries.bipe.api.BipeWhoCanMessage
import io.element.android.libraries.bipe.generated.BipeSettingsGroup
import io.element.android.libraries.bipe.handle.BipeInvitesRow
import io.element.android.libraries.bipe.selfchat.BipeSelfChatRow
import io.element.android.libraries.bipe.settings.BipeWayBack
import io.element.android.libraries.bipe.govbr.BipeGovbrSheet
import io.element.android.libraries.bipe.ui.BipeTrustProfileCard
import io.element.android.libraries.designsystem.components.avatar.AvatarRow
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithLargeHeight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.toImmutableList
import io.element.android.libraries.bipe.R as BipeR

@Composable
fun PreferencesRootView(
    state: PreferencesRootState,
    onBackClick: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenRageShake: () -> Unit,
    onEditProfileClick: (MatrixUser) -> Unit,
    modifier: Modifier = Modifier,
    onBipeHandleClick: () -> Unit = {},
    onBipeInvitesClick: () -> Unit = {},
    // Bipe: Seu perfil and the group screens under "Configurações" (ADR 0112).
    onBipeProfileClick: () -> Unit = {},
    onBipeGroupClick: (BipeSettingsGroup) -> Unit = {},
    // Bipe: the Você tab (ADR 0053) shows this screen without its top bar, with the self-chat row first.
    bipeTabContentPadding: PaddingValues? = null,
    onBipeSelfChatClick: (() -> Unit)? = null,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)

    Box(modifier = modifier) {
        // Bipe: the Você root (ADR 0112). Who you are first, then the setting groups. No Element section headers.
        val pageContent: @Composable ColumnScope.() -> Unit = {
            BipeYouHeader(
                modifier = Modifier.testTag(TestTags.settingsUserProfile),
                matrixUser = state.myUser,
                handle = state.bipeHandle,
                onClick = onBipeProfileClick,
            )
            if (state.otherSessions.isNotEmpty()) {
                MultiAccountSection(state = state)
            }
            onBipeSelfChatClick?.let { BipeSelfChatRow(onClick = it) }
            BipeTrustProfileCard(
                profile = BipeTrustProfile.create(
                    displayName = state.myUser.displayName,
                    hasAvatar = state.myUser.avatarUrl != null,
                    handle = state.bipeHandle,
                    isCompany = state.bipeIsCompany,
                    business = state.bipeBusiness,
                    personBadge = state.bipePersonBadge,
                    govbrEnabled = state.bipeGovbrEnabled,
                ),
                onEditProfileClick = { onEditProfileClick(state.myUser) },
                onHandleClick = onBipeHandleClick,
                onGovbrClick = { state.eventSink(PreferencesRootEvent.BipeOpenGovbr) },
            )
            state.bipeGovbrFlow?.let { flow ->
                BipeGovbrSheet(flow = flow, onDismiss = { state.eventSink(PreferencesRootEvent.BipeCloseGovbr) })
            }
            BipeInvitesRow(onClick = onBipeInvitesClick)
            BipeSettingsGroupRows(state = state, onGroupClick = onBipeGroupClick)
            DeveloperSection(
                state = state,
                onOpenAnalytics = onOpenAnalytics,
                onOpenDeveloperSettings = onOpenDeveloperSettings,
                onOpenLabs = onOpenLabs,
                onOpenRageShake = onOpenRageShake,
            )
            BipeVersionFooter(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                version = state.version,
                deviceId = state.bipeDeviceId,
                onClick = if (!state.showDeveloperSettings) {
                    { state.eventSink(PreferencesRootEvent.OnVersionInfoClick) }
                } else {
                    null
                }
            )
        }
        if (bipeTabContentPadding != null) {
            // Bipe: the Você tab. The home screen has the top bar and the snackbar host.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bipeTabContentPadding),
                content = pageContent,
            )
        } else {
            // Bipe: the classic settings root shows the same structure as the Você tab (ADR 0112).
            PreferencePage(
                onBackClick = onBackClick,
                title = stringResource(id = BipeR.string.bipe_settings_section),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                content = pageContent,
            )
        }
    }
}

/** Bipe: section "Configurações" with one row per group, each with its current state as the subtitle (ADR 0112). */
@Composable
private fun BipeSettingsGroupRows(
    state: PreferencesRootState,
    onGroupClick: (BipeSettingsGroup) -> Unit,
) {
    PreferenceCategory(title = stringResource(BipeR.string.bipe_settings_section)) {
        for (group in BipeSettingsGroup.root) {
            val subtitle = when (group) {
                BipeSettingsGroup.PRIVACY -> state.bipeWhoCanMessage?.let { stringResource(it.summaryRes) }
                BipeSettingsGroup.ACCOUNT -> state.bipeWaysBackCount?.let { count ->
                    if (count == 0) {
                        stringResource(BipeR.string.bipe_settings_account_paths_none)
                    } else {
                        stringResource(BipeR.string.bipe_settings_account_paths, count.toString())
                    }
                }
                BipeSettingsGroup.NOTIFICATIONS -> state.bipeNotificationsOn?.let {
                    stringResource(if (it) BipeR.string.bipe_settings_notifications_on else BipeR.string.bipe_settings_notifications_off)
                }
                BipeSettingsGroup.PROFILE, BipeSettingsGroup.CHATS, BipeSettingsGroup.HELP -> null
            }
            ListItem(
                content = { Text(stringResource(group.title)) },
                supportingContent = subtitle?.let { { Text(it) } },
                leadingContent = ListItemContent.Icon(IconSource.Vector(group.icon())),
                // The only warning badge in Settings: one way back or none (ADR 0112).
                trailingContent = ListItemContent.Badge.takeIf {
                    group == BipeSettingsGroup.ACCOUNT && BipeWayBack.needsWarning(state.bipeWaysBackCount)
                },
                onClick = { onGroupClick(group) },
            )
        }
    }
}

@Composable
private fun BipeSettingsGroup.icon(): ImageVector = when (this) {
    BipeSettingsGroup.PROFILE -> CompoundIcons.UserProfile()
    BipeSettingsGroup.PRIVACY -> CompoundIcons.Lock()
    BipeSettingsGroup.ACCOUNT -> CompoundIcons.Key()
    BipeSettingsGroup.NOTIFICATIONS -> CompoundIcons.Notifications()
    BipeSettingsGroup.CHATS -> CompoundIcons.Chat()
    BipeSettingsGroup.HELP -> CompoundIcons.Info()
}

private val BipeWhoCanMessage.summaryRes: Int
    get() = when (this) {
        BipeWhoCanMessage.EVERYONE -> BipeR.string.bipe_settings_privacy_summary_everyone
        BipeWhoCanMessage.VERIFIED -> BipeR.string.bipe_settings_privacy_summary_verified
        BipeWhoCanMessage.CONTACTS -> BipeR.string.bipe_settings_privacy_summary_contacts
    }

/**
 * Ref: https://www.figma.com/design/G1xy0HDZKJf5TCRFmKb5d5/Compound-Android-Components?node-id=5414-4759
 */
@Composable
private fun ColumnScope.MultiAccountSection(
    state: PreferencesRootState,
) {
    HorizontalDivider(
        thickness = 8.dp,
        color = ElementTheme.colors.bgSubtleSecondary,
    )
    // Bipe: only shown with two or more accounts, and without "Add account" until #267 (ADR 0112).
    run {
        val expandedStateDescription = if (state.isOtherAccountsSectionExpanded) {
            stringResource(CommonStrings.a11y_state_expanded)
        } else {
            stringResource(CommonStrings.a11y_state_collapsed)
        }
        ListItem(
            modifier = Modifier.semantics {
                stateDescription = expandedStateDescription
            },
            content = { Text(stringResource(CommonStrings.common_switch_account)) },
            onClick = { state.eventSink(PreferencesRootEvent.ToggleOtherAccountsExpanded) },
            trailingContent = ListItemContent.Custom { _ ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(
                        visible = !state.isOtherAccountsSectionExpanded,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        AvatarRow(
                            avatarDataList = state.otherSessions
                                .take(3)
                                .map { it.getAvatarData(AvatarSize.OtherAccountItem) }
                                .toImmutableList(),
                            avatarType = AvatarType.User,
                            lastOnTop = true,
                        )
                    }
                    // Animate the chevron icon to rotate when the section is expanded/collapsed
                    val rotation: Float by animateFloatAsState(
                        targetValue = if (state.isOtherAccountsSectionExpanded) -180f else 0f,
                        animationSpec = tween(
                            delayMillis = 0,
                            durationMillis = 300,
                        ),
                        label = "chevron"
                    )
                    Icon(
                        modifier = Modifier.rotate(rotation),
                        imageVector = CompoundIcons.ChevronDown(),
                        contentDescription = null,
                    )
                }
            },
        )
        AnimatedVisibility(
            visible = state.isOtherAccountsSectionExpanded,
        ) {
            Column {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = ElementTheme.colors.bgSubtleSecondary,
                )
                state.otherSessions.forEach { matrixUser ->
                    MatrixUserRow(
                        modifier = Modifier
                            .clickable {
                                state.eventSink(PreferencesRootEvent.SwitchToSession(matrixUser.userId))
                            }
                            .padding(top = 2.dp, bottom = 2.dp, end = 8.dp),
                        matrixUser = matrixUser,
                        avatarSize = AvatarSize.AccountItem,
                        verticalSpaceWidth = 16.dp,
                    )
                }
            }
        }
    }
}

/**
 * Bipe: developer options after the 7 taps on the version, English only (ADR 0112).
 * Analytics, Labs and "Report a problem" show here, and only in builds that are not release builds.
 */
@Composable
private fun ColumnScope.DeveloperSection(
    state: PreferencesRootState,
    onOpenAnalytics: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenRageShake: () -> Unit,
) {
    AnimatedVisibility(
        visible = state.showDeveloperSettings,
    ) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = ElementTheme.colors.bgSubtleSecondary,
            )
            if (state.showAnalyticsSettings && state.bipeIsDeveloperBuild) {
                ListItem(
                    content = { Text(stringResource(id = CommonStrings.common_analytics)) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Chart())),
                    onClick = onOpenAnalytics,
                )
            }
            if (state.showLabsItem && state.bipeIsDeveloperBuild) {
                ListItem(
                    content = { Text(stringResource(id = R.string.screen_labs_title)) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Labs())),
                    onClick = onOpenLabs,
                )
            }
            if (state.preferencesAccountState.canReportBug && state.bipeIsDeveloperBuild) {
                ListItem(
                    content = { Text(stringResource(id = CommonStrings.common_report_a_problem)) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.ChatProblem())),
                    onClick = onOpenRageShake,
                )
            }
            DeveloperPreferencesView(onOpenDeveloperSettings)
        }
    }
}

@Composable
private fun DeveloperPreferencesView(onOpenDeveloperSettings: () -> Unit) {
    ListItem(
        content = { Text(stringResource(id = CommonStrings.common_developer_options)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Code())),
        onClick = onOpenDeveloperSettings
    )
}

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewLightPreview(@PreviewParameter(PreferencesRootStatePreviewParam::class) state: PreferencesRootState) =
    ElementPreviewLight(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewDarkPreview(@PreviewParameter(PreferencesRootStatePreviewParam::class) state: PreferencesRootState) =
    ElementPreviewDark(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: PreferencesRootState) {
    PreferencesRootView(
        state = state,
        onBackClick = {},
        onOpenAnalytics = {},
        onOpenDeveloperSettings = {},
        onOpenLabs = {},
        onOpenRageShake = {},
        onEditProfileClick = {},
    )
}
