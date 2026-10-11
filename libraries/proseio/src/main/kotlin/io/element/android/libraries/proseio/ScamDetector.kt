/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio

import androidx.annotation.StringRes
import io.element.android.libraries.proseio.generated.ProseioRegistries
import java.util.regex.Pattern

/** The scam pattern a message looks like (security.md rule 9). Friction, not protection. */
enum class ScamWarningKind(val id: String, @StringRes val messageRes: Int) {
    PIX_CODE("pixCode", R.string.proseio_scam_warning_pix_code),
    BOLETO("boleto", R.string.proseio_scam_warning_boleto),
    PIX_KEY("pixKey", R.string.proseio_scam_warning_pix_key),
    RECEIPT("receipt", R.string.proseio_scam_warning_receipt),
    NEW_NUMBER("newNumber", R.string.proseio_scam_warning_new_number),
    URGENCY("urgency", R.string.proseio_scam_warning_urgency);

    companion object {
        fun fromId(id: String): ScamWarningKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Flags payment-scam signals in a message.
 * Must match proseio-shared/test-vectors/scam-detection.json and scam-warnings.json.
 */
object ScamDetector {
    /** Match IDs in reference order: `<patternGroup>:<id>` for regexes, then `urgency:<phrase>`, then `receipt:<keyword>`. */
    fun matchIds(message: String): List<String> {
        val ids = mutableListOf<String>()
        for (pattern in compiledPatterns) {
            if (pattern.expression.matcher(message).find()) ids.add(pattern.matchId)
        }

        // The reference matches phrases on the normalised message without homoglyph folding.
        // String.contains compares UTF-16 units, like JS `includes`.
        val normalized = NameNormalizer.normalize(message, homoglyphs = emptyMap())
        for (phrase in ProseioRegistries.urgencyPhrases) {
            if (normalized.contains(phrase)) ids.add("urgency:$phrase")
        }
        for (keyword in ProseioRegistries.receiptKeywords) {
            if (normalized.contains(keyword)) ids.add("receipt:$keyword")
        }
        return ids
    }

    /**
     * The one warning to show for a message, or `null`. Cached by event ID, so timeline rebuilds stay cheap.
     * The body is part of the key because an edit keeps the event ID.
     */
    fun warningKind(message: String, eventId: String): ScamWarningKind? {
        val bodyHash = message.hashCode()
        synchronized(warningCache) {
            val cached = warningCache[eventId]
            if (cached != null && cached.bodyHash == bodyHash) return cached.kind
        }

        val kind = warningKind(matchIds(message), message)
        synchronized(warningCache) {
            if (warningCache.size >= MAX_CACHED_EVENTS) warningCache.clear()
            warningCache[eventId] = CachedWarning(bodyHash, kind)
        }
        return kind
    }

    /**
     * Picks the most actionable warning from [matchIds] output.
     * Must match proseio-shared/test-vectors/scam-warnings.json.
     */
    fun warningKind(matchIds: List<String>, message: String): ScamWarningKind? {
        val groups = matchIds.map { it.substringBefore(':') }.toSet()
        val phrases = matchIds.filter { it.startsWith(URGENCY_PREFIX) }.map { it.removePrefix(URGENCY_PREFIX) }

        return warningKindPriority.firstOrNull { kind ->
            when (kind) {
                ScamWarningKind.PIX_CODE -> "pixCopyPastePatterns" in groups
                ScamWarningKind.BOLETO -> "boletoPatterns" in groups
                // A phone number or e-mail alone is normal chat. Warn only when the message also talks about Pix.
                ScamWarningKind.PIX_KEY -> "pixKeyPatterns" in groups && mentionsPix(message)
                ScamWarningKind.RECEIPT -> "receipt" in groups
                ScamWarningKind.NEW_NUMBER -> phrases.any { it in ProseioRegistries.newNumberPhrases }
                ScamWarningKind.URGENCY -> phrases.isNotEmpty()
            }
        }
    }

    private const val URGENCY_PREFIX = "urgency:"
    private const val MAX_CACHED_EVENTS = 2000

    private class CompiledPattern(val matchId: String, val expression: Pattern)

    private data class CachedWarning(val bodyHash: Int, val kind: ScamWarningKind?)

    private val warningCache = HashMap<String, CachedWarning>()

    /** proseio-shared validates that the priority lists every kind once, so `mapNotNull` drops nothing. */
    private val warningKindPriority = ProseioRegistries.scamWarningKindPriority.mapNotNull(ScamWarningKind::fromId)

    private val compiledPatterns: List<CompiledPattern> by lazy {
        val groups = listOf(
            "pixKeyPatterns" to ProseioRegistries.pixKeyPatterns,
            "pixCopyPastePatterns" to ProseioRegistries.pixCopyPastePatterns,
            "boletoPatterns" to ProseioRegistries.boletoPatterns,
        )
        // UNICODE_CHARACTER_CLASS makes `\s` match Unicode spaces, as in V8 and ICU. The registries use explicit
        // ASCII classes for digits and letters, so the flag changes nothing else.
        // proseio-shared validates every regex at generation time, so a compile failure here is not expected.
        groups.flatMap { (name, patterns) ->
            patterns.mapNotNull { pattern ->
                runCatching { Pattern.compile(pattern.regex, Pattern.UNICODE_CHARACTER_CLASS) }
                    .getOrNull()
                    ?.let { CompiledPattern("$name:${pattern.id}", it) }
            }
        }
    }

    private fun mentionsPix(message: String): Boolean {
        val normalized = NameNormalizer.normalize(message, homoglyphs = emptyMap())
        return ProseioRegistries.pixKeyContextWords.any { normalized.contains(it) }
    }
}
