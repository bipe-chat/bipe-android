/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio

import androidx.annotation.StringRes
import io.element.android.libraries.proseio.api.ProseioApiClient
import io.element.android.libraries.proseio.api.ProseioApiException
import io.element.android.libraries.proseio.api.ProseioMyProfile
import io.element.android.libraries.proseio.api.ProseioPublicProfile
import io.element.android.libraries.proseio.generated.ProseioRegistries
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/** Same ids as `profileProblem()` in proseio-shared/tools/lib/registries.mjs and the API's `ProfileProblem`. */
enum class ProseioProfileProblem(val id: String) {
    NAME_EMPTY("name_empty"),
    NAME_TOO_LONG("name_too_long"),
    NAME_RESERVED("name_reserved"),
    BIO_TOO_LONG("bio_too_long"),
    BIO_TOO_MANY_LINES("bio_too_many_lines"),

    /** Server only: a profile cannot be turned on without a handle. */
    NO_HANDLE("no_handle");

    val isNameProblem: Boolean get() = this == NAME_EMPTY || this == NAME_TOO_LONG || this == NAME_RESERVED
    val isBioProblem: Boolean get() = this == BIO_TOO_LONG || this == BIO_TOO_MANY_LINES

    @get:StringRes
    val messageRes: Int
        get() = when (this) {
            NAME_EMPTY -> R.string.proseio_public_profile_name_empty
            NAME_TOO_LONG -> R.string.proseio_public_profile_name_too_long
            NAME_RESERVED -> R.string.proseio_warning_reserved_name
            BIO_TOO_LONG -> R.string.proseio_public_profile_bio_too_long
            BIO_TOO_MANY_LINES -> R.string.proseio_public_profile_bio_too_many_lines
            NO_HANDLE -> R.string.proseio_public_profile_needs_handle
        }

    /** Format arguments for [messageRes]: the limit from registries/profiles.json. */
    val messageArgs: Array<Any>
        get() = when (this) {
            NAME_TOO_LONG -> arrayOf(ProseioRegistries.PROFILE_NAME_MAX_LENGTH.toString())
            BIO_TOO_LONG -> arrayOf(ProseioRegistries.PROFILE_BIO_MAX_LENGTH.toString())
            BIO_TOO_MANY_LINES -> arrayOf(ProseioRegistries.PROFILE_BIO_MAX_LINES.toString())
            else -> emptyArray()
        }

    fun localizedMessage(stringProvider: StringProvider): String = stringProvider.getString(messageRes, *messageArgs)

    companion object {
        fun fromId(id: String): ProseioProfileProblem? = entries.firstOrNull { it.id == id }
    }
}

/** Client-side profile checks for early feedback (ADR 0041). The server is the authority. */
object ProseioProfileRules {
    private val lineBreaks = Regex("\r\n?")

    /** Port of `profileProblem()` in proseio-shared/tools/lib/registries.mjs. Lengths count code points. */
    fun problem(name: String, bio: String): ProseioProfileProblem? {
        val trimmedName = NameNormalizer.trimJavaScript(name)
        if (trimmedName.isEmpty()) return ProseioProfileProblem.NAME_EMPTY
        if (trimmedName.codePointCount(0, trimmedName.length) > ProseioRegistries.PROFILE_NAME_MAX_LENGTH) return ProseioProfileProblem.NAME_TOO_LONG
        if (NameNormalizer.reservedCategory(trimmedName) != null) return ProseioProfileProblem.NAME_RESERVED
        val trimmedBio = NameNormalizer.trimJavaScript(bio)
        if (trimmedBio.codePointCount(0, trimmedBio.length) > ProseioRegistries.PROFILE_BIO_MAX_LENGTH) return ProseioProfileProblem.BIO_TOO_LONG
        if (trimmedBio.split("\n").size > ProseioRegistries.PROFILE_BIO_MAX_LINES) return ProseioProfileProblem.BIO_TOO_MANY_LINES
        return null
    }

    /**
     * The problem the server reports on `PUT /v1/me/profile`: it normalises line breaks first, and it accepts
     * an empty draft while the profile is off. A missing handle is checked by the caller.
     */
    fun saveProblem(enabled: Boolean, name: String, bio: String): ProseioProfileProblem? {
        val normalizedBio = bio.replace(lineBreaks, "\n")
        val isEmptyDraft = NameNormalizer.trimJavaScript(name).isEmpty() && NameNormalizer.trimJavaScript(normalizedBio).isEmpty()
        if (!enabled && isEmptyDraft) return null
        val problem = problem(name, normalizedBio) ?: return null
        return problem.takeIf { enabled || it != ProseioProfileProblem.NAME_EMPTY }
    }
}

/**
 * Public profiles for one user session: other people's by handle, and the user's own.
 *
 * Reads are synchronous like [ProseioHandleResolver]: [profile] returns `null` at first, fetches
 * `GET /v1/profiles/{handle}`, and [profiles] emits when the answer arrives.
 */
class ProseioPublicProfiles(
    private val apiClient: ProseioApiClient,
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val profile: ProseioPublicProfile?, val expiryMillis: Long)

    private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())

    /** Lowercase handle to published profile, `null` when the handle has none. Emits on every cache change. */
    val profiles: StateFlow<Map<String, ProseioPublicProfile?>> get() = profilesFlow.asStateFlow()
    private val profilesFlow = MutableStateFlow<Map<String, ProseioPublicProfile?>>(emptyMap())

    /** The user's own profile, `null` until loaded. */
    val myProfile: StateFlow<ProseioMyProfile?> get() = myProfileFlow.asStateFlow()
    private val myProfileFlow = MutableStateFlow<ProseioMyProfile?>(null)

    private val lock = Any()
    private val inFlight = mutableSetOf<String>()

    /** The cached published profile for [handle] (without "@"). Fetches it when the cache has no fresh value. */
    fun profile(handle: String): ProseioPublicProfile? {
        val key = handle.lowercase(Locale.ROOT)
        val entry = entries.value[key]
        if ((entry?.expiryMillis ?: Long.MIN_VALUE) < clock()) fetch(key)
        return entry?.profile
    }

    /** Loads the user's own profile into [myProfile]. Failures keep the old value. */
    suspend fun refreshMine() {
        try {
            storeMine(ProseioApiClient.withTokenRetry(accessToken) { token -> apiClient.myProfile(token) })
        } catch (error: ProseioApiException) {
            Timber.w(error, "Proseio profile load failed")
        }
    }

    /** Stores the user's own profile after a load or a save, and drops the cached public copy so it shows the change. */
    fun storeMine(profile: ProseioMyProfile) {
        myProfileFlow.value = profile
        profile.handle?.lowercase(Locale.ROOT)?.let { key ->
            entries.update { it - key }
            profilesFlow.update { it - key }
        }
    }

    private fun fetch(key: String) {
        synchronized(lock) {
            if (!inFlight.add(key)) return
        }
        scope.launch {
            try {
                set(key, Entry(apiClient.publicProfile(key), clock() + FOUND_TTL_MILLIS))
            } catch (error: ProseioApiException) {
                if (error is ProseioApiException.Server && error.statusCode == 404) {
                    set(key, Entry(null, clock() + MISSING_TTL_MILLIS))
                } else {
                    // Keep the old value, with a short expiry so views do not retry on every render while offline.
                    set(key, Entry(entries.value[key]?.profile, clock() + MISSING_TTL_MILLIS))
                }
            } finally {
                synchronized(lock) { inFlight.remove(key) }
            }
        }
    }

    private fun set(key: String, entry: Entry) {
        entries.update { it + (key to entry) }
        profilesFlow.update { it + (key to entry.profile) }
    }

    private companion object {
        val FOUND_TTL_MILLIS = 10.minutes.inWholeMilliseconds
        val MISSING_TTL_MILLIS = 2.minutes.inWholeMilliseconds
    }
}
