/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import androidx.compose.runtime.Stable
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.textcomposer.mentions.ResolvedSuggestion
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.TextEditorState
import io.element.android.wysiwyg.display.TextDisplay
import kotlinx.collections.immutable.ImmutableList

@Stable
data class MessageComposerState(
    val textEditorState: TextEditorState,
    val isFullScreen: Boolean,
    val mode: MessageComposerMode,
    val isInThreadTimeline: Boolean,
    val showAttachmentSourcePicker: Boolean,
    val showTextFormatting: Boolean,
    val canShareLocation: Boolean,
    /** Bipe: DMs have no polls. */
    val isDm: Boolean,
    /** Bipe: sticker picker (ADR 0029). */
    val stickerPicker: StickerPickerState,
    val suggestions: ImmutableList<ResolvedSuggestion>,
    val resolveMentionDisplay: (String, String) -> TextDisplay,
    val resolveAtRoomMentionDisplay: () -> TextDisplay,
    val slashCommandAction: AsyncAction<Unit>,
    val eventSink: (MessageComposerEvent) -> Unit,
    /** Bipe (bipe-chat/bipe#199, bipe-chat/bipe#415): the notification ask after the first sent message. Null when hidden. */
    val bipeNotificationAsk: BipeNotificationAsk? = null,
)

/** [dmName] is the other person's name in a DM, for "Ana vai responder. Quer saber na hora?". */
data class BipeNotificationAsk(val dmName: String?)
