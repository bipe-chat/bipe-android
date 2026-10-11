/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.api

import io.element.android.libraries.core.uri.ensureTrailingSlash
import io.element.android.libraries.proseio.ProseioHandleProblem
import io.element.android.libraries.proseio.R
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
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

/** Client for the Proseio API. Contract: proseio-shared/api/openapi.yaml. */
class ProseioApiClient internal constructor(private val service: ProseioApiService) {
    // Sign-up

    /** `POST /v1/signup`. No auth. */
    suspend fun signup(request: ProseioSignupRequest): ProseioSignupResponse = call { service.signup(request) }

    // Invites

    /** `GET /v1/invites/{code}`. No auth. Validity and the inviter's handle. */
    suspend fun invitePreview(code: String): ProseioInvitePreview = call { service.invitePreview(code) }

    /** `GET /v1/me/invites`. */
    suspend fun myInvites(accessToken: String): ProseioMyInvites = call { service.myInvites(bearer(accessToken)) }

    /** `POST /v1/me/invites`. The code is returned once. */
    suspend fun createInvite(accessToken: String): ProseioCreatedInvite = call { service.createInvite(bearer(accessToken)) }

    /** `DELETE /v1/me/invites/{inviteId}`. Revokes a pending invite. */
    suspend fun revokeInvite(id: String, accessToken: String) {
        // Retrofit returns a null body for 204, so this call checks the status only.
        checked { service.revokeInvite(bearer(accessToken), id) }
    }

    // Handles

    /** `GET /v1/handles/{handle}/availability`. No auth. */
    suspend fun handleAvailability(handle: String): ProseioHandleAvailability = call { service.handleAvailability(handle) }

    /** `GET /v1/handles/{handle}`. Exact lookup. */
    suspend fun handle(handle: String, accessToken: String): ProseioHandleRecord = call { service.handle(bearer(accessToken), handle) }

    /** `POST /v1/handles/resolve`. Returns Matrix ID to handle. IDs without a handle are absent. */
    suspend fun resolveHandles(userIds: List<String>, accessToken: String): Map<String, String> =
        call { service.resolveHandles(bearer(accessToken), ResolveHandlesRequest(userIds)) }.handles

    /** `GET /v1/me/handle`. */
    suspend fun myHandle(accessToken: String): ProseioMyHandle = call { service.myHandle(bearer(accessToken)) }

    /** `PUT /v1/me/handle`. */
    suspend fun setMyHandle(handle: String, accessToken: String): ProseioMyHandle =
        call { service.setMyHandle(bearer(accessToken), SetHandleRequest(handle)) }

    companion object {
        /**
         * [baseUrl] is the API root, e.g. `https://api.proseio.com.br`.
         * [callFactory] must not log bodies or headers: requests carry passwords and access tokens.
         */
        fun create(callFactory: Call.Factory, json: Json, baseUrl: String): ProseioApiClient {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl.ensureTrailingSlash())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .callFactory(callFactory)
                .build()
            return ProseioApiClient(retrofit.create(ProseioApiService::class.java))
        }

        /**
         * Runs [request] with the token from [accessToken]. On HTTP 401 it reads the token again and retries once,
         * because Matrix access tokens are short-lived and the SDK may have refreshed it meanwhile.
         */
        suspend fun <T> withTokenRetry(accessToken: suspend () -> String?, request: suspend (String) -> T): T {
            val token = accessToken() ?: throw ProseioApiException.Server(ProseioApiErrorCode.UNAUTHORIZED, reason = null, statusCode = 401)
            return try {
                request(token)
            } catch (error: ProseioApiException) {
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
        return response.body() ?: throw ProseioApiException.UnexpectedResponse(statusCode = response.code())
    }

    /** Sends the request and maps transport failures and non-2xx answers to [ProseioApiException]. */
    private suspend fun <T> checked(block: suspend () -> Response<T>): Response<T> {
        val response = try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: java.io.IOException) {
            throw ProseioApiException.Network(error)
        } catch (error: Exception) {
            // Encoding and decoding failures: the answer is outside the contract.
            throw ProseioApiException.UnexpectedResponse(statusCode = null, cause = error)
        }

        if (!response.isSuccessful) {
            val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                ?.let { runCatching { errorJson.decodeFromString(ProseioErrorBody.serializer(), it) }.getOrNull() }
            if (errorBody != null) {
                throw ProseioApiException.Server(
                    code = ProseioApiErrorCode.fromId(errorBody.error) ?: ProseioApiErrorCode.INTERNAL,
                    reason = errorBody.reason,
                    statusCode = response.code(),
                )
            }
            throw ProseioApiException.UnexpectedResponse(statusCode = response.code())
        }
        return response
    }
}

/** Retrofit encodes each `@Path` value, so a handle cannot add path segments. */
internal interface ProseioApiService {
    @POST("v1/signup")
    suspend fun signup(@Body request: ProseioSignupRequest): Response<ProseioSignupResponse>

    @GET("v1/invites/{code}")
    suspend fun invitePreview(@Path("code") code: String): Response<ProseioInvitePreview>

    @GET("v1/me/invites")
    suspend fun myInvites(@Header("Authorization") authorization: String): Response<ProseioMyInvites>

    @POST("v1/me/invites")
    suspend fun createInvite(@Header("Authorization") authorization: String): Response<ProseioCreatedInvite>

    @DELETE("v1/me/invites/{inviteId}")
    suspend fun revokeInvite(@Header("Authorization") authorization: String, @Path("inviteId") inviteId: String): Response<Unit>

    @GET("v1/handles/{handle}/availability")
    suspend fun handleAvailability(@Path("handle") handle: String): Response<ProseioHandleAvailability>

    @GET("v1/handles/{handle}")
    suspend fun handle(@Header("Authorization") authorization: String, @Path("handle") handle: String): Response<ProseioHandleRecord>

    @POST("v1/handles/resolve")
    suspend fun resolveHandles(
        @Header("Authorization") authorization: String,
        @Body request: ResolveHandlesRequest,
    ): Response<ResolveHandlesResponse>

    @GET("v1/me/handle")
    suspend fun myHandle(@Header("Authorization") authorization: String): Response<ProseioMyHandle>

    @PUT("v1/me/handle")
    suspend fun setMyHandle(@Header("Authorization") authorization: String, @Body request: SetHandleRequest): Response<ProseioMyHandle>
}

/** The contract's `Error.error` enum. */
enum class ProseioApiErrorCode(val id: String) {
    BAD_REQUEST("bad_request"),
    INVITE_INVALID("invite_invalid"),
    INVITE_QUOTA_EXHAUSTED("invite_quota_exhausted"),
    EMAIL_TAKEN("email_taken"),
    HANDLE_INVALID("handle_invalid"),
    HANDLE_TAKEN("handle_taken"),
    HANDLE_COOLDOWN("handle_cooldown"),
    PASSWORD_WEAK("password_weak"),
    UNAUTHORIZED("unauthorized"),
    NOT_FOUND("not_found"),
    RATE_LIMITED("rate_limited"),
    INTERNAL("internal");

    companion object {
        fun fromId(id: String): ProseioApiErrorCode? = entries.firstOrNull { it.id == id }
    }
}

sealed class ProseioApiException(cause: Throwable? = null) : Exception(cause) {
    /** The server answered with the contract's error body. */
    data class Server(val code: ProseioApiErrorCode, val reason: String?, val statusCode: Int) : ProseioApiException()

    /** The request did not reach the server or got no answer. */
    class Network(cause: Throwable? = null) : ProseioApiException(cause)

    /** The server answered with something outside the contract. */
    class UnexpectedResponse(val statusCode: Int?, cause: Throwable? = null) : ProseioApiException(cause)

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
            ProseioApiErrorCode.INVITE_INVALID -> stringProvider.getString(R.string.proseio_error_invite_invalid)
            ProseioApiErrorCode.INVITE_QUOTA_EXHAUSTED -> stringProvider.getString(R.string.proseio_error_invite_quota)
            ProseioApiErrorCode.EMAIL_TAKEN -> stringProvider.getString(R.string.proseio_error_email_taken)
            ProseioApiErrorCode.PASSWORD_WEAK -> stringProvider.getString(R.string.proseio_error_password_weak)
            ProseioApiErrorCode.HANDLE_TAKEN -> ProseioHandleProblem.TAKEN.localizedMessage(stringProvider)
            ProseioApiErrorCode.HANDLE_INVALID ->
                (reason?.let(ProseioHandleProblem::fromId) ?: ProseioHandleProblem.INVALID_CHARACTERS).localizedMessage(stringProvider)
            ProseioApiErrorCode.HANDLE_COOLDOWN -> stringProvider.getString(R.string.proseio_error_handle_cooldown)
            ProseioApiErrorCode.RATE_LIMITED -> stringProvider.getString(R.string.proseio_error_rate_limited)
            ProseioApiErrorCode.UNAUTHORIZED -> stringProvider.getString(R.string.proseio_error_unauthorized)
            ProseioApiErrorCode.BAD_REQUEST, ProseioApiErrorCode.NOT_FOUND, ProseioApiErrorCode.INTERNAL ->
                stringProvider.getString(R.string.proseio_error_generic)
        }
        is Network -> stringProvider.getString(R.string.proseio_error_network)
        is UnexpectedResponse -> stringProvider.getString(R.string.proseio_error_generic)
    }
}
