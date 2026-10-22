/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.voicerecorder.impl

import android.Manifest
import androidx.annotation.RequiresPermission
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.VoiceMessageConfig
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.coroutine.childScope
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.voicerecorder.api.VoiceRecorder
import io.element.android.libraries.voicerecorder.api.VoiceRecorderState
import io.element.android.libraries.voicerecorder.impl.audio.Audio
import io.element.android.libraries.voicerecorder.impl.audio.AudioConfig
import io.element.android.libraries.voicerecorder.impl.audio.AudioLevelCalculator
import io.element.android.libraries.voicerecorder.impl.audio.AudioReader
import io.element.android.libraries.voicerecorder.impl.audio.Encoder
import io.element.android.libraries.voicerecorder.impl.audio.SampleRate
import io.element.android.libraries.voicerecorder.impl.audio.resample
import io.element.android.libraries.voicerecorder.impl.file.VoiceFileConfig
import io.element.android.libraries.voicerecorder.impl.file.VoiceFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import timber.log.Timber
import java.io.File
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class)
class DefaultVoiceRecorder(
    private val dispatchers: CoroutineDispatchers,
    private val timeSource: TimeSource,
    private val audioReaderFactory: AudioReader.Factory,
    private val encoder: Encoder,
    private val fileManager: VoiceFileManager,
    private val config: AudioConfig,
    private val fileConfig: VoiceFileConfig,
    private val audioLevelCalculator: AudioLevelCalculator,
    @SessionCoroutineScope
    sessionCoroutineScope: CoroutineScope,
) : VoiceRecorder {
    private val voiceCoroutineScope by lazy {
        sessionCoroutineScope.childScope(dispatchers.io, "VoiceRecorder-${UUID.randomUUID()}")
    }

    private var outputFile: File? = null
    private var audioReader: AudioReader? = null
    private var recordingJob: Job? = null

    // List of Float between 0 and 1 representing the audio levels
    private val levels: MutableList<Float> = mutableListOf()
    private val lock = Mutex()

    private val _state = MutableStateFlow<VoiceRecorderState>(VoiceRecorderState.Idle)
    override val state: StateFlow<VoiceRecorderState> = _state

    // Bipe: pause and resume (bipe#80). The paused time does not count in the elapsed time.
    private var startedAt: TimeMark? = null
    private var pausedAt: TimeMark? = null
    private var pausedTotal: Duration = Duration.ZERO

    // Bipe: the PCM for on-device transcription (ADR 0023). Chunks are copies; a slow collector loses chunks, never the recording.
    private val _pcm = MutableSharedFlow<ShortArray>(extraBufferCapacity = PCM_BUFFER_CHUNKS)
    override val pcm: Flow<ShortArray> = _pcm.asSharedFlow()
    override val pcmSampleRateHz: Int = SampleRate.HZ

    private fun elapsedTime(): Duration {
        val start = startedAt ?: return Duration.ZERO
        val currentPause = pausedAt?.elapsedNow() ?: Duration.ZERO
        return start.elapsedNow() - pausedTotal - currentPause
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun startRecord() = lock.withLock {
        if (recordingJob != null) {
            Timber.w("Voice recorder is already recording, ignoring this start")
            return@withLock
        }

        Timber.i("Voice recorder started recording")
        outputFile = fileManager.createFile()
            .also(encoder::init)

        levels.clear()

        val audioRecorder = audioReaderFactory.create(config, dispatchers).also { audioReader = it }

        startedAt = timeSource.markNow()
        pausedAt = null
        pausedTotal = Duration.ZERO
        recordingJob = voiceCoroutineScope.launch {
            audioRecorder.record { audio ->
                yield()
                // Bipe: a read that a pause interrupted is not part of the recording.
                if (pausedAt != null) return@record

                val elapsedTime = elapsedTime()

                if (elapsedTime > VoiceMessageConfig.maxVoiceMessageDuration) {
                    Timber.w("Voice message time limit reached")
                    stopRecord(false)
                    return@record
                }

                when (audio) {
                    is Audio.Data -> {
                        val audioLevel = audioLevelCalculator.calculateAudioLevel(audio.buffer)

                        lock.withLock {
                            levels.add(audioLevel)
                            _state.emit(VoiceRecorderState.Recording(elapsedTime, levels.toList()))
                        }
                        encoder.encode(audio.buffer, audio.readSize)
                        if (_pcm.subscriptionCount.value > 0 && audio.readSize > 0) {
                            if (!_pcm.tryEmit(audio.buffer.copyOf(audio.readSize))) Timber.w("Voice recorder: PCM chunk dropped")
                        }
                    }
                    is Audio.Error -> {
                        Timber.e("Voice message error: code=${audio.audioRecordErrorCode}")
                        _state.emit(VoiceRecorderState.Recording(elapsedTime, listOf()))
                    }
                }
            }
        }
    }

    override suspend fun pauseRecord() = lock.withLock {
        val reader = audioReader ?: return@withLock
        if (pausedAt != null) return@withLock
        reader.pause()
        pausedAt = timeSource.markNow()
        Timber.i("Voice recorder paused")
        _state.emit(VoiceRecorderState.Recording(elapsedTime(), levels.toList(), isPaused = true))
    }

    override suspend fun resumeRecord() = lock.withLock {
        val reader = audioReader ?: return@withLock
        val pause = pausedAt ?: return@withLock
        pausedTotal += pause.elapsedNow()
        pausedAt = null
        reader.resume()
        Timber.i("Voice recorder resumed")
        _state.emit(VoiceRecorderState.Recording(elapsedTime(), levels.toList(), isPaused = false))
    }

    /**
     * Stop the current recording.
     *
     * Call [deleteRecording] to delete any recorded audio.
     */
    override suspend fun stopRecord(
        cancelled: Boolean
    ) {
        recordingJob?.cancel()?.also {
            Timber.i("Voice recorder stopped recording")
        }
        recordingJob = null
        pausedAt = null

        audioReader?.stop()
        audioReader = null
        encoder.release()

        lock.withLock {
            if (cancelled) {
                deleteRecording()
                levels.clear()
            }

            _state.emit(
                when (val file = outputFile) {
                    null -> VoiceRecorderState.Idle
                    else -> {
                        val duration = (state.value as? VoiceRecorderState.Recording)?.elapsedTime
                        VoiceRecorderState.Finished(
                            file = file,
                            mimeType = fileConfig.mimeType,
                            waveform = levels.resample(100),
                            duration = duration ?: 0.milliseconds
                        )
                    }
                }
            )
        }
    }

    /**
     * Stop the current recording and delete the output file.
     */
    override suspend fun deleteRecording() {
        outputFile?.let(fileManager::deleteFile)?.also {
            Timber.i("Voice recorder deleted recording")
        }
        outputFile = null
        _state.emit(VoiceRecorderState.Idle)
    }
}

/** About 40 s of audio at the reader's buffer size, so a short stall in the transcriber loses nothing. */
private const val PCM_BUFFER_CHUNKS = 512
