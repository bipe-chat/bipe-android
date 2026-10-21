/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.bipe.account.BipeAccountSession
import io.element.android.libraries.bipe.account.ui.BipeBiometricCheck
import io.element.android.libraries.bipe.account.ui.BipeEnterPinDialog
import io.element.android.libraries.bipe.generated.BipeHaptic
import io.element.android.libraries.bipe.haptics.rememberBipeHaptics
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

private sealed interface ExportDialog {
    data class Pin(val error: String?) : ExportDialog
    data object Failed : ExportDialog
}

/**
 * "Baixar meus dados" (ADR 0112, #364): proves the owner first (biometrics or the phone's screen lock, else the app lock PIN,
 * else refused), then gets the export, saves it in the cache and opens the system share sheet with it.
 */
@Composable
fun BipeDataExportRow(account: BipeAccountSession, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ExportDialog?>(null) }
    val haptics = rememberBipeHaptics()
    val reason = stringResource(R.string.bipe_privacy_export_biometric)
    val pinMismatch = stringResource(R.string.bipe_pin_mismatch)

    fun export() {
        busy = true
        scope.launch {
            account.exportData()
                .mapCatchingExceptions { file -> withContext(Dispatchers.IO) { context.saveExport(file) } }
                .onSuccess { context.shareExport(it) }
                .onFailure {
                    Timber.w(it, "Bipe: data export failed")
                    dialog = ExportDialog.Failed
                }
            busy = false
        }
    }

    fun start() {
        scope.launch {
            when {
                activity != null && BipeBiometricCheck.isAvailable(context) -> {
                    if (BipeBiometricCheck.authenticate(activity, title, reason)) export()
                }
                account.hasAppLockPin() -> dialog = ExportDialog.Pin(error = null)
                // Nothing can prove the owner. Fail closed.
                else -> dialog = ExportDialog.Failed
            }
        }
    }

    ListItem(
        modifier = modifier,
        content = { Text(title) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Download())),
        trailingContent = if (busy) ListItemContent.Custom { CircularProgressIndicator() } else null,
        enabled = !busy,
        onClick = ::start,
    )

    when (val shown = dialog) {
        null -> Unit
        is ExportDialog.Pin -> BipeEnterPinDialog(
            title = stringResource(R.string.bipe_pin_enter_title),
            subtitle = reason,
            error = shown.error,
            onSubmit = { pin ->
                scope.launch {
                    if (account.verifyAppLockPin(pin)) {
                        dialog = null
                        export()
                    } else {
                        haptics(BipeHaptic.Error) // pin_wrong
                        dialog = ExportDialog.Pin(pinMismatch)
                    }
                }
            },
            onDismiss = { dialog = null },
        )
        ExportDialog.Failed -> ErrorDialog(content = stringResource(R.string.bipe_privacy_export_error), onSubmit = { dialog = null })
    }
}

/** Saves the export in its own cache folder, which holds only the latest export. */
private fun Context.saveExport(export: BipeDataExportFile): File {
    val folder = File(cacheDir, EXPORT_FOLDER)
    folder.deleteRecursively()
    folder.mkdirs()
    return File(folder, export.fileName).apply { writeBytes(export.bytes) }
}

private fun Context.shareExport(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (error: ActivityNotFoundException) {
        Timber.w(error, "Bipe: no app to share the data export")
    }
}

private const val EXPORT_FOLDER = "bipe-export"
