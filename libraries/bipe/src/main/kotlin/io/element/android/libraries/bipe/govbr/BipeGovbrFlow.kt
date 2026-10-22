/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.govbr

import io.element.android.libraries.bipe.BadgeEnvelope
import io.element.android.libraries.bipe.BadgeLevel
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.api.BipeBadgeLookup
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Base64

/**
 * Trust step 5 (bipe#545, bipe docs/identity.md): the user signs a Bipe challenge PDF in the gov.br app.
 * The server makes the PDF and checks the signed file. The app saves the PDF, sends the user to gov.br and uploads the file that comes back.
 * Port of BipeGovbrFlow on iOS.
 */
class BipeGovbrFlow(
    private val apiClient: BipeApiClient,
    private val accessToken: suspend () -> String?,
) {
    sealed interface Phase {
        data object Ready : Phase
        data object Preparing : Phase

        /** The user saves [pdf] and signs it in the gov.br app. Not a data class: [pdf] is an array. */
        class WaitingForSignature(val pdf: ByteArray, val fileName: String) : Phase
        data object Checking : Phase

        /** [name] is the verified name from the new badge, when the server sent one. */
        data class Verified(val name: String?) : Phase
        data class Rejected(val reason: BipeGovbrReason) : Phase
        data object Failed : Phase
    }

    private val mutablePhase = MutableStateFlow<Phase>(Phase.Ready)
    val phase: StateFlow<Phase> = mutablePhase.asStateFlow()

    private var verificationId: String? = null

    /** Gets a new challenge. Also the "Tentar de novo" action: a new challenge is always valid. */
    suspend fun start() {
        mutablePhase.value = Phase.Preparing
        mutablePhase.value = runCatchingExceptions {
            val challenge = BipeApiClient.withTokenRetry(accessToken) { apiClient.startGovbrVerification(it) }
            verificationId = challenge.verificationId
            Phase.WaitingForSignature(Base64.getDecoder().decode(challenge.pdf), challenge.fileName)
        }.getOrElse {
            Timber.w(it, "Bipe: gov.br challenge failed")
            Phase.Failed
        }
    }

    /** Uploads the signed file from the "Já assinei" picker. */
    suspend fun submit(pdf: ByteArray) {
        val id = verificationId
        if (id == null) {
            mutablePhase.value = Phase.Failed
            return
        }
        if (pdf.isEmpty() || pdf.size > MAX_DOCUMENT_BYTES) {
            mutablePhase.value = Phase.Rejected(BipeGovbrReason.NOT_SIGNED)
            return
        }
        mutablePhase.value = Phase.Checking
        mutablePhase.value = runCatchingExceptions {
            val status = BipeApiClient.withTokenRetry(accessToken) { apiClient.sendGovbrDocument(id, pdf, it) }
            when (status.state) {
                "verified" -> Phase.Verified(status.badge?.unverifiedPayload()?.name)
                "rejected", "expired" -> Phase.Rejected(BipeGovbrReason.fromId(status.reason))
                else -> Phase.Failed
            }
        }.getOrElse {
            Timber.w(it, "Bipe: gov.br document check failed")
            Phase.Failed
        }
    }

    companion object {
        /** The gov.br app on Google Play. */
        const val GOVBR_PACKAGE = "br.gov.meugovbr"

        /** The API's limit for the signed file. */
        const val MAX_DOCUMENT_BYTES = 5 * 1024 * 1024
    }
}

/** `VerificationStatus.reason`. */
enum class BipeGovbrReason(val id: String) {
    NOT_SIGNED("not_signed"),
    NOT_GOVBR("not_govbr"),
    WRONG_DOCUMENT("wrong_document"),
    EXPIRED("expired"),
    CPF_IN_USE("cpf_in_use"),
    CHANGED_AFTER_SIGNING("changed_after_signing");

    companion object {
        fun fromId(id: String?): BipeGovbrReason = entries.firstOrNull { it.id == id } ?: EXPIRED
    }
}

/**
 * The level of the envelope in a 200 answer, read without the signature check.
 * Only for the owner's own trust card, never to show a seal: seals go through BadgeVerifier.
 */
fun BipeBadgeLookup.unverifiedLevel(): BadgeLevel? {
    val envelope = (this as? BipeBadgeLookup.Envelope)?.json ?: return null
    val lvl = runCatchingExceptions { json.decodeFromString<BadgeEnvelope>(envelope) }.getOrNull()?.unverifiedPayload()?.lvl
    return BadgeLevel.entries.firstOrNull { it.name.equals(lvl, ignoreCase = true) }
}

@Serializable
private data class LoosePayload(val name: String? = null, val lvl: String? = null)

private val json = Json { ignoreUnknownKeys = true }

private fun BadgeEnvelope.unverifiedPayload(): LoosePayload? = runCatchingExceptions {
    json.decodeFromString<LoosePayload>(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
}.getOrNull()
