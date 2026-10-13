/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dev.zacsweers.metro.Inject
import io.element.android.libraries.androidutils.bitmap.calculateInSampleSize
import io.element.android.libraries.androidutils.bitmap.resizeToMax
import io.element.android.libraries.androidutils.bitmap.rotateToExifMetadataOrientation
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.proseio.generated.ProseioRegistries
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Re-encodes a picked image for `PUT /v1/me/profile/photo` (ADR 0041): JPEG, at most
 * [ProseioRegistries.PROFILE_PHOTO_MAX_PIXELS] on each side and [ProseioRegistries.PROFILE_PHOTO_MAX_BYTES].
 * It lowers the quality until the photo fits, then the size. The output has no EXIF; the server strips metadata as well.
 */
@Inject
class BipeProfilePhotoEncoder(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) {
    /** @throws IllegalStateException when the image cannot be read or cannot fit the limits. */
    suspend fun encode(uri: Uri): ByteArray = withContext(dispatchers.io) {
        val maxPixels = ProseioRegistries.PROFILE_PHOTO_MAX_PIXELS
        val orientation = open(uri).use { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { input -> BitmapFactory.decodeStream(input, null, options) }
        options.inSampleSize = options.calculateInSampleSize(maxPixels, maxPixels)
        options.inJustDecodeBounds = false
        val decoded = open(uri).use { input -> BitmapFactory.decodeStream(input, null, options) }
            ?: error("Profile photo: decoding failed")

        fit(decoded.rotateToExifMetadataOrientation(orientation).resizeToMax(maxPixels, maxPixels))
    }

    /** Lowers the quality until the photo fits, then shrinks it and tries again. */
    private fun fit(start: Bitmap): ByteArray {
        var bitmap = start
        while (bitmap.width >= MIN_SIDE && bitmap.height >= MIN_SIDE) {
            for (quality in QUALITIES) {
                val bytes = compress(bitmap, quality)
                if (bytes.size <= ProseioRegistries.PROFILE_PHOTO_MAX_BYTES) return bytes
            }
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * SHRINK_FACTOR).toInt(), (bitmap.height * SHRINK_FACTOR).toInt(), true)
        }
        error("Profile photo: cannot fit the size limit")
    }

    private fun open(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("Profile photo: cannot open the image")

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private companion object {
        val QUALITIES = listOf(90, 80, 70, 60, 50, 40)
        const val SHRINK_FACTOR = 0.75
        const val MIN_SIDE = 64
    }
}
