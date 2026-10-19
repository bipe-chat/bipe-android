/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.element.android.appconfig.RoomListConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.HomeNavigationBarItem
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.filters.RoomListFiltersView
import io.element.android.features.home.impl.filters.aRoomListFiltersState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersEvent
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.aSelectedSpaceFiltersState
import io.element.android.features.home.impl.spacefilters.anUnselectedSpaceFiltersState
import io.element.android.libraries.bipe.generated.BipeRegistries
import io.element.android.libraries.designsystem.atomic.atoms.RedIndicatorAtom
import io.element.android.libraries.designsystem.components.TopAppBarScrollBehaviorLayout
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.modifiers.backgroundVerticalGradient
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.preview.USER_NAME_ALICE
import io.element.android.libraries.designsystem.text.AdaptativeTitle
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.user.DisplayedStatus
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.api.user.UserStatus
import io.element.android.libraries.matrix.ui.components.aMatrixUser
import io.element.android.libraries.matrix.ui.components.aMatrixUserList
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.toEmojiText
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.element.android.libraries.bipe.R as BipeR

@Composable
fun HomeTopBar(
    selectedNavigationItem: HomeNavigationBarItem,
    currentUserAndNeighbors: ImmutableList<MatrixUser>,
    showAvatarIndicator: Boolean,
    areSearchResultsDisplayed: Boolean,
    onToggleSearch: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    onOpenSettings: () -> Unit,
    onAccountSwitch: (SessionId) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    canReportBug: Boolean,
    displayFilters: Boolean,
    filtersState: RoomListFiltersState,
    spaceFiltersState: SpaceFiltersState,
    modifier: Modifier = Modifier,
    bipeAccounts: ImmutableList<MatrixUser> = persistentListOf(),
    bipeCanAddAccount: Boolean = false,
    bipeMaxAccounts: Int = BipeRegistries.MAX_PERSONAL_ACCOUNTS,
    onBipeAddAccount: () -> Unit = {},
) {
    val contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    Column(modifier) {
        TopAppBar(
            modifier = Modifier
                .backgroundVerticalGradient(
                    isVisible = !areSearchResultsDisplayed,
                )
                .statusBarsPadding()
                .padding(contentPadding),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            title = {
                val displayTitle = when (selectedNavigationItem) {
                    HomeNavigationBarItem.Chats -> {
                        when (spaceFiltersState) {
                            is SpaceFiltersState.Selected -> spaceFiltersState.selectedFilter.spaceRoom.displayName
                            else -> stringResource(selectedNavigationItem.labelRes)
                        }
                    }
                    HomeNavigationBarItem.Spaces -> null
                    // Bipe: Grupos and Você (ADR 0053)
                    HomeNavigationBarItem.Groups,
                    HomeNavigationBarItem.You -> stringResource(selectedNavigationItem.labelRes)
                }
                displayTitle?.let {
                    val style = when (spaceFiltersState) {
                        // Space name
                        is SpaceFiltersState.Selected -> ElementTheme.typography.fontHeadingSmMedium
                        // "Chats"
                        else -> ElementTheme.typography.fontHeadingLgBold
                    }
                    AdaptativeTitle(
                        title = displayTitle,
                        style = style,
                        twoLinesStyle = ElementTheme.typography.fontHeadingSmMedium,
                    )
                }
            },
            navigationIcon = {
                BipeAccountMenuAnchor(
                    accounts = bipeAccounts,
                    canAddAccount = bipeCanAddAccount,
                    maxAccounts = bipeMaxAccounts,
                    onAccountSwitch = onAccountSwitch,
                    onAddAccount = onBipeAddAccount,
                ) { openMenu ->
                    NavigationIcon(
                        currentUserAndNeighbors = currentUserAndNeighbors,
                        showAvatarIndicator = showAvatarIndicator,
                        onAccountSwitch = onAccountSwitch,
                        onClick = onOpenSettings,
                        onLongClick = openMenu,
                    )
                }
            },
            actions = {
                if (selectedNavigationItem.isBipeRoomList) { // Bipe: search on Conversas and Grupos
                    RoomListMenuItems(
                        onToggleSearch = onToggleSearch,
                        onMenuActionClick = onMenuActionClick,
                        canReportBug = canReportBug,
                        spaceFiltersState = spaceFiltersState,
                    )
                }
            },
            // We want a 16dp left padding for the navigationIcon :
            // 4dp from default TopAppBarHorizontalPadding
            // 8dp from AccountIcon default padding (48dp touch target around 32dp avatar)
            // 4dp extra padding using left insets
            windowInsets = WindowInsets(left = 4.dp),
        )
        if (displayFilters) {
            TopAppBarScrollBehaviorLayout(scrollBehavior = scrollBehavior) {
                RoomListFiltersView(
                    state = filtersState,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .padding(contentPadding)
                )
            }
        }
    }
}

@Composable
private fun RowScope.RoomListMenuItems(
    onToggleSearch: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    canReportBug: Boolean,
    spaceFiltersState: SpaceFiltersState,
) {
    IconButton(
        onClick = onToggleSearch,
    ) {
        Icon(
            imageVector = CompoundIcons.Search(),
            contentDescription = stringResource(CommonStrings.action_search),
        )
    }
    SpaceFilterButton(spaceFiltersState = spaceFiltersState)
    if (RoomListConfig.HAS_DROP_DOWN_MENU) {
        var showMenu by remember { mutableStateOf(false) }
        IconButton(
            onClick = { showMenu = !showMenu }
        ) {
            Icon(
                imageVector = CompoundIcons.OverflowVertical(),
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (RoomListConfig.SHOW_INVITE_MENU_ITEM) {
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        onMenuActionClick(RoomListMenuAction.InviteFriends)
                    },
                    text = { Text(stringResource(id = CommonStrings.action_invite)) },
                    leadingIcon = {
                        Icon(
                            imageVector = CompoundIcons.ShareAndroid(),
                            tint = ElementTheme.colors.iconSecondary,
                            contentDescription = null,
                        )
                    }
                )
            }
            if (RoomListConfig.SHOW_REPORT_PROBLEM_MENU_ITEM && canReportBug) {
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        onMenuActionClick(RoomListMenuAction.ReportBug)
                    },
                    text = { Text(stringResource(id = CommonStrings.common_report_a_problem)) },
                    leadingIcon = {
                        Icon(
                            imageVector = CompoundIcons.ChatProblem(),
                            tint = ElementTheme.colors.iconSecondary,
                            contentDescription = null,
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SpaceFilterButton(
    spaceFiltersState: SpaceFiltersState,
) {
    if (spaceFiltersState == SpaceFiltersState.Disabled) return

    fun onClick() {
        when (spaceFiltersState) {
            is SpaceFiltersState.Unselected -> spaceFiltersState.eventSink(SpaceFiltersEvent.Unselected.ShowFilters)
            is SpaceFiltersState.Selected -> spaceFiltersState.eventSink(SpaceFiltersEvent.Selected.ClearSelection)
            else -> Unit
        }
    }

    val isSelected = spaceFiltersState is SpaceFiltersState.Selected
    IconButton(
        onClick = ::onClick,
        colors = if (isSelected) {
            IconButtonDefaults.iconButtonColors(
                containerColor = ElementTheme.colors.bgActionPrimaryRest,
                contentColor = ElementTheme.colors.iconOnSolidPrimary,
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        },
    ) {
        Icon(
            imageVector = CompoundIcons.Filter(),
            contentDescription = stringResource(R.string.screen_roomlist_your_spaces),
        )
    }
}

@Composable
private fun NavigationIcon(
    currentUserAndNeighbors: ImmutableList<MatrixUser>,
    showAvatarIndicator: Boolean,
    onAccountSwitch: (SessionId) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Bipe: one avatar, no vertical swipe between accounts (bipe#434). The current account is in the middle of the
    // neighbours. A tap switches to the next account, or opens settings with one account; a long press lists the
    // accounts (ADR 0086, #267).
    val currentUser = currentUserAndNeighbors.getOrNull(1) ?: currentUserAndNeighbors.first()
    val nextUser = currentUserAndNeighbors.getOrNull(2)
    AccountIcon(
        matrixUser = currentUser,
        showAvatarIndicator = showAvatarIndicator,
        reserveStatusEmojiSlot = currentUser.displayedStatus != null,
        onClick = if (nextUser != null && nextUser.userId != currentUser.userId) {
            { onAccountSwitch(SessionId(nextUser.userId.value)) }
        } else {
            onClick
        },
        onLongClick = onLongClick,
    )
}

/**
 * Bipe: the account menu on a long press of the avatar (ADR 0086, #267). Every account, the current one first, then
 * "Adicionar conta" up to the cap of personal accounts.
 */
@Composable
private fun BipeAccountMenuAnchor(
    accounts: ImmutableList<MatrixUser>,
    canAddAccount: Boolean,
    maxAccounts: Int,
    onAccountSwitch: (SessionId) -> Unit,
    onAddAccount: () -> Unit,
    content: @Composable (openMenu: () -> Unit) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        content { showMenu = true }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            accounts.forEachIndexed { index, account ->
                DropdownMenuItem(
                    text = { Text(account.displayName ?: account.userId.value) },
                    leadingIcon = { Avatar(avatarData = account.getAvatarData(size = AvatarSize.AccountItem), avatarType = AvatarType.User) },
                    trailingIcon = if (index == 0) {
                        { Icon(imageVector = CompoundIcons.Check(), contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        showMenu = false
                        if (index != 0) onAccountSwitch(SessionId(account.userId.value))
                    },
                )
            }
            if (canAddAccount) {
                DropdownMenuItem(
                    text = { Text(stringResource(BipeR.string.bipe_account_add)) },
                    leadingIcon = { Icon(imageVector = CompoundIcons.Plus(), contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onAddAccount()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(BipeR.string.bipe_account_cap_reached, maxAccounts.toString())) },
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}

@Composable
private fun AccountIcon(
    matrixUser: MatrixUser,
    showAvatarIndicator: Boolean,
    reserveStatusEmojiSlot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .testTag(TestTags.homeScreenSettings)
            .combinedClickable(
                interactionSource = interactionSource,
                // The ripple is rendered on the avatar only, see below.
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val avatarData by remember(matrixUser) {
                derivedStateOf {
                    matrixUser.getAvatarData(size = AvatarSize.CurrentUserTopBar)
                }
            }
            Avatar(
                avatarData = avatarData,
                avatarType = AvatarType.User,
                contentDescription = if (showAvatarIndicator) {
                    stringResource(CommonStrings.a11y_settings_with_required_action)
                } else {
                    stringResource(CommonStrings.common_settings)
                },
            )
            if (showAvatarIndicator) {
                RedIndicatorAtom(
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        if (reserveStatusEmojiSlot) {
            StatusEmoji(
                emoji = matrixUser.displayedStatus?.toEmojiText(),
                modifier = Modifier.offset(x = statusEmojiStartOffset),
            )
        }
    }
}

private val statusEmojiSize = 20.dp

// The avatar is centered inside a 48dp touch target, so there is already 8dp of free space
// on its trailing side. Compensate it to end up with a 4dp gap after the avatar itself.
private val statusEmojiStartOffset = 4.dp - (48.dp - AvatarSize.CurrentUserTopBar.dp) / 2

@Composable
private fun StatusEmoji(
    emoji: String?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Ensure the emoji size is not affected by the user's font scale settings
    val fixedFontScaleDensity = remember(density.density) {
        Density(density = density.density, fontScale = 1f)
    }
    Box(
        modifier = modifier.size(statusEmojiSize),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            CompositionLocalProvider(LocalDensity provides fixedFontScaleDensity) {
                Text(
                    text = emoji,
                    style = ElementTheme.typography.fontBodyMdRegular,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarSpaceFiltersSelectedPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = aSelectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarSpacesPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Spaces,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = false,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarWithIndicatorPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = true,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarWithStatusPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(
            aMatrixUser(
                id = "@id:domain",
                displayName = USER_NAME_ALICE,
                displayedStatus = DisplayedStatus.UserSet(UserStatus(emoji = "🌴", text = "Away")),
            )
        ),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarMultiAccountPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = aMatrixUserList().take(3).toImmutableList(),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}
