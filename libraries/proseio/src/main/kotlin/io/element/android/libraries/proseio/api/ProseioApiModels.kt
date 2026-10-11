/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.api

import io.element.android.libraries.proseio.ProseioHandleProblem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime

// Models of proseio-shared/api/openapi.yaml. JSON keys are snake_case.

@Serializable
data class ProseioSignupRequest(
    /** Optional while the server runs open sign-up (beta). */
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("handle") val handle: String?,
    @SerialName("display_name") val displayName: String?,
) {
    /** Never prints the password or the email. */
    override fun toString(): String = "ProseioSignupRequest"
}

@Serializable
data class ProseioSignupResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("handle") val handle: String? = null,
    /** Present when a personal invite was used. */
    @SerialName("inviter_user_id") val inviterUserId: String? = null,
)

@Serializable
data class ProseioInvitePreview(
    @SerialName("valid") val valid: Boolean,
    /** Without @. Only for a valid personal invite whose inviter has a handle. */
    @SerialName("inviter_handle") val inviterHandle: String? = null,
)

enum class ProseioInviteStatus(val id: String) {
    PENDING("pending"),
    USED("used"),
    EXPIRED("expired"),
    REVOKED("revoked"),
}

@Serializable
data class ProseioInvite(
    @SerialName("id") val id: String,
    @SerialName("status") private val statusId: String,
    @Serializable(with = InstantSerializer::class) @SerialName("created_at") val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) @SerialName("expires_at") val expiresAt: Instant,
    /** Only when [status] is `USED`. */
    @SerialName("joined_user_id") val joinedUserId: String? = null,
) {
    /** An unknown status from a newer server must not fail the whole list. */
    val status: ProseioInviteStatus
        get() = ProseioInviteStatus.entries.firstOrNull { it.id == statusId } ?: ProseioInviteStatus.EXPIRED
}

@Serializable
data class ProseioMyInvites(
    @SerialName("quota") val quota: Int,
    @SerialName("available") val available: Int,
    @SerialName("invites") val invites: List<ProseioInvite>,
)

@Serializable
data class ProseioCreatedInvite(
    @SerialName("id") val id: String,
    @SerialName("code") val code: String,
    @SerialName("url") val url: String,
    @Serializable(with = InstantSerializer::class) @SerialName("expires_at") val expiresAt: Instant,
) {
    /** Never prints the code. */
    override fun toString(): String = "ProseioCreatedInvite($id)"
}

@Serializable
data class ProseioHandleAvailability(
    @SerialName("handle") val handle: String,
    @SerialName("available") val available: Boolean,
    @SerialName("reason") private val reason: String? = null,
) {
    /** An unknown reason from a newer server must not fail the whole response. */
    val problem: ProseioHandleProblem?
        get() = reason?.let { ProseioHandleProblem.fromId(it) ?: ProseioHandleProblem.TAKEN }
}

@Serializable
data class ProseioHandleRecord(
    @SerialName("handle") val handle: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
data class ProseioMyHandle(
    @SerialName("user_id") val userId: String,
    @SerialName("handle") val handle: String? = null,
    /** `null` when a change is allowed now. */
    @Serializable(with = InstantSerializer::class) @SerialName("changeable_at") val changeableAt: Instant? = null,
)

@Serializable
internal data class ResolveHandlesRequest(@SerialName("user_ids") val userIds: List<String>)

@Serializable
internal data class ResolveHandlesResponse(@SerialName("handles") val handles: Map<String, String>)

@Serializable
internal data class SetHandleRequest(@SerialName("handle") val handle: String)

@Serializable
internal data class ProseioErrorBody(
    @SerialName("error") val error: String,
    @SerialName("reason") val reason: String? = null,
)

/** ISO 8601 with `Z` or an offset, with or without fractional seconds. */
internal object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("ProseioInstant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = OffsetDateTime.parse(decoder.decodeString()).toInstant()
}
