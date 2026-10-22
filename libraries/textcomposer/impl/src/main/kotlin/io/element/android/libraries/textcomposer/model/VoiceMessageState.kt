/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer.model

import androidx.compose.runtime.Immutable
import io.element.android.libraries.bipe.voice.BipeVoiceTranscriptDraft
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Duration

@Immutable
sealed interface VoiceMessageState {
    data object Idle : VoiceMessageState

    data class Preview(
        val isSending: Boolean,
        val isPlaying: Boolean,
        val showCursor: Boolean,
        val playbackProgress: Float,
        val time: Duration,
        val duration: Duration,
        // Values are between 0 and 1
        val waveform: ImmutableList<Float>,
        // Bipe: the sender-side transcript (ADR 0023); null when this phone cannot transcribe on the device.
        val transcript: BipeVoiceTranscriptDraft? = null,
    ) : VoiceMessageState

    data class Recording(
        val duration: Duration,
        // Values are between 0 and 1
        val levels: ImmutableList<Float>,
        // Bipe: pause and resume while recording (bipe#80).
        val isPaused: Boolean = false,
    ) : VoiceMessageState
}
