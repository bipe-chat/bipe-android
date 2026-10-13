/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe

import androidx.annotation.StringRes
import io.element.android.libraries.bipe.generated.BipeRegistries
import java.util.regex.Pattern

/** The scam pattern a message looks like (security.md rule 9). Friction, not protection. */
enum class ScamWarningKind(val id: String, @StringRes val messageRes: Int) {
    PIX_CODE("pixCode", R.string.bipe_scam_warning_pix_code),
    BOLETO("boleto", R.string.bipe_scam_warning_boleto),
    PIX_KEY("pixKey", R.string.bipe_scam_warning_pix_key),
    DELIVERY_FEE("deliveryFee", R.string.bipe_scam_warning_delivery_fee),
    LOOKALIKE_LINK("lookalikeLink", R.string.bipe_scam_warning_lookalike_link),
    BANK_ALERT("bankAlert", R.string.bipe_scam_warning_bank_alert),
    RECEIPT("receipt", R.string.bipe_scam_warning_receipt),
    NEW_NUMBER("newNumber", R.string.bipe_scam_warning_new_number),
    URGENCY("urgency", R.string.bipe_scam_warning_urgency);

    companion object {
        fun fromId(id: String): ScamWarningKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Flags payment-scam signals in a message.
 * Must match bipe-shared/test-vectors/scam-detection.json and scam-warnings.json.
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
        for (phrase in BipeRegistries.urgencyPhrases) {
            if (normalized.contains(phrase)) ids.add("urgency:$phrase")
        }
        for (keyword in BipeRegistries.receiptKeywords) {
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
     * Must match bipe-shared/test-vectors/scam-warnings.json.
     */
    fun warningKind(matchIds: List<String>, message: String): ScamWarningKind? {
        val groups = matchIds.map { it.substringBefore(':') }.toSet()
        val phrases = matchIds.filter { it.startsWith(URGENCY_PREFIX) }.map { it.removePrefix(URGENCY_PREFIX) }
        val normalized = NameNormalizer.normalize(message, homoglyphs = emptyMap())
        val hosts = linkHosts(message)

        return warningKindPriority.firstOrNull { kind ->
            when (kind) {
                ScamWarningKind.PIX_CODE -> "pixCopyPastePatterns" in groups
                ScamWarningKind.BOLETO -> "boletoPatterns" in groups
                // A phone number or e-mail alone is normal chat. Warn only when the message also talks about Pix.
                ScamWarningKind.PIX_KEY -> "pixKeyPatterns" in groups && containsAny(normalized, BipeRegistries.pixKeyContextWords)
                // The fake-delivery fee scam: a link, and the message talks about a held parcel and a fee.
                ScamWarningKind.DELIVERY_FEE -> hosts.isNotEmpty() && deliveryWordLists.all { containsAny(normalized, it) }
                // A link that copies a bank, government or company address, also when spelled with homoglyphs.
                ScamWarningKind.LOOKALIKE_LINK -> hosts.any(::isLookalike)
                // The fake bank alert: a link, and the message says the account, card or Pix is blocked or needs an update.
                ScamWarningKind.BANK_ALERT -> hosts.isNotEmpty() && bankAlertWordLists.all { containsAny(normalized, it) }
                ScamWarningKind.RECEIPT -> "receipt" in groups
                // The falso parente opener. On Bipe it says new account, which is normal chat without a family term or urgency.
                ScamWarningKind.NEW_NUMBER -> phrases.any { it in BipeRegistries.newNumberPhrases } || isNewAccountOpener(normalized, phrases)
                ScamWarningKind.URGENCY -> phrases.isNotEmpty()
            }
        }
    }

    /**
     * A link host. [folded] is a link-pattern match on the host-folded message. [plain] is the same UTF-16 range
     * of the message normalised without folding, so it keeps homoglyphs.
     */
    data class LinkHost(val folded: String, val plain: String)

    /** Every link host in the message. Must match bipe-shared `linkHosts`. */
    fun linkHosts(message: String): List<LinkHost> {
        val plain = NameNormalizer.normalize(message, homoglyphs = emptyMap())
        val folded = NameNormalizer.normalize(message, homoglyphs = BipeRegistries.hostHomoglyphs)
        // bipe-shared validates that host folding keeps every UTF-16 index. An empty plain host is never official.
        val aligned = plain.length == folded.length
        return linkExpressions.flatMap { expression ->
            val matcher = expression.matcher(folded)
            buildList {
                while (matcher.find()) {
                    add(LinkHost(matcher.group(), if (aligned) plain.substring(matcher.start(), matcher.end()) else ""))
                }
            }
        }
    }

    private const val URGENCY_PREFIX = "urgency:"
    private const val MAX_CACHED_EVENTS = 2000

    private class CompiledPattern(val matchId: String, val expression: Pattern)

    private data class CachedWarning(val bodyHash: Int, val kind: ScamWarningKind?)

    private val warningCache = HashMap<String, CachedWarning>()

    /** bipe-shared validates that the priority lists every kind once, so `mapNotNull` drops nothing. */
    private val warningKindPriority = BipeRegistries.scamWarningKindPriority.mapNotNull(ScamWarningKind::fromId)

    private val compiledPatterns: List<CompiledPattern> by lazy {
        val groups = listOf(
            "pixKeyPatterns" to BipeRegistries.pixKeyPatterns,
            "pixCopyPastePatterns" to BipeRegistries.pixCopyPastePatterns,
            "boletoPatterns" to BipeRegistries.boletoPatterns,
            "linkPatterns" to BipeRegistries.linkPatterns,
        )
        // UNICODE_CHARACTER_CLASS makes `\s` match Unicode spaces, as in V8 and ICU. The registries use explicit
        // ASCII classes for digits and letters, so the flag changes nothing else.
        // bipe-shared validates every regex at generation time, so a compile failure here is not expected.
        groups.flatMap { (name, patterns) ->
            patterns.mapNotNull { pattern ->
                runCatching { Pattern.compile(pattern.regex, Pattern.UNICODE_CHARACTER_CLASS) }
                    .getOrNull()
                    ?.let { CompiledPattern("$name:${pattern.id}", it) }
            }
        }
    }

    private val linkExpressions: List<Pattern> by lazy {
        BipeRegistries.linkPatterns.mapNotNull { runCatching { Pattern.compile(it.regex, Pattern.UNICODE_CHARACTER_CLASS) }.getOrNull() }
    }

    private val bankAlertWordLists = listOf(
        BipeRegistries.bankAlertSubjectWords,
        BipeRegistries.bankAlertThreatWords,
    )

    /** True when the host copies a brand: its folded form names a brand and its plain form is not that brand's domain. */
    private fun isLookalike(host: LinkHost): Boolean {
        val squashed = host.folded.replace(".", "").replace("-", "")
        if (BipeRegistries.lookalikeBrandTokens.none { squashed.contains(it) }) return false
        return BipeRegistries.officialDomains.none { host.plain == it || host.plain.endsWith(".$it") }
    }

    private val deliveryWordLists = listOf(
        BipeRegistries.deliveryParcelWords,
        BipeRegistries.deliveryHoldWords,
        BipeRegistries.deliveryFeeWords,
    )

    private fun containsAny(normalized: String, words: List<String>): Boolean = words.any { normalized.contains(it) }

    private fun isNewAccountOpener(normalized: String, phrases: List<String>): Boolean =
        containsAny(normalized, BipeRegistries.newAccountPhrases) &&
            (phrases.isNotEmpty() || containsWord(normalized, BipeRegistries.familyTerms))

    /**
     * True when the normalised text has one of [terms] as a whole word: each run of characters outside [0-9a-z]
     * becomes one space, then the term must have a space on each side.
     */
    private fun containsWord(normalized: String, terms: Set<String>): Boolean {
        val words = " ${normalized.replace(nonWordRuns, " ")} "
        return terms.any { words.contains(" $it ") }
    }

    private val nonWordRuns = Regex("[^0-9a-z]+")
}
