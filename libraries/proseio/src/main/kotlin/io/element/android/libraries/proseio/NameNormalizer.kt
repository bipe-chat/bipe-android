/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio

import io.element.android.libraries.proseio.generated.ProseioRegistries
import io.element.android.libraries.proseio.generated.ReservedNameCategory
import java.text.Normalizer
import java.util.Locale

/**
 * Port of `normalize()` in proseio-shared/tools/lib/registries.mjs.
 * Must match proseio-shared/test-vectors/normalization.json.
 */
object NameNormalizer {
    /**
     * NFKD, strip combining marks, lowercase, fold homoglyphs, collapse whitespace, trim.
     * Pass an empty [homoglyphs] map to skip folding (scam phrase matching does this).
     */
    fun normalize(input: String, homoglyphs: Map<Int, String> = ProseioRegistries.homoglyphs): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFKD)
        val stripped = StringBuilder(decomposed.length)
        decomposed.codePoints().forEach { codePoint ->
            if (!isMark(codePoint)) stripped.appendCodePoint(codePoint)
        }
        // Locale-free like JS toLowerCase(). Java handles the final sigma context (Σ -> ς) too.
        val lowered = stripped.toString().lowercase(Locale.ROOT)

        val result = StringBuilder(lowered.length)
        var pendingSpace = false
        // JS iterates code points, so fold per code point, not per UTF-16 unit.
        lowered.codePoints().forEach { codePoint ->
            if (isJavaScriptWhitespace(codePoint)) {
                pendingSpace = true
                return@forEach
            }
            if (pendingSpace && result.isNotEmpty()) {
                result.append(' ')
            }
            pendingSpace = false
            val folded = homoglyphs[codePoint]
            if (folded != null) result.append(folded) else result.appendCodePoint(codePoint)
        }
        return result.toString()
    }

    /** Category of a reserved display name, or `null` when the name is free to use. */
    fun reservedCategory(displayName: String): ReservedNameCategory? =
        ProseioRegistries.reservedNames[normalize(displayName)]

    /** JS `String.prototype.trim()`: strips the same whitespace set as JS, not Kotlin's [String.trim] set. */
    fun trimJavaScript(input: String): String {
        var start = 0
        var end = input.length
        while (start < end) {
            val codePoint = input.codePointAt(start)
            if (!isJavaScriptWhitespace(codePoint)) break
            start += Character.charCount(codePoint)
        }
        while (end > start) {
            val codePoint = input.codePointBefore(end)
            if (!isJavaScriptWhitespace(codePoint)) break
            end -= Character.charCount(codePoint)
        }
        return input.substring(start, end)
    }

    private fun isMark(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }

    /**
     * JS `\s` and `String.prototype.trim()`: WhiteSpace plus LineTerminator (ECMA-262).
     * Not the same set as Java `\s` or `Character.isWhitespace` (U+00A0, U+FEFF, U+0085 differ).
     */
    private fun isJavaScriptWhitespace(codePoint: Int): Boolean = when (codePoint) {
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20, 0xA0, 0xFEFF, 0x2028, 0x2029 -> true
        else -> Character.getType(codePoint).toByte() == Character.SPACE_SEPARATOR
    }
}
