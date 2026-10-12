/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio

import io.element.android.libraries.proseio.generated.ProseioRegistries
import java.net.URI
import java.util.Locale

/**
 * Profile links: `https://proseio.com.br/@handle`. The web page there falls back to the store;
 * the app opens it as an app link.
 */
object ProseioProfileLink {
    const val HOST = "proseio.com.br"

    fun url(handle: String): String? =
        runCatching { URI("https", HOST, "/@$handle", null).toASCIIString() }.getOrNull()

    /** The handle in a profile link, lowercased, or `null` when the URL is not one. */
    fun handle(url: String): String? {
        val components = proseioPathComponents(url) ?: return null
        val first = components.singleOrNull()?.takeIf { it.startsWith("@") } ?: return null
        val handle = ProseioHandle.sanitize(first).lowercase(Locale.ROOT)
        return handle.takeIf { ProseioHandle.localProblem(it) == null }
    }

    /** The Matrix user ID in a `https://matrix.to/#/@user:server` permalink. */
    fun matrixUserId(permalink: String): String? {
        val uri = runCatching { URI(permalink) }.getOrNull() ?: return null
        if (uri.host != "matrix.to") return null
        val fragment = uri.fragment ?: return null
        val userId = fragment.substringBefore('?').removePrefix("/")
        return userId.takeIf { it.startsWith("@") && !it.contains('/') }
    }

    /** Decoded, non-empty path components of an `https` URL on the Proseio host, or `null`. */
    internal fun proseioPathComponents(url: String): List<String>? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT)
        if (uri.scheme != "https" || (host != HOST && host != "www.$HOST")) return null
        return uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    }
}

/**
 * Invite links: `https://proseio.com.br/convite/XXXX-XXXX-XXXX`. The web page there explains how to install;
 * the app opens it as an app link and pre-fills the sign-up code.
 */
object ProseioInviteLink {
    /** Rule: proseio-shared/registries/invite-codes.json (generated). Cases: test-vectors/invite-codes.json. */
    private val pattern = Regex(ProseioRegistries.INVITE_CODE_PATTERN)

    /** The invite code in a link, normalised, or `null` when the URL is not an invite link. */
    fun code(url: String): String? {
        val components = ProseioProfileLink.proseioPathComponents(url) ?: return null
        if (components.size != 2 || components[0] != "convite") return null
        return normalizedCode(components[1])
    }

    /** `XXXX-XXXX-XXXX` from anything a user typed or pasted: a code, an invite link or a whole message. `null` when it holds no code. */
    fun normalizedCode(raw: String): String? {
        val text = raw.take(ProseioRegistries.INVITE_CODE_MAX_INPUT_LENGTH).uppercase(Locale.ROOT)
        val match = pattern.find(text) ?: return null
        return match.groupValues.drop(1).joinToString("-")
    }
}

/** A normalised invite code. Prints redacted, because routes and actions get logged. */
@JvmInline
value class ProseioInviteCode private constructor(val value: String) {
    override fun toString(): String = "ProseioInviteCode(redacted)"

    companion object {
        /** `null` when [raw] cannot be a code. */
        fun parse(raw: String): ProseioInviteCode? = ProseioInviteLink.normalizedCode(raw)?.let(::ProseioInviteCode)
    }
}
