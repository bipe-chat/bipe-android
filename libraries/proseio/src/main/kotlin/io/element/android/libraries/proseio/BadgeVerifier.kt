/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio

import com.google.crypto.tink.subtle.Ed25519Verify
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.security.GeneralSecurityException

/**
 * Verifies signed badge envelopes (proseio-shared/docs/badge-attestation.md, v1).
 * Must match proseio-shared/test-vectors/badges.json.
 *
 * @param trustedKeys `kid` to raw 32-byte Ed25519 public key. Release builds must not include `test-only-*` keys.
 * @throws IllegalArgumentException when a key is not 32 bytes.
 */
class BadgeVerifier(trustedKeys: Map<String, ByteArray>) {
    private val verifiers: Map<String, Ed25519Verify> = trustedKeys.mapValues { (kid, key) ->
        require(key.size == Ed25519Verify.PUBLIC_KEY_LEN) { "invalid public key for kid $kid" }
        Ed25519Verify(key)
    }

    /** Decodes and verifies envelope JSON. Undecodable JSON is `malformed`. */
    fun verify(envelopeJson: String, sender: String, senderDevice: String, nowMillis: Long = System.currentTimeMillis()): BadgeVerificationResult {
        // Check `v` before the full shape: a future version may have other fields.
        val probe = runCatching { json.decodeFromString<JsonObject>(envelopeJson) }.getOrNull()
        val version = (probe?.get("v") as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?: return BadgeVerificationResult.Invalid(BadgeVerificationFailure.MALFORMED)
        if (version != BadgeEnvelope.SUPPORTED_VERSION) {
            return BadgeVerificationResult.Invalid(BadgeVerificationFailure.UNSUPPORTED_VERSION)
        }
        val envelope = runCatching { json.decodeFromJsonElement(BadgeEnvelope.serializer(), probe) }.getOrNull()
            ?: return BadgeVerificationResult.Invalid(BadgeVerificationFailure.MALFORMED)
        return verify(envelope, sender, senderDevice, nowMillis)
    }

    /** Runs the documented checks in order and returns the first failure. */
    fun verify(envelope: BadgeEnvelope, sender: String, senderDevice: String, nowMillis: Long = System.currentTimeMillis()): BadgeVerificationResult {
        fun invalid(failure: BadgeVerificationFailure) = BadgeVerificationResult.Invalid(failure)

        if (envelope.v != BadgeEnvelope.SUPPORTED_VERSION) return invalid(BadgeVerificationFailure.UNSUPPORTED_VERSION)
        val verifier = verifiers[envelope.kid] ?: return invalid(BadgeVerificationFailure.UNKNOWN_KID)
        // Undecodable base64url is not a signature over any payload bytes, so it fails closed as malformed.
        val payloadBytes = Base64Url.decode(envelope.payload) ?: return invalid(BadgeVerificationFailure.MALFORMED)
        val signature = Base64Url.decode(envelope.sig) ?: return invalid(BadgeVerificationFailure.MALFORMED)
        // Verify the exact bytes. Never re-serialise the JSON before this check.
        try {
            verifier.verify(signature, payloadBytes)
        } catch (_: GeneralSecurityException) {
            return invalid(BadgeVerificationFailure.INVALID_SIGNATURE)
        }
        val payload = runCatching { json.decodeFromString(BadgePayload.serializer(), payloadBytes.decodeToString(throwOnInvalidSequence = true)) }
            .getOrNull()
            ?.takeUnless { it.lvl == BadgeLevel.INSTITUTION && it.cnpj == null }
            ?: return invalid(BadgeVerificationFailure.MALFORMED)

        if (nowMillis >= payload.exp * 1000) return invalid(BadgeVerificationFailure.EXPIRED)
        if (nowMillis < payload.iat * 1000) return invalid(BadgeVerificationFailure.NOT_YET_VALID)
        if (payload.sub != sender) return invalid(BadgeVerificationFailure.SUBJECT_MISMATCH)
        if (payload.dev != senderDevice) return invalid(BadgeVerificationFailure.DEVICE_MISMATCH)
        return BadgeVerificationResult.Valid(payload)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
data class BadgeEnvelope(
    val v: Int,
    val kid: String,
    /** base64url without padding: the UTF-8 JSON payload bytes. */
    val payload: String,
    /** base64url without padding: Ed25519 signature over the decoded payload bytes. */
    val sig: String,
) {
    companion object {
        const val SUPPORTED_VERSION = 1
    }
}

@Serializable
enum class BadgeLevel {
    @SerialName("pix") PIX,
    @SerialName("govbr") GOVBR,
    @SerialName("institution") INSTITUTION,
}

@Serializable
data class BadgePayload(
    /** Matrix user ID. */
    val sub: String,
    val lvl: BadgeLevel,
    /** Verified legal name or institution name. */
    val name: String,
    /** Institution CNPJ, digits only. Present only for `institution`. */
    val cnpj: String? = null,
    /** Fingerprint of the device key the badge is bound to. */
    val dev: String,
    /** Issued at, Unix seconds. */
    val iat: Long,
    /** Expires at, Unix seconds. */
    val exp: Long,
)

sealed interface BadgeVerificationResult {
    data class Valid(val payload: BadgePayload) : BadgeVerificationResult
    data class Invalid(val failure: BadgeVerificationFailure) : BadgeVerificationResult
}

/** [code] values are the codes in badge-attestation.md and test-vectors/badges.json. */
enum class BadgeVerificationFailure(val code: String) {
    UNSUPPORTED_VERSION("unsupported_version"),
    UNKNOWN_KID("unknown_kid"),
    INVALID_SIGNATURE("invalid_signature"),
    MALFORMED("malformed"),
    EXPIRED("expired"),
    NOT_YET_VALID("not_yet_valid"),
    SUBJECT_MISMATCH("subject_mismatch"),
    DEVICE_MISMATCH("device_mismatch"),
}

/**
 * Strict base64url without padding (RFC 4648 section 5). Rejects padding and the standard alphabet.
 * Own decoder because `java.util.Base64` needs API 26 and min SDK is 24.
 */
internal object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun decode(input: String): ByteArray? {
        if (input.length % 4 == 1) return null
        val out = java.io.ByteArrayOutputStream(input.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in input) {
            val value = ALPHABET.indexOf(c)
            if (value < 0) return null
            // Keep only the bits not yet written, so the buffer cannot overflow.
            buffer = ((buffer shl 6) or value) and 0xFFF
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
