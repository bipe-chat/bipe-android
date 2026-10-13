/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.modifiers.clearFocusOnTap
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.bipe.BipeProfileProblem
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.api.BipeMyProfile
import io.element.android.libraries.proseio.generated.ProseioRegistries

/** "Perfil público" (ADR 0041): toggle, name, bio and photo of the public profile, and its link. */
@Composable
fun BipePublicProfileView(
    state: BipePublicProfileState,
    onBackClick: () -> Unit,
    onHandleClick: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    fun save() {
        if (!state.canSave) return
        focusManager.clearFocus()
        state.eventSink(BipePublicProfileEvent.Save)
    }

    Scaffold(
        modifier = modifier.clearFocusOnTap(focusManager),
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.proseio_settings_public_profile),
                navigationIcon = { BackButton(onClick = onBackClick) },
                actions = {
                    TextButton(
                        text = stringResource(R.string.proseio_public_profile_save),
                        enabled = state.canSave,
                        onClick = ::save,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.needsHandle || state.problem == BipeProfileProblem.NO_HANDLE) {
                NeedsHandleSection(onHandleClick = onHandleClick)
            }
            Column {
                PreferenceSwitch(
                    title = stringResource(R.string.proseio_public_profile_toggle),
                    isChecked = state.enabled && !state.needsHandle,
                    enabled = !state.isLoading && !state.needsHandle,
                    onCheckedChange = { state.eventSink(BipePublicProfileEvent.SetEnabled(it)) },
                )
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.proseio_public_profile_footer),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            state.saved?.let { saved ->
                PhotoSection(
                    saved = saved,
                    enabled = !state.isLoading && state.photoAction !is AsyncAction.Loading,
                    eventSink = state.eventSink,
                )
            }
            val nameProblem = state.problem?.takeIf { it.isNameProblem }
            TextField(
                modifier = Modifier.padding(horizontal = 16.dp),
                label = stringResource(R.string.proseio_public_profile_name),
                value = state.name,
                supportingText = nameProblem?.let { stringResource(it.messageRes, *it.messageArgs) },
                validity = if (nameProblem != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                enabled = !state.isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                onValueChange = { state.eventSink(BipePublicProfileEvent.UpdateName(it)) },
            )
            val bioProblem = state.problem?.takeIf { it.isBioProblem }
            TextField(
                modifier = Modifier.padding(horizontal = 16.dp),
                label = stringResource(R.string.proseio_public_profile_bio),
                value = state.bio,
                placeholder = stringResource(R.string.proseio_public_profile_bio_placeholder),
                supportingText = bioProblem?.let { stringResource(it.messageRes, *it.messageArgs) }
                    ?: "${state.bio.codePointCount(0, state.bio.length)}/${ProseioRegistries.PROFILE_BIO_MAX_LENGTH}",
                validity = if (bioProblem != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                enabled = !state.isLoading,
                minLines = 3,
                maxLines = ProseioRegistries.PROFILE_BIO_MAX_LINES + 1,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                onValueChange = { state.eventSink(BipePublicProfileEvent.UpdateBio(it)) },
            )
            state.shareUrl?.let { url -> ShareButton(url) }
            Spacer(modifier = Modifier.height(16.dp))
        }

        AsyncActionView(
            async = state.saveAction,
            onSuccess = { onSaveSuccess() },
            errorMessage = { it.message.orEmpty() },
            onErrorDismiss = { state.eventSink(BipePublicProfileEvent.ClearError) },
        )
        AsyncActionView(
            async = state.photoAction,
            onSuccess = {},
            errorMessage = { it.message.orEmpty() },
            onErrorDismiss = { state.eventSink(BipePublicProfileEvent.ClearError) },
        )
    }
}

/** No handle, no profile: the link uses the handle. */
@Composable
private fun NeedsHandleSection(onHandleClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.proseio_public_profile_needs_handle),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
        )
        OutlinedButton(
            text = stringResource(R.string.proseio_settings_handle),
            leadingIcon = IconSource.Vector(CompoundIcons.Mention()),
            size = ButtonSize.Medium,
            onClick = onHandleClick,
        )
    }
}

/** The photo the server holds, with change and remove. The server only serves it once the user has a handle. */
@Composable
private fun PhotoSection(
    saved: BipeMyProfile,
    enabled: Boolean,
    eventSink: (BipePublicProfileEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        saved.photoUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                text = stringResource(R.string.proseio_public_profile_photo_change),
                leadingIcon = IconSource.Vector(CompoundIcons.Image()),
                size = ButtonSize.Medium,
                enabled = enabled,
                onClick = { eventSink(BipePublicProfileEvent.ChangePhoto) },
            )
            if (saved.hasPhoto) {
                TextButton(
                    text = stringResource(R.string.proseio_public_profile_photo_remove),
                    leadingIcon = IconSource.Vector(CompoundIcons.Delete()),
                    size = ButtonSize.Medium,
                    destructive = true,
                    enabled = enabled,
                    onClick = { eventSink(BipePublicProfileEvent.RemovePhoto) },
                )
            }
        }
    }
}

@Composable
private fun ShareButton(url: String) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        text = stringResource(R.string.proseio_public_profile_share),
        leadingIcon = IconSource.Vector(CompoundIcons.ShareAndroid()),
        onClick = {
            context.startSharePlainTextIntent(activityResultLauncher = null, chooserTitle = null, text = url)
        },
    )
}

@PreviewsDayNight
@Composable
internal fun BipePublicProfileViewPreview() = ElementPreview {
    BipePublicProfileView(
        state = BipePublicProfileState(
            isLoading = false,
            saved = BipeMyProfile(enabled = true, name = "Ana Souza", bio = "Professora em Recife.", hasPhoto = false, handle = "ana.souza"),
            enabled = true,
            name = "Ana Souza",
            bio = "Professora em Recife.",
            problem = null,
            saveAction = AsyncAction.Uninitialized,
            photoAction = AsyncAction.Uninitialized,
            eventSink = {},
        ),
        onBackClick = {},
        onHandleClick = {},
        onSaveSuccess = {},
    )
}

@PreviewsDayNight
@Composable
internal fun BipePublicProfileViewNeedsHandlePreview() = ElementPreview {
    BipePublicProfileView(
        state = BipePublicProfileState(
            isLoading = false,
            saved = BipeMyProfile(enabled = false, name = "", bio = "", hasPhoto = false, handle = null),
            enabled = false,
            name = "Ana Souza",
            bio = "",
            problem = null,
            saveAction = AsyncAction.Uninitialized,
            photoAction = AsyncAction.Uninitialized,
            eventSink = {},
        ),
        onBackClick = {},
        onHandleClick = {},
        onSaveSuccess = {},
    )
}
