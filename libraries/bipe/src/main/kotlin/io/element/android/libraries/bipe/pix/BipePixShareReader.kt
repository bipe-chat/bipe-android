/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.pix

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.bipe.PixCheck
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import zxingcpp.BarcodeReader

/** "Conferir no Bipe" (bipe ADR 0091): finds Pix in what another app shared. Nothing leaves the device. */
fun interface BipePixShareReader {
    /** The shared text, or the text of a QR code in a shared image, that holds a Pix code or key. Null when none does. */
    suspend fun pixText(text: String?, imageUris: List<Uri>): String?
}

@ContributesBinding(AppScope::class)
class DefaultBipePixShareReader(
    @ApplicationContext private val context: Context,
) : BipePixShareReader {
    override suspend fun pixText(text: String?, imageUris: List<Uri>): String? = withContext(Dispatchers.IO) {
        sequence {
            text?.let { yield(it) }
            for (uri in imageUris) yieldAll(qrTexts(uri))
        }.firstOrNull { PixCheck.check(it).foundPix }
    }

    private fun qrTexts(uri: Uri): List<String> = runCatchingExceptions {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        // A QR code survives downscaling well. Large photos would cost memory for nothing.
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_SIDE) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return emptyList()
        BarcodeReader().read(bitmap).mapNotNull { it.text }
    }.getOrElse {
        Timber.w(it, "Could not read QR codes from a shared image")
        emptyList()
    }

    private companion object {
        const val MAX_SIDE = 2048
    }
}
