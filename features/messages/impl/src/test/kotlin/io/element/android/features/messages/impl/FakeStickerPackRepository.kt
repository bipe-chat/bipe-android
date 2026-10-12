/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl

import android.net.Uri
import io.element.android.libraries.proseio.stickers.StickerPack
import io.element.android.libraries.proseio.stickers.StickerPackRepository

/** Proseio: sticker packs for presenter tests. Nothing is stored. */
class FakeStickerPackRepository(
    private val packs: List<StickerPack> = emptyList(),
) : StickerPackRepository {
    override suspend fun packs(roomId: String) = Result.success(packs)
    override suspend fun importStickers(uris: List<Uri>) = Result.success(0)
    override suspend fun addSticker(bytes: ByteArray) = Result.success(1)
    override suspend fun makeSticker(uri: Uri) = Result.success(1)
}
