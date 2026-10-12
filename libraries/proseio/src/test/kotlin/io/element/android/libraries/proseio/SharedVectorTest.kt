/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio

import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import java.io.File

/** Every case in proseio-shared/test-vectors must pass (ADR 0031, rule 4). iOS runs the same files. */
class SharedVectorTest {
    @Test
    fun normalization() {
        for (vector in vectors("normalization.json", "vectors")) {
            val input = vector.string("input")!!
            assertWithMessage("normalize($input)").that(NameNormalizer.normalize(input)).isEqualTo(vector.string("normalized"))
            assertWithMessage("reserved($input)").that(NameNormalizer.reservedCategory(input)?.id).isEqualTo(vector.string("reserved"))
        }
    }

    @Test
    fun scamDetection() {
        for (vector in vectors("scam-detection.json", "vectors")) {
            val message = vector.string("message")!!
            val expected = vector.getValue("matches").jsonArray.map { it.jsonPrimitive.content }
            assertWithMessage("matches($message)").that(ScamDetector.matchIds(message)).isEqualTo(expected)
        }
    }

    @Test
    fun scamWarnings() {
        for (vector in vectors("scam-warnings.json", "vectors")) {
            val message = vector.string("message")!!
            val kind = ScamDetector.warningKind(ScamDetector.matchIds(message), message)
            assertWithMessage("kind($message)").that(kind?.id).isEqualTo(vector.string("kind"))
        }
    }

    /** The client checks length and characters only. Reserved names and lookalikes are the server's call. */
    @Test
    fun handles() {
        val localProblems = setOf("too_short", "too_long", "invalid_characters")
        for (vector in vectors("handles.json", "cases")) {
            val input = vector.string("input")!!
            val expected = vector.string("problem")?.takeIf { it in localProblems }
            assertWithMessage("localProblem($input)").that(ProseioHandle.localProblem(input)?.id).isEqualTo(expected)
        }
    }

    @Test
    fun inviteCodes() {
        for (vector in vectors("invite-codes.json", "vectors")) {
            val input = vector.string("input")!!
            assertWithMessage("normalizedCode($input)").that(ProseioInviteLink.normalizedCode(input)).isEqualTo(vector.string("code"))
        }
    }

    @Test
    fun badges() {
        val file = load("badges.json")
        val nowMillis = file.getValue("now").jsonPrimitive.long * 1000
        val keys = file.getValue("publicKeys").jsonObject.mapValues { (_, key) -> Base64Url.decode(key.jsonPrimitive.content)!! }
        val verifier = BadgeVerifier(keys)
        for (vector in file.getValue("vectors").jsonArray.map { it.jsonObject }) {
            val result = verifier.verify(
                envelopeJson = vector.getValue("envelope").toString(),
                sender = vector.string("sender")!!,
                senderDevice = vector.string("senderDevice")!!,
                nowMillis = nowMillis,
            )
            val outcome = when (result) {
                is BadgeVerificationResult.Valid -> "valid"
                is BadgeVerificationResult.Invalid -> result.failure.code
            }
            assertWithMessage("badge ${vector.string("name")}").that(outcome).isEqualTo(vector.string("expect"))
        }
    }

    private fun vectors(name: String, key: String): List<JsonObject> = (load(name).getValue(key) as JsonArray).map { it.jsonObject }

    private fun load(name: String): JsonObject = Json.parseToJsonElement(File(vectorsDir, name).readText()).jsonObject

    private fun JsonObject.string(key: String): String? = (get(key) as JsonElement?)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    private companion object {
        /** proseio-shared next to this repo. Unit tests run in the module directory. PROSEIO_SHARED overrides it. */
        val vectorsDir: File = File(System.getenv("PROSEIO_SHARED") ?: "../../../proseio-shared", "test-vectors")
    }
}
