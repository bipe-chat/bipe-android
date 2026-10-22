/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.govbr

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * "Verificar com gov.br", trust step 5 (bipe#545). Port of BipeGovbrScreen on iOS.
 * Tap 1 gets the challenge and opens the system save dialog; after the save the gov.br app opens.
 * "Já assinei" picks the signed PDF. Bipe as a share target for the signed PDF comes in a later change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BipeGovbrSheet(flow: BipeGovbrFlow, onDismiss: () -> Unit) {
    val phase by flow.phase.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        val waiting = flow.phase.value as? BipeGovbrFlow.Phase.WaitingForSignature
        if (uri == null || waiting == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) { context.writeBytes(uri, waiting.pdf) }
            if (saved) context.openGovbrApp()
        }
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val pdf = withContext(Dispatchers.IO) { context.readBytes(uri) } ?: ByteArray(0)
            flow.submit(pdf)
        }
    }
    val start: () -> Unit = {
        scope.launch {
            flow.start()
            (flow.phase.value as? BipeGovbrFlow.Phase.WaitingForSignature)?.let { saveLauncher.launch(it.fileName) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.bipe_govbr_title),
                style = ElementTheme.typography.fontHeadingSmMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListSupportingText(text = stringResource(R.string.bipe_govbr_intro))
            ListSupportingText(text = stringResource(R.string.bipe_govbr_needs_level))
            StepRow(R.string.bipe_govbr_step_save, CompoundIcons.Download())
            StepRow(R.string.bipe_govbr_step_sign, CompoundIcons.Verified())
            StepRow(R.string.bipe_govbr_step_share, CompoundIcons.Attachment())
            when (val current = phase) {
                BipeGovbrFlow.Phase.Ready -> ActionRow(R.string.bipe_govbr_start, CompoundIcons.Verified(), start)
                BipeGovbrFlow.Phase.Preparing -> WaitingRow(R.string.bipe_govbr_start)
                is BipeGovbrFlow.Phase.WaitingForSignature -> {
                    ActionRow(R.string.bipe_govbr_already_signed, CompoundIcons.Attachment()) { pickLauncher.launch(arrayOf(PDF_MIME)) }
                    ActionRow(R.string.bipe_govbr_step_save, CompoundIcons.Download()) { saveLauncher.launch(current.fileName) }
                    ActionRow(R.string.bipe_govbr_get_app, CompoundIcons.Download()) { context.openGovbrApp() }
                }
                BipeGovbrFlow.Phase.Checking -> WaitingRow(R.string.bipe_govbr_checking)
                is BipeGovbrFlow.Phase.Verified -> ListItem(
                    content = {
                        val name = current.name
                        Text(if (name != null) stringResource(R.string.bipe_govbr_verified, name) else stringResource(R.string.bipe_trust_step_govbr))
                    },
                    leadingContent = ListItemContent.Icon(
                        IconSource.Vector(CompoundIcons.CheckCircleSolid()),
                        tintColor = ElementTheme.colors.iconSuccessPrimary,
                    ),
                )
                is BipeGovbrFlow.Phase.Rejected -> {
                    ListSupportingText(text = stringResource(current.reason.messageRes))
                    ActionRow(R.string.bipe_govbr_retry, CompoundIcons.Verified(), start)
                }
                BipeGovbrFlow.Phase.Failed -> {
                    ListSupportingText(text = stringResource(CommonStrings.error_unknown))
                    ActionRow(R.string.bipe_govbr_retry, CompoundIcons.Verified(), start)
                }
            }
            ListSupportingText(text = stringResource(R.string.bipe_govbr_safety))
        }
    }
}

@Composable
private fun StepRow(textRes: Int, icon: ImageVector) {
    ListItem(
        content = { Text(stringResource(textRes)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(icon)),
    )
}

@Composable
private fun ActionRow(textRes: Int, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        content = { Text(stringResource(textRes)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(icon), tintColor = ElementTheme.colors.iconAccentPrimary),
        onClick = onClick,
    )
}

@Composable
private fun WaitingRow(textRes: Int) {
    ListItem(
        content = { Text(stringResource(textRes)) },
        trailingContent = ListItemContent.Custom { CircularProgressIndicator(Modifier.size(20.dp)) },
    )
}

private val BipeGovbrReason.messageRes: Int
    get() = when (this) {
        BipeGovbrReason.NOT_SIGNED -> R.string.bipe_govbr_reason_not_signed
        BipeGovbrReason.NOT_GOVBR -> R.string.bipe_govbr_reason_not_govbr
        BipeGovbrReason.WRONG_DOCUMENT -> R.string.bipe_govbr_reason_wrong_document
        BipeGovbrReason.EXPIRED -> R.string.bipe_govbr_reason_expired
        BipeGovbrReason.CPF_IN_USE -> R.string.bipe_govbr_reason_cpf_in_use
        BipeGovbrReason.CHANGED_AFTER_SIGNING -> R.string.bipe_govbr_reason_changed_after_signing
    }

private const val PDF_MIME = "application/pdf"

/** Opens the gov.br app when installed, else its Play page. */
private fun Context.openGovbrApp() {
    val launch = packageManager.getLaunchIntentForPackage(BipeGovbrFlow.GOVBR_PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${BipeGovbrFlow.GOVBR_PACKAGE}"))
    try {
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (error: ActivityNotFoundException) {
        Timber.w(error, "Bipe: no app opens the gov.br link")
    }
}

private fun Context.writeBytes(uri: Uri, bytes: ByteArray): Boolean = try {
    contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
} catch (error: IOException) {
    Timber.w(error, "Bipe: gov.br challenge not saved")
    false
}

/** Reads at most one byte over the API limit, so a large file cannot fill the memory. */
private fun Context.readBytes(uri: Uri): ByteArray? = try {
    contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() <= BipeGovbrFlow.MAX_DOCUMENT_BYTES) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
} catch (error: IOException) {
    Timber.w(error, "Bipe: signed PDF not read")
    null
}
