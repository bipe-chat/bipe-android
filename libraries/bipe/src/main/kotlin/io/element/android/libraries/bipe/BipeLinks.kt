/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe

import io.element.android.libraries.bipe.generated.BipeRegistries
import java.net.URI
import java.util.Locale

/**
 * Profile links: `https://bipe.chat/@handle`. The web page there falls back to the store;
 * the app opens it as an app link. Links on the old host `proseio.com.br` still parse: it redirects to `bipe.chat`.
 */
object BipeProfileLink {
    const val HOST = "bipe.chat"

    /** Old host, kept so shared old links still open. It redirects to [HOST]. */
    private const val LEGACY_HOST = "proseio.com.br"
    private val acceptedHosts = setOf(HOST, "www.$HOST", LEGACY_HOST, "www.$LEGACY_HOST")

    fun url(handle: String): String? =
        runCatching { URI("https", HOST, "/@$handle", null).toASCIIString() }.getOrNull()

    /** The handle in a profile link, lowercased, or `null` when the URL is not one. */
    fun handle(url: String): String? {
        val components = bipePathComponents(url) ?: return null
        val first = components.singleOrNull()?.takeIf { it.startsWith("@") } ?: return null
        val handle = BipeHandle.sanitize(first).lowercase(Locale.ROOT)
        return handle.takeIf { BipeHandle.localProblem(it) == null }
    }

    /** The Matrix user ID in a `https://matrix.to/#/@user:server` permalink. */
    fun matrixUserId(permalink: String): String? {
        val uri = runCatching { URI(permalink) }.getOrNull() ?: return null
        if (uri.host != "matrix.to") return null
        val fragment = uri.fragment ?: return null
        val userId = fragment.substringBefore('?').removePrefix("/")
        return userId.takeIf { it.startsWith("@") && !it.contains('/') }
    }

    /** Decoded, non-empty path components of an `https` URL on the Bipe host, or `null`. */
    internal fun bipePathComponents(url: String): List<String>? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT)
        if (uri.scheme != "https" || host !in acceptedHosts) return null
        return uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    }
}

/**
 * Invite links: `https://bipe.chat/convite/XXXX-XXXX-XXXX`. The web page there explains how to install;
 * the app opens it as an app link and pre-fills the sign-up code.
 */
object BipeInviteLink {
    /** Rule: proseio-shared/registries/invite-codes.json (generated). Cases: test-vectors/invite-codes.json. */
    private val pattern = Regex(BipeRegistries.INVITE_CODE_PATTERN)

    /** The invite code in a link, normalised, or `null` when the URL is not an invite link. */
    fun code(url: String): String? {
        val components = BipeProfileLink.bipePathComponents(url) ?: return null
        if (components.size != 2 || components[0] != "convite") return null
        return normalizedCode(components[1])
    }

    /** `XXXX-XXXX-XXXX` from anything a user typed or pasted: a code, an invite link or a whole message. `null` when it holds no code. */
    fun normalizedCode(raw: String): String? {
        val text = raw.take(BipeRegistries.INVITE_CODE_MAX_INPUT_LENGTH).uppercase(Locale.ROOT)
        val match = pattern.find(text) ?: return null
        return match.groupValues.drop(1).joinToString("-")
    }
}

/** A normalised invite code. Prints redacted, because routes and actions get logged. */
@JvmInline
value class BipeInviteCode private constructor(val value: String) {
    override fun toString(): String = "BipeInviteCode(redacted)"

    companion object {
        /** `null` when [raw] cannot be a code. */
        fun parse(raw: String): BipeInviteCode? = BipeInviteLink.normalizedCode(raw)?.let(::BipeInviteCode)
    }
}
