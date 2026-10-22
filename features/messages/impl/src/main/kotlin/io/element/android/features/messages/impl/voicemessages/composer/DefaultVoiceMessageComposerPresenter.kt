/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.voicemessages.composer

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import im.vector.app.features.analytics.plan.Composer
import io.element.android.features.messages.api.MessageComposerContext
import io.element.android.features.messages.api.timeline.voicemessages.composer.VoiceMessageComposerEvent
import io.element.android.features.messages.api.timeline.voicemessages.composer.VoiceMessageComposerPresenter
import io.element.android.features.messages.api.timeline.voicemessages.composer.VoiceMessageComposerState
import io.element.android.libraries.audio.api.AudioFocus
import io.element.android.libraries.audio.api.AudioFocusRequester
import io.element.android.libraries.bipe.voice.BipeVoiceTranscriber
import io.element.android.libraries.bipe.voice.BipeVoiceTranscriptDraft
import io.element.android.libraries.bipe.voice.caption
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.VoiceMessagePlayerEvent
import io.element.android.libraries.textcomposer.model.VoiceMessageRecorderEvent
import io.element.android.libraries.textcomposer.model.VoiceMessageState
import io.element.android.libraries.voiceplayer.api.VoiceMessageException
import io.element.android.libraries.voicerecorder.api.VoiceRecorder
import io.element.android.libraries.voicerecorder.api.VoiceRecorderState
import io.element.android.services.analytics.api.AnalyticsService
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@AssistedInject
class DefaultVoiceMessageComposerPresenter(
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    @Assisted private val timelineMode: Timeline.Mode,
    private val voiceRecorder: VoiceRecorder,
    private val analyticsService: AnalyticsService,
    private val audioFocus: AudioFocus,
    mediaSenderFactory: MediaSenderFactory,
    private val player: VoiceMessageComposerPlayer,
    private val messageComposerContext: MessageComposerContext,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
    private val transcriber: BipeVoiceTranscriber,
) : VoiceMessageComposerPresenter {
    @ContributesBinding(RoomScope::class)
    @AssistedFactory
    interface Factory : VoiceMessageComposerPresenter.Factory {
        override fun create(timelineMode: Timeline.Mode): DefaultVoiceMessageComposerPresenter
    }

    private val permissionsPresenter = permissionsPresenterFactory.create(Manifest.permission.RECORD_AUDIO)
    private var pendingEvent: VoiceMessageRecorderEvent.Start? = null
    private val mediaSender = mediaSenderFactory.create(timelineMode)

    // Bipe: sender-side transcript (ADR 0023). The session reads the recorder's PCM while the person records.
    private var transcriptSession: BipeVoiceTranscriber.Session? = null
    private var transcriptFeed: Job? = null
    private val transcriptDraft = MutableStateFlow<BipeVoiceTranscriptDraft?>(null)

    @Composable
    override fun present(): VoiceMessageComposerState {
        val localCoroutineScope = rememberCoroutineScope()
        val recorderState by voiceRecorder.state.collectAsState(initial = VoiceRecorderState.Idle)
        val playerState by player.state.collectAsState(initial = VoiceMessageComposerPlayer.State.Initial)
        val transcript by transcriptDraft.collectAsState()
        val keepScreenOn by remember { derivedStateOf { recorderState is VoiceRecorderState.Recording } }
        val permissionState by rememberUpdatedState(permissionsPresenter.present())
        var isSending by remember { mutableStateOf(false) }
        var showSendFailureDialog by remember { mutableStateOf(false) }

        LaunchedEffect(recorderState) {
            val recording = recorderState as? VoiceRecorderState.Finished
                ?: return@LaunchedEffect
            player.setMedia(recording.file.path)
        }

        LaunchedEffect(permissionState.permissionGranted) {
            if (permissionState.permissionGranted) {
                pendingEvent?.let {
                    localCoroutineScope.startRecording()
                    pendingEvent = null
                }
            }
        }

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    sessionCoroutineScope.finishRecording()
                    player.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    sessionCoroutineScope.cancelRecording()
                }
                else -> {}
            }
        }

        fun handleVoiceMessageRecorderEvent(event: VoiceMessageRecorderEvent) {
            pendingEvent = null
            when (event) {
                VoiceMessageRecorderEvent.Start -> {
                    Timber.v("Voice message record button pressed")
                    when {
                        permissionState.permissionGranted -> {
                            localCoroutineScope.startRecording()
                        }
                        else -> {
                            Timber.i("Voice message permission needed")
                            pendingEvent = VoiceMessageRecorderEvent.Start
                            permissionState.eventSink(PermissionsEvent.RequestPermissions)
                        }
                    }
                }
                VoiceMessageRecorderEvent.Stop -> {
                    Timber.v("Voice message stop button pressed")
                    localCoroutineScope.finishRecording()
                }
                VoiceMessageRecorderEvent.Cancel -> {
                    Timber.v("Voice message cancel button tapped")
                    localCoroutineScope.cancelRecording()
                }
                VoiceMessageRecorderEvent.Pause -> localCoroutineScope.launch { voiceRecorder.pauseRecord() }
                VoiceMessageRecorderEvent.Resume -> localCoroutineScope.launch { voiceRecorder.resumeRecord() }
            }
        }

        fun handleVoiceMessagePlayerEvent(event: VoiceMessagePlayerEvent) {
            localCoroutineScope.launch {
                when (event) {
                    VoiceMessagePlayerEvent.Play -> player.play()
                    VoiceMessagePlayerEvent.Pause -> player.pause()
                    is VoiceMessagePlayerEvent.Seek -> player.seek(event.position)
                }
            }
        }

        fun sendVoiceMessage(inReplyToEventId: EventId?) {
            val finishedState = recorderState as? VoiceRecorderState.Finished
            if (finishedState == null) {
                val exception = VoiceMessageException.FileException("No file to send")
                analyticsService.trackError(exception)
                Timber.e(exception)
                return
            }
            if (isSending) {
                return
            }
            isSending = true
            player.pause()
            analyticsService.captureComposerEvent()
            // A transcript still in progress is dropped: the sender never waits for it (ADR 0023).
            val caption = transcriptDraft.value.caption
            sessionCoroutineScope.launch {
                val result = sendMessage(
                    file = finishedState.file,
                    mimeType = finishedState.mimeType,
                    waveform = finishedState.waveform,
                    caption = caption,
                    inReplyToEventId = inReplyToEventId,
                )
                if (result.isFailure) {
                    showSendFailureDialog = true
                }
            }.invokeOnCompletion {
                isSending = false
            }
        }

        fun handleEvent(event: VoiceMessageComposerEvent) {
            when (event) {
                is VoiceMessageComposerEvent.RecorderEvent -> handleVoiceMessageRecorderEvent(event.recorderEvent)
                is VoiceMessageComposerEvent.PlayerEvent -> handleVoiceMessagePlayerEvent(event.playerEvent)
                is VoiceMessageComposerEvent.SendVoiceMessage -> {
                    // Capture reply info eagerly before any coroutine dispatch, since CloseSpecialMode
                    // may reset composerMode before the coroutine runs.
                    val inReplyToEventId = (messageComposerContext.composerMode as? MessageComposerMode.Reply)?.eventId
                    localCoroutineScope.launch {
                        sendVoiceMessage(inReplyToEventId)
                    }
                }
                VoiceMessageComposerEvent.DeleteVoiceMessage -> {
                    player.pause()
                    localCoroutineScope.deleteRecording()
                }
                is VoiceMessageComposerEvent.TranscriptChange -> transcriptDraft.update { draft ->
                    if (draft is BipeVoiceTranscriptDraft.Ready) BipeVoiceTranscriptDraft.Ready(event.text) else draft
                }
                VoiceMessageComposerEvent.DismissPermissionsRationale -> {
                    permissionState.eventSink(PermissionsEvent.CloseDialog)
                }
                VoiceMessageComposerEvent.AcceptPermissionRationale -> {
                    permissionState.eventSink(PermissionsEvent.OpenSystemSettingAndCloseDialog)
                }
                is VoiceMessageComposerEvent.LifecycleEvent -> handleLifecycleEvent(event.event)
                VoiceMessageComposerEvent.DismissSendFailureDialog -> {
                    showSendFailureDialog = false
                }
            }
        }

        return VoiceMessageComposerState(
            voiceMessageState = when (val state = recorderState) {
                is VoiceRecorderState.Recording -> VoiceMessageState.Recording(
                    duration = state.elapsedTime,
                    levels = state.levels
                        // Keep only the last 128 samples for display, else we can have a crash
                        .takeLast(128)
                        .toImmutableList(),
                    isPaused = state.isPaused,
                )
                is VoiceRecorderState.Finished ->
                    previewState(
                        playerState = playerState,
                        recorderState = recorderState,
                        isSending = isSending,
                        transcript = transcript,
                    )
                else -> VoiceMessageState.Idle
            },
            showPermissionRationaleDialog = permissionState.showDialog,
            showSendFailureDialog = showSendFailureDialog,
            keepScreenOn = keepScreenOn,
            eventSink = ::handleEvent,
        )
    }

    @Composable
    private fun previewState(
        playerState: VoiceMessageComposerPlayer.State,
        recorderState: VoiceRecorderState,
        isSending: Boolean,
        transcript: BipeVoiceTranscriptDraft?,
    ): VoiceMessageState {
        val showCursor by remember(playerState.isStopped, isSending) { derivedStateOf { !playerState.isStopped && !isSending } }
        val playerTime by remember(playerState, recorderState) { derivedStateOf { displayTime(playerState, recorderState) } }
        val waveform by remember(recorderState) { derivedStateOf { recorderState.finishedWaveform() } }

        val recorderDuration = (recorderState as VoiceRecorderState.Finished).duration
        val duration = playerState.durationMs?.milliseconds ?: recorderDuration

        return VoiceMessageState.Preview(
            isSending = isSending,
            isPlaying = playerState.isPlaying,
            showCursor = showCursor,
            playbackProgress = playerState.progress,
            time = playerTime,
            duration = duration,
            waveform = waveform,
            transcript = transcript,
        )
    }

    private fun CoroutineScope.startRecording() = launch {
        try {
            audioFocus.requestAudioFocus(AudioFocusRequester.RecordVoiceMessage) {
                // something else grabbed focus (phone call, etc) - finish gracefully
                // so the user keeps their partial recording
                sessionCoroutineScope.finishRecording()
            }
            startTranscript()
            voiceRecorder.startRecord()
        } catch (e: SecurityException) {
            stopTranscript()
            audioFocus.releaseAudioFocus()
            Timber.e(e, "Voice message error")
            analyticsService.trackError(VoiceMessageException.PermissionMissing("Expected permission to record but none", e))
        }
    }

    private fun CoroutineScope.finishRecording() = launch {
        voiceRecorder.stopRecord()
        audioFocus.releaseAudioFocus()
        finishTranscript()
    }

    private fun CoroutineScope.cancelRecording() = launch {
        voiceRecorder.stopRecord(cancelled = true)
        audioFocus.releaseAudioFocus()
        stopTranscript()
    }

    private fun CoroutineScope.deleteRecording() = launch {
        voiceRecorder.deleteRecording()
        stopTranscript()
    }

    /** Bipe: subscribes to the recorder's PCM before the recording starts, so no audio is missed. */
    private fun startTranscript() {
        stopTranscript()
        val session = transcriber.startSession(sessionCoroutineScope, voiceRecorder.pcmSampleRateHz) ?: return
        transcriptSession = session
        transcriptFeed = sessionCoroutineScope.launch { voiceRecorder.pcm.collect(session::write) }
    }

    /** Bipe: the preview shows "Transcrevendo…" until the recogniser has the rest of the audio. */
    private suspend fun finishTranscript() {
        val session = transcriptSession ?: return
        transcriptFeed?.cancel()
        transcriptFeed = null
        transcriptDraft.value = BipeVoiceTranscriptDraft.Transcribing
        val text = session.finish()
        // A new recording or a delete replaced this session meanwhile.
        if (transcriptSession !== session) return
        transcriptSession = null
        transcriptDraft.value = text?.let(BipeVoiceTranscriptDraft::Ready) ?: BipeVoiceTranscriptDraft.Unavailable
    }

    private fun stopTranscript() {
        transcriptFeed?.cancel()
        transcriptFeed = null
        transcriptSession?.cancel()
        transcriptSession = null
        transcriptDraft.value = null
    }

    private suspend fun sendMessage(
        file: File,
        mimeType: String,
        waveform: List<Float>,
        caption: String?,
        inReplyToEventId: EventId? = null,
    ): Result<Unit> {
        val result = mediaSender.sendVoiceMessage(
            uri = file.toUri(),
            mimeType = mimeType,
            waveForm = waveform,
            caption = caption,
            inReplyToEventId = inReplyToEventId,
        )

        if (result.isFailure) {
            Timber.e(result.exceptionOrNull(), "Voice message error")
            return result
        }

        voiceRecorder.deleteRecording()
        stopTranscript()

        return result
    }

    private fun AnalyticsService.captureComposerEvent() =
        capture(
            Composer(
                inThread = messageComposerContext.composerMode.inThread,
                isEditing = messageComposerContext.composerMode.isEditing,
                isReply = messageComposerContext.composerMode.isReply,
                messageType = Composer.MessageType.VoiceMessage,
            )
        )
}

private fun VoiceRecorderState.finishedWaveform(): ImmutableList<Float> =
    (this as? VoiceRecorderState.Finished)
        ?.waveform
        .orEmpty()
        .toImmutableList()

/**
 * The time to display depending on the player state.
 *
 * Either the current position or total duration.
 */
private fun displayTime(
    playerState: VoiceMessageComposerPlayer.State,
    recording: VoiceRecorderState
): Duration = when {
    !playerState.isStopped ->
        playerState.currentPosition.milliseconds
    recording is VoiceRecorderState.Finished ->
        recording.duration
    else ->
        0.milliseconds
}
