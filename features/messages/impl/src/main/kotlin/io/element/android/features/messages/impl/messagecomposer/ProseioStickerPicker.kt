/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl.messagecomposer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import io.element.android.libraries.proseio.stickers.Sticker
import io.element.android.libraries.proseio.stickers.StickerPack
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import io.element.android.libraries.proseio.R as ProseioR

/** Proseio: the sticker picker (ADR 0029, packs per ADR 0033). */
@Immutable
sealed interface StickerPickerState {
    data object Hidden : StickerPickerState
    data object Loading : StickerPickerState
    data class Loaded(
        val packs: ImmutableList<StickerPack>,
        /** The result of the last import, shown above the packs. */
        val importNotice: StickerImportNotice? = null,
    ) : StickerPickerState
    data object Failed : StickerPickerState
}

@Immutable
sealed interface StickerImportNotice {
    data class Added(val count: Int) : StickerImportNotice
    data object NoneNew : StickerImportNotice
    data object Failed : StickerImportNotice
}

@Composable
internal fun StickerPickerBottomSheet(
    state: StickerPickerState,
    roomId: String,
    onStickerClick: (Sticker) -> Unit,
    onRetryClick: () -> Unit,
    onImport: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == StickerPickerState.Hidden) return
    var showImportHelp by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(OpenStickerFiles()) { uris ->
        if (uris.isNotEmpty()) onImport(uris)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = false) {
        StickerPickerContent(state, roomId, onStickerClick, onRetryClick, onImportClick = { showImportHelp = true })
    }
    if (showImportHelp) {
        ConfirmationDialog(
            title = stringResource(ProseioR.string.proseio_stickers_import_action),
            content = listOf(
                stringResource(ProseioR.string.proseio_stickers_import_help),
                stringResource(ProseioR.string.proseio_stickers_import_help_folder_android),
                stringResource(ProseioR.string.proseio_stickers_import_note),
            ).joinToString("\n\n"),
            submitText = stringResource(CommonStrings.action_choose_file),
            onSubmitClick = {
                showImportHelp = false
                filePicker.launch(StickerImporterMimeTypes)
            },
            onDismiss = { showImportHelp = false },
        )
    }
}

/** A WhatsApp chat export, or the sticker files themselves. */
private val StickerImporterMimeTypes = arrayOf("application/zip", "image/webp", "image/png")

/**
 * Opens the document picker in WhatsApp's received-stickers folder, where every sticker the user got is a `.webp` file.
 * If the folder does not exist, the picker opens in its default place.
 */
private class OpenStickerFiles : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = super.createIntent(context, input)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, WHATSAPP_STICKERS_FOLDER)
        }
        return intent
    }
}

private val WHATSAPP_STICKERS_FOLDER: Uri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    "primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
)

@Composable
private fun StickerPickerContent(
    state: StickerPickerState,
    roomId: String,
    onStickerClick: (Sticker) -> Unit,
    onRetryClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(ProseioR.string.proseio_stickers_title),
                style = ElementTheme.typography.fontHeadingSmMedium,
                color = ElementTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(ProseioR.string.proseio_stickers_import_action),
                size = ButtonSize.Medium,
                enabled = state != StickerPickerState.Loading,
                onClick = onImportClick,
            )
        }
        if (state is StickerPickerState.Loaded && state.importNotice != null) {
            Text(
                text = importNoticeText(state.importNotice),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        when (state) {
            StickerPickerState.Hidden -> Unit
            StickerPickerState.Loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            StickerPickerState.Failed -> Message(
                title = stringResource(ProseioR.string.proseio_stickers_error_load),
                body = null,
                onRetryClick = onRetryClick,
            )
            is StickerPickerState.Loaded -> if (state.packs.isEmpty()) {
                Message(
                    title = stringResource(ProseioR.string.proseio_stickers_empty_title),
                    body = stringResource(ProseioR.string.proseio_stickers_empty_body),
                    onRetryClick = null,
                )
            } else {
                StickerGrid(state.packs, roomId, onStickerClick)
            }
        }
    }
}

@Composable
private fun StickerGrid(
    packs: ImmutableList<StickerPack>,
    roomId: String,
    onStickerClick: (Sticker) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 80.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        packs.forEach { pack ->
            item(key = pack.id, span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = packTitle(pack, roomId),
                    style = ElementTheme.typography.fontBodySmMedium,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(pack.stickers, key = { it.id }) { sticker ->
                StickerCell(sticker, onStickerClick)
            }
        }
    }
}

@Composable
private fun StickerCell(sticker: Sticker, onStickerClick: (Sticker) -> Unit) {
    val description = stringResource(ProseioR.string.proseio_a11y_sticker_send, sticker.body)
    AsyncImage(
        model = MediaRequestData(MediaSource(sticker.url), MediaRequestData.Kind.Thumbnail(240L)),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable { onStickerClick(sticker) }
            .semantics { contentDescription = description },
    )
}

@Composable
private fun Message(title: String, body: String?, onRetryClick: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetryClick != null) {
            Button(text = stringResource(CommonStrings.action_retry), onClick = onRetryClick)
        }
    }
}

@Composable
private fun importNoticeText(notice: StickerImportNotice): String = when (notice) {
    is StickerImportNotice.Added -> stringResource(ProseioR.string.proseio_stickers_import_done, notice.count.toString())
    StickerImportNotice.NoneNew -> stringResource(ProseioR.string.proseio_stickers_import_none)
    StickerImportNotice.Failed -> stringResource(ProseioR.string.proseio_stickers_import_failed)
}

@Composable
private fun packTitle(pack: StickerPack, roomId: String): String = when (val source = pack.source) {
    StickerPack.Source.User -> stringResource(ProseioR.string.proseio_stickers_pack_personal)
    is StickerPack.Source.Room -> pack.displayName
        ?: if (source.roomId == roomId) {
            stringResource(ProseioR.string.proseio_stickers_pack_room)
        } else {
            stringResource(ProseioR.string.proseio_stickers_title)
        }
}

@PreviewsDayNight
@Composable
internal fun StickerPickerContentPreview() = ElementPreview {
    Column {
        listOf(
            StickerPickerState.Loading,
            StickerPickerState.Failed,
            StickerPickerState.Loaded(persistentListOf()),
            StickerPickerState.Loaded(persistentListOf(), importNotice = StickerImportNotice.Added(12)),
        ).forEach { state ->
            StickerPickerContent(state, roomId = "!room:proseio.com.br", onStickerClick = {}, onRetryClick = {}, onImportClick = {})
        }
    }
}
