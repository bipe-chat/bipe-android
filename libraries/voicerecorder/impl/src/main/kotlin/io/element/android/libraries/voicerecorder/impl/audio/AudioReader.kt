/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.voicerecorder.impl.audio

import io.element.android.libraries.core.coroutine.CoroutineDispatchers

interface AudioReader {
    /**
     * Record audio data continuously.
     *
     * @param onAudio callback when audio is read.
     */
    suspend fun record(
        onAudio: suspend (Audio) -> Unit,
    )

    fun stop()

    /** Bipe: stop the microphone without ending [record]. */
    fun pause()

    /** Bipe: start the microphone again after [pause]. */
    fun resume()

    interface Factory {
        fun create(config: AudioConfig, dispatchers: CoroutineDispatchers): AudioReader
    }
}
