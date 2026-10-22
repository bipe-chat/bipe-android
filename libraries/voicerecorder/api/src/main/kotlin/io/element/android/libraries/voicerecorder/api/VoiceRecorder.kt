/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.voicerecorder.api

import android.Manifest
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Audio recorder which records audio to opus/ogg files.
 */
interface VoiceRecorder {
    /**
     * Start a recording.
     *
     * Call [stopRecord] to stop the recording and release resources.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun startRecord()

    /**
     * Bipe: pause the current recording. The microphone stops and the paused time does not count. Call [resumeRecord] to go on,
     * or [stopRecord] to finish with the audio so far.
     */
    suspend fun pauseRecord()

    /**
     * Bipe: resume a paused recording into the same file. The permission was checked when the recording started.
     */
    suspend fun resumeRecord()

    /**
     * Stop the current recording.
     *
     * Call [deleteRecording] to delete any recorded audio.
     *
     * @param cancelled If true, the recording is deleted.
     */
    suspend fun stopRecord(
        cancelled: Boolean = false
    )

    /**
     * Stop the current recording and delete the output file.
     */
    suspend fun deleteRecording()

    /**
     * The current state of the recorder.
     */
    val state: StateFlow<VoiceRecorderState>

    /**
     * Bipe: the recorded audio as it is read, 16-bit mono PCM at [pcmSampleRateHz], for on-device transcription (ADR 0023).
     * Nothing is emitted while paused.
     */
    val pcm: Flow<ShortArray>

    /** Bipe: the sample rate of [pcm]. */
    val pcmSampleRateHz: Int
}
