/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.api

import io.element.android.libraries.core.uri.ensureTrailingSlash
import io.element.android.libraries.bipe.BipeHandleProblem
import io.element.android.libraries.bipe.BipeProfileProblem
import io.element.android.libraries.bipe.R
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Client for the Bipe API. Contract: proseio-shared/api/openapi.yaml. */
class BipeApiClient internal constructor(private val service: BipeApiService) {
    // Sign-up

    /** `POST /v1/signup`. No auth. */
    suspend fun signup(request: BipeSignupRequest): BipeSignupResponse = call { service.signup(request) }

    // Invites

    /** `GET /v1/invites/{code}`. No auth. Validity and the inviter's handle. */
    suspend fun invitePreview(code: String): BipeInvitePreview = call { service.invitePreview(code) }

    /** `GET /v1/me/invites`. */
    suspend fun myInvites(accessToken: String): BipeMyInvites = call { service.myInvites(bearer(accessToken)) }

    /** `POST /v1/me/invites`. The code is returned once. */
    suspend fun createInvite(accessToken: String): BipeCreatedInvite = call { service.createInvite(bearer(accessToken)) }

    /** `DELETE /v1/me/invites/{inviteId}`. Revokes a pending invite. */
    suspend fun revokeInvite(id: String, accessToken: String) {
        // Retrofit returns a null body for 204, so this call checks the status only.
        checked { service.revokeInvite(bearer(accessToken), id) }
    }

    // Handles

    /** `GET /v1/handles/{handle}/availability`. No auth. */
    suspend fun handleAvailability(handle: String): BipeHandleAvailability = call { service.handleAvailability(handle) }

    /** `GET /v1/handles/{handle}`. Exact lookup. */
    suspend fun handle(handle: String, accessToken: String): BipeHandleRecord = call { service.handle(bearer(accessToken), handle) }

    /** `POST /v1/handles/resolve`. Returns Matrix ID to handle. IDs without a handle are absent. */
    suspend fun resolveHandles(userIds: List<String>, accessToken: String): Map<String, String> =
        call { service.resolveHandles(bearer(accessToken), ResolveHandlesRequest(userIds)) }.handles

    /** `GET /v1/me/handle`. */
    suspend fun myHandle(accessToken: String): BipeMyHandle = call { service.myHandle(bearer(accessToken)) }

    /** `PUT /v1/me/handle`. */
    suspend fun setMyHandle(handle: String, accessToken: String): BipeMyHandle =
        call { service.setMyHandle(bearer(accessToken), SetHandleRequest(handle)) }

    // Public profiles (ADR 0041)

    /** `GET /v1/profiles/{handle}`. No auth. HTTP 404 for "no such handle" and "profile off" alike. */
    suspend fun publicProfile(handle: String): BipePublicProfile = call { service.publicProfile(handle) }

    /** `GET /v1/me/profile`. */
    suspend fun myProfile(accessToken: String): BipeMyProfile = call { service.myProfile(bearer(accessToken)) }

    /** `PUT /v1/me/profile`. Error `profile_invalid` carries a [BipeProfileProblem] id as the reason. */
    suspend fun setMyProfile(update: BipeProfileUpdate, accessToken: String): BipeMyProfile =
        call { service.setMyProfile(bearer(accessToken), update) }

    /** `PUT /v1/me/profile/photo`. [jpeg] must already be re-encoded within the limits of registries/profiles.json. */
    suspend fun setMyProfilePhoto(jpeg: ByteArray, accessToken: String): BipeMyProfile =
        call { service.setMyProfilePhoto(bearer(accessToken), jpeg.toRequestBody(JPEG)) }

    /** `DELETE /v1/me/profile/photo`. */
    suspend fun deleteMyProfilePhoto(accessToken: String): BipeMyProfile = call { service.deleteMyProfilePhoto(bearer(accessToken)) }

    companion object {
        private val JPEG = "image/jpeg".toMediaType()

        /**
         * [baseUrl] is the API root, e.g. `https://api.proseio.com.br`.
         * [callFactory] must not log bodies or headers: requests carry passwords and access tokens.
         */
        fun create(callFactory: Call.Factory, json: Json, baseUrl: String): BipeApiClient {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl.ensureTrailingSlash())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .callFactory(callFactory)
                .build()
            return BipeApiClient(retrofit.create(BipeApiService::class.java))
        }

        /**
         * Runs [request] with the token from [accessToken]. On HTTP 401 it reads the token again and retries once,
         * because Matrix access tokens are short-lived and the SDK may have refreshed it meanwhile.
         */
        suspend fun <T> withTokenRetry(accessToken: suspend () -> String?, request: suspend (String) -> T): T {
            val token = accessToken() ?: throw BipeApiException.Server(BipeApiErrorCode.UNAUTHORIZED, reason = null, statusCode = 401)
            return try {
                request(token)
            } catch (error: BipeApiException) {
                val freshToken = if (error.isUnauthorized) accessToken() else null
                freshToken ?: throw error
                request(freshToken)
            }
        }

        private val errorJson = Json { ignoreUnknownKeys = true }
    }

    private fun bearer(accessToken: String) = "Bearer $accessToken"

    private suspend fun <T : Any> call(block: suspend () -> Response<T>): T {
        val response = checked(block)
        return response.body() ?: throw BipeApiException.UnexpectedResponse(statusCode = response.code())
    }

    /** Sends the request and maps transport failures and non-2xx answers to [BipeApiException]. */
    private suspend fun <T> checked(block: suspend () -> Response<T>): Response<T> {
        val response = try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: java.io.IOException) {
            throw BipeApiException.Network(error)
        } catch (error: Exception) {
            // Encoding and decoding failures: the answer is outside the contract.
            throw BipeApiException.UnexpectedResponse(statusCode = null, cause = error)
        }

        if (!response.isSuccessful) {
            val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                ?.let { runCatching { errorJson.decodeFromString(BipeErrorBody.serializer(), it) }.getOrNull() }
            if (errorBody != null) {
                throw BipeApiException.Server(
                    code = BipeApiErrorCode.fromId(errorBody.error) ?: BipeApiErrorCode.INTERNAL,
                    reason = errorBody.reason,
                    statusCode = response.code(),
                )
            }
            throw BipeApiException.UnexpectedResponse(statusCode = response.code())
        }
        return response
    }
}

/** Retrofit encodes each `@Path` value, so a handle cannot add path segments. */
internal interface BipeApiService {
    @POST("v1/signup")
    suspend fun signup(@Body request: BipeSignupRequest): Response<BipeSignupResponse>

    @GET("v1/invites/{code}")
    suspend fun invitePreview(@Path("code") code: String): Response<BipeInvitePreview>

    @GET("v1/me/invites")
    suspend fun myInvites(@Header("Authorization") authorization: String): Response<BipeMyInvites>

    @POST("v1/me/invites")
    suspend fun createInvite(@Header("Authorization") authorization: String): Response<BipeCreatedInvite>

    @DELETE("v1/me/invites/{inviteId}")
    suspend fun revokeInvite(@Header("Authorization") authorization: String, @Path("inviteId") inviteId: String): Response<Unit>

    @GET("v1/handles/{handle}/availability")
    suspend fun handleAvailability(@Path("handle") handle: String): Response<BipeHandleAvailability>

    @GET("v1/handles/{handle}")
    suspend fun handle(@Header("Authorization") authorization: String, @Path("handle") handle: String): Response<BipeHandleRecord>

    @POST("v1/handles/resolve")
    suspend fun resolveHandles(
        @Header("Authorization") authorization: String,
        @Body request: ResolveHandlesRequest,
    ): Response<ResolveHandlesResponse>

    @GET("v1/me/handle")
    suspend fun myHandle(@Header("Authorization") authorization: String): Response<BipeMyHandle>

    @PUT("v1/me/handle")
    suspend fun setMyHandle(@Header("Authorization") authorization: String, @Body request: SetHandleRequest): Response<BipeMyHandle>

    @GET("v1/profiles/{handle}")
    suspend fun publicProfile(@Path("handle") handle: String): Response<BipePublicProfile>

    @GET("v1/me/profile")
    suspend fun myProfile(@Header("Authorization") authorization: String): Response<BipeMyProfile>

    @PUT("v1/me/profile")
    suspend fun setMyProfile(@Header("Authorization") authorization: String, @Body request: BipeProfileUpdate): Response<BipeMyProfile>

    /** Retrofit sends a [RequestBody] as it is, past the JSON converter. */
    @PUT("v1/me/profile/photo")
    suspend fun setMyProfilePhoto(@Header("Authorization") authorization: String, @Body photo: RequestBody): Response<BipeMyProfile>

    @DELETE("v1/me/profile/photo")
    suspend fun deleteMyProfilePhoto(@Header("Authorization") authorization: String): Response<BipeMyProfile>
}

/** The contract's `Error.error` enum. */
enum class BipeApiErrorCode(val id: String) {
    BAD_REQUEST("bad_request"),
    INVITE_INVALID("invite_invalid"),
    INVITE_QUOTA_EXHAUSTED("invite_quota_exhausted"),
    EMAIL_TAKEN("email_taken"),
    HANDLE_INVALID("handle_invalid"),
    HANDLE_TAKEN("handle_taken"),
    HANDLE_COOLDOWN("handle_cooldown"),
    PASSWORD_WEAK("password_weak"),
    ATTESTATION_INVALID("attestation_invalid"),
    PROFILE_INVALID("profile_invalid"),
    PHOTO_INVALID("photo_invalid"),
    UNAUTHORIZED("unauthorized"),
    NOT_FOUND("not_found"),
    RATE_LIMITED("rate_limited"),
    INTERNAL("internal");

    companion object {
        fun fromId(id: String): BipeApiErrorCode? = entries.firstOrNull { it.id == id }
    }
}

sealed class BipeApiException(cause: Throwable? = null) : Exception(cause) {
    /** The server answered with the contract's error body. */
    data class Server(val code: BipeApiErrorCode, val reason: String?, val statusCode: Int) : BipeApiException()

    /** The request did not reach the server or got no answer. */
    class Network(cause: Throwable? = null) : BipeApiException(cause)

    /** The server answered with something outside the contract. */
    class UnexpectedResponse(val statusCode: Int?, cause: Throwable? = null) : BipeApiException(cause)

    /** HTTP 401: the access token was rejected. */
    val isUnauthorized: Boolean
        get() = when (this) {
            is Server -> statusCode == 401
            is UnexpectedResponse -> statusCode == 401
            is Network -> false
        }

    /** User-facing message (pt-BR / en). */
    fun localizedMessage(stringProvider: StringProvider): String = when (this) {
        is Server -> when (code) {
            BipeApiErrorCode.INVITE_INVALID -> stringProvider.getString(R.string.proseio_error_invite_invalid)
            BipeApiErrorCode.INVITE_QUOTA_EXHAUSTED -> stringProvider.getString(R.string.proseio_error_invite_quota)
            BipeApiErrorCode.EMAIL_TAKEN -> stringProvider.getString(R.string.proseio_error_email_taken)
            BipeApiErrorCode.PASSWORD_WEAK -> stringProvider.getString(R.string.proseio_error_password_weak)
            BipeApiErrorCode.HANDLE_TAKEN -> BipeHandleProblem.TAKEN.localizedMessage(stringProvider)
            BipeApiErrorCode.HANDLE_INVALID ->
                (reason?.let(BipeHandleProblem::fromId) ?: BipeHandleProblem.INVALID_CHARACTERS).localizedMessage(stringProvider)
            BipeApiErrorCode.HANDLE_COOLDOWN -> stringProvider.getString(R.string.proseio_error_handle_cooldown)
            BipeApiErrorCode.PROFILE_INVALID ->
                reason?.let(BipeProfileProblem::fromId)?.localizedMessage(stringProvider) ?: stringProvider.getString(R.string.proseio_error_generic)
            BipeApiErrorCode.PHOTO_INVALID -> stringProvider.getString(R.string.proseio_public_profile_photo_invalid)
            BipeApiErrorCode.RATE_LIMITED -> stringProvider.getString(R.string.proseio_error_rate_limited)
            BipeApiErrorCode.UNAUTHORIZED -> stringProvider.getString(R.string.proseio_error_unauthorized)
            // App Attest is iOS only. Android sign-up gets Play Integrity later (contract: Attestation).
            BipeApiErrorCode.ATTESTATION_INVALID,
            BipeApiErrorCode.BAD_REQUEST, BipeApiErrorCode.NOT_FOUND, BipeApiErrorCode.INTERNAL ->
                stringProvider.getString(R.string.proseio_error_generic)
        }
        is Network -> stringProvider.getString(R.string.proseio_error_network)
        is UnexpectedResponse -> stringProvider.getString(R.string.proseio_error_generic)
    }
}
