/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio

import io.element.android.libraries.proseio.api.ProseioApiClient
import io.element.android.libraries.proseio.api.ProseioApiException
import io.element.android.libraries.proseio.generated.ProseioRegistries
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Same ids as `handleProblem()` in proseio-shared/tools/lib/registries.mjs and the API's `HandleProblem`. */
enum class ProseioHandleProblem(val id: String) {
    TOO_SHORT("too_short"),
    TOO_LONG("too_long"),
    INVALID_CHARACTERS("invalid_characters"),
    RESERVED("reserved"),

    /** Also covers held and lookalike handles (server only). */
    TAKEN("taken");

    fun localizedMessage(stringProvider: StringProvider): String = when (this) {
        TOO_SHORT -> stringProvider.getString(R.string.proseio_handle_too_short, ProseioRegistries.HANDLE_MIN_LENGTH.toString())
        TOO_LONG -> stringProvider.getString(R.string.proseio_handle_too_long, ProseioRegistries.HANDLE_MAX_LENGTH.toString())
        INVALID_CHARACTERS -> stringProvider.getString(R.string.proseio_handle_invalid_characters)
        RESERVED -> stringProvider.getString(R.string.proseio_handle_reserved)
        TAKEN -> stringProvider.getString(R.string.proseio_handle_taken)
    }

    companion object {
        fun fromId(id: String): ProseioHandleProblem? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Client-side handle checks for early feedback. The server is the authority:
 * reserved names and lookalikes are checked there through the availability endpoint.
 */
object ProseioHandle {
    private val pattern = Regex(ProseioRegistries.HANDLE_PATTERN)

    /** Trims whitespace and a leading "@" the user may type. */
    fun sanitize(input: String): String = input.trim().removePrefix("@")

    /** Length and character checks only. `null` means the server must decide. */
    fun localProblem(handle: String): ProseioHandleProblem? {
        // JS `length` and Kotlin `length` both count UTF-16 units. The pattern is ASCII-only, so non-ASCII input fails it anyway.
        if (handle.length < ProseioRegistries.HANDLE_MIN_LENGTH) return ProseioHandleProblem.TOO_SHORT
        if (handle.length > ProseioRegistries.HANDLE_MAX_LENGTH) return ProseioHandleProblem.TOO_LONG
        if (!pattern.containsMatchIn(handle)) return ProseioHandleProblem.INVALID_CHARACTERS
        return null
    }
}

/** Client-side checks for the sign-up form. Limits come from the API contract (`SignupRequest`). */
object ProseioSignupRules {
    const val PASSWORD_MIN_LENGTH = 8
    const val PASSWORD_MAX_LENGTH = 256
    const val INVITE_CODE_MAX_LENGTH = 64
    const val EMAIL_MAX_LENGTH = 254
    const val DISPLAY_NAME_MAX_LENGTH = 100

    private val emailShape = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

    /** A loose shape check. MAS and the API validate the address for real. */
    fun isPlausibleEmail(email: String): Boolean =
        email.codePointCount(0, email.length) <= EMAIL_MAX_LENGTH && emailShape.containsMatchIn(email)

    fun isValidPassword(password: String): Boolean =
        password.codePointCount(0, password.length) in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH
}

/** Result of checking a handle the user typed. */
sealed interface ProseioHandleStatus {
    data object Empty : ProseioHandleStatus
    data object Checking : ProseioHandleStatus
    data object Available : ProseioHandleStatus
    data class Problem(val problem: ProseioHandleProblem) : ProseioHandleStatus

    /** The check failed (offline or server error). The server decides on submit. */
    data object Unknown : ProseioHandleStatus

    val isBlocking: Boolean get() = this is Problem

    /** Footer text for the handle field. [defaultText] shows when there is nothing to report. */
    fun footer(defaultText: String, stringProvider: StringProvider): String = when (this) {
        Empty, Unknown -> defaultText
        Checking -> stringProvider.getString(R.string.proseio_handle_checking)
        Available -> stringProvider.getString(R.string.proseio_handle_available)
        is Problem -> problem.localizedMessage(stringProvider)
    }
}

/** Debounced availability check: local rules at once, then the API after a pause in typing. */
class ProseioHandleAvailabilityChecker(
    private val apiClient: ProseioApiClient,
    private val scope: CoroutineScope,
    private val debounce: Duration = 500.milliseconds,
) {
    private var job: Job? = null

    /** Cancels any check in flight. [update] gets every status change for [input], in [scope]. */
    fun check(input: String, update: (ProseioHandleStatus) -> Unit) {
        job?.cancel()

        val handle = ProseioHandle.sanitize(input)
        if (handle.isEmpty()) {
            update(ProseioHandleStatus.Empty)
            return
        }
        ProseioHandle.localProblem(handle)?.let {
            update(ProseioHandleStatus.Problem(it))
            return
        }

        update(ProseioHandleStatus.Checking)
        job = scope.launch {
            delay(debounce)
            // Cancellation propagates out of handleAvailability, so a stale result never reaches update.
            val status = try {
                val availability = apiClient.handleAvailability(handle)
                if (availability.available) {
                    ProseioHandleStatus.Available
                } else {
                    ProseioHandleStatus.Problem(availability.problem ?: ProseioHandleProblem.TAKEN)
                }
            } catch (_: ProseioApiException) {
                ProseioHandleStatus.Unknown
            }
            update(status)
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
