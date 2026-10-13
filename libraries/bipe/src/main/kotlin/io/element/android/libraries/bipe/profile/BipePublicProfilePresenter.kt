/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.bipe.BipeProfileProblem
import io.element.android.libraries.bipe.BipeProfileRules
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeApiErrorCode
import io.element.android.libraries.bipe.api.BipeApiException
import io.element.android.libraries.bipe.api.BipeMyProfile
import io.element.android.libraries.bipe.api.BipeProfileUpdate
import io.element.android.libraries.bipe.di.BipeSession
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

/** "Perfil público" in settings (ADR 0041): publish a profile with its own name, bio and photo. */
@Inject
class BipePublicProfilePresenter(
    private val bipeSession: BipeSession,
    private val matrixClient: MatrixClient,
    private val mediaPickerProvider: PickerProvider,
    private val photoEncoder: BipeProfilePhotoEncoder,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val stringProvider: StringProvider,
) : Presenter<BipePublicProfileState> {
    @Composable
    override fun present(): BipePublicProfileState {
        val coroutineScope = rememberCoroutineScope()
        val apiClient = bipeSession.apiClient

        var isLoading by remember { mutableStateOf(true) }
        var saved by remember { mutableStateOf<BipeMyProfile?>(null) }
        var enabled by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("") }
        var bio by remember { mutableStateOf("") }
        var serverProblem by remember { mutableStateOf<BipeProfileProblem?>(null) }
        var saveAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        var photoAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        /** Takes the server's profile. [resetForm] also puts its values in the form. */
        fun apply(profile: BipeMyProfile, resetForm: Boolean) {
            saved = profile
            bipeSession.publicProfiles.storeMine(profile)
            if (resetForm) {
                enabled = profile.enabled
                // First time the server has no name: start from the Matrix display name. The user can change it.
                name = profile.name.ifEmpty { matrixClient.userProfile.value.displayName.orEmpty() }
                bio = profile.bio
                serverProblem = null
            }
        }

        /** A profile problem from the server stays under its field; anything else is a dialog. */
        fun failure(error: BipeApiException): AsyncAction<Unit> {
            val problem = (error as? BipeApiException.Server)
                ?.takeIf { it.code == BipeApiErrorCode.PROFILE_INVALID }
                ?.reason
                ?.let(BipeProfileProblem::fromId)
            if (problem != null) {
                serverProblem = problem
                return AsyncAction.Uninitialized
            }
            return AsyncAction.Failure(Exception(error.localizedMessage(stringProvider)))
        }

        LaunchedEffect(Unit) {
            try {
                apply(withTokenRetry { token -> apiClient.myProfile(token) }, resetForm = true)
            } catch (error: BipeApiException) {
                Timber.e(error, "Bipe public profile load failed")
                saveAction = failure(error)
            } finally {
                isLoading = false
            }
        }

        // The user can set a handle from this screen. When it arrives, reload so the toggle works, and keep the form.
        val handles by bipeSession.handleResolver.handles.collectAsState()
        val myHandle = remember(handles) { bipeSession.handleResolver.handle(matrixClient.sessionId.value) }
        LaunchedEffect(myHandle) {
            if (myHandle == null || saved?.handle != null || saved == null) return@LaunchedEffect
            try {
                apply(withTokenRetry { token -> apiClient.myProfile(token) }, resetForm = false)
            } catch (error: BipeApiException) {
                Timber.w(error, "Bipe public profile reload failed")
            }
        }

        val photoPicker = mediaPickerProvider.registerGalleryImagePicker(
            onResult = { uri ->
                if (uri != null) {
                    coroutineScope.launch {
                        photoAction = AsyncAction.Loading
                        val jpeg = try {
                            photoEncoder.encode(uri)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Timber.w(error, "Bipe profile photo encoding failed")
                            photoAction = AsyncAction.Failure(Exception(stringProvider.getString(R.string.proseio_public_profile_photo_invalid)))
                            return@launch
                        }
                        photoAction = try {
                            apply(withTokenRetry { token -> apiClient.setMyProfilePhoto(jpeg, token) }, resetForm = false)
                            AsyncAction.Uninitialized
                        } catch (error: BipeApiException) {
                            Timber.e(error, "Bipe profile photo upload failed")
                            AsyncAction.Failure(Exception(error.localizedMessage(stringProvider)))
                        }
                    }
                }
            }
        )

        fun handleEvent(event: BipePublicProfileEvent) {
            when (event) {
                is BipePublicProfileEvent.SetEnabled -> {
                    enabled = event.enabled
                    serverProblem = null
                }
                is BipePublicProfileEvent.UpdateName -> {
                    name = event.name
                    serverProblem = null
                }
                is BipePublicProfileEvent.UpdateBio -> {
                    bio = event.bio
                    serverProblem = null
                }
                BipePublicProfileEvent.Save -> coroutineScope.launch {
                    saveAction = AsyncAction.Loading
                    saveAction = try {
                        val update = BipeProfileUpdate(enabled = enabled, name = name, bio = bio)
                        apply(withTokenRetry { token -> apiClient.setMyProfile(update, token) }, resetForm = true)
                        snackbarDispatcher.post(SnackbarMessage(R.string.proseio_public_profile_saved))
                        AsyncAction.Success(Unit)
                    } catch (error: BipeApiException) {
                        Timber.e(error, "Bipe public profile save failed")
                        failure(error)
                    }
                }
                BipePublicProfileEvent.ChangePhoto -> photoPicker.launch()
                BipePublicProfileEvent.RemovePhoto -> coroutineScope.launch {
                    photoAction = AsyncAction.Loading
                    photoAction = try {
                        apply(withTokenRetry { token -> apiClient.deleteMyProfilePhoto(token) }, resetForm = false)
                        AsyncAction.Uninitialized
                    } catch (error: BipeApiException) {
                        Timber.e(error, "Bipe profile photo removal failed")
                        AsyncAction.Failure(Exception(error.localizedMessage(stringProvider)))
                    }
                }
                BipePublicProfileEvent.ClearError -> {
                    saveAction = AsyncAction.Uninitialized
                    photoAction = AsyncAction.Uninitialized
                }
            }
        }

        return BipePublicProfileState(
            isLoading = isLoading,
            saved = saved,
            enabled = enabled,
            name = name,
            bio = bio,
            problem = serverProblem ?: BipeProfileRules.saveProblem(enabled = enabled, name = name, bio = bio),
            saveAction = saveAction,
            photoAction = photoAction,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun <T> withTokenRetry(request: suspend (String) -> T): T =
        BipeApiClient.withTokenRetry(bipeSession.accessToken, request)
}
