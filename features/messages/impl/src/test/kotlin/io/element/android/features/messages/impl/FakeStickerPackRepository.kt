/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.messages.impl

import android.net.Uri
import io.element.android.libraries.bipe.stickers.StickerPack
import io.element.android.libraries.bipe.stickers.StickerPackRepository

/** Bipe: sticker packs for presenter tests. Nothing is stored. */
class FakeStickerPackRepository(
    private val packs: List<StickerPack> = emptyList(),
) : StickerPackRepository {
    override suspend fun packs(roomId: String) = Result.success(packs)
    override suspend fun importStickers(uris: List<Uri>) = Result.success(0)
    override suspend fun addSticker(bytes: ByteArray) = Result.success(1)
    override suspend fun makeSticker(uri: Uri) = Result.success(1)
}
