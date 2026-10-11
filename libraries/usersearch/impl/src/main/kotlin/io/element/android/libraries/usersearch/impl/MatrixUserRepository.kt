/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.usersearch.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.proseio.ProseioHandle
import io.element.android.libraries.proseio.api.ProseioApiException
import io.element.android.libraries.proseio.di.ProseioSession
import io.element.android.libraries.usersearch.api.UserListDataSource
import io.element.android.libraries.usersearch.api.UserRepository
import io.element.android.libraries.usersearch.api.UserSearchResult
import io.element.android.libraries.usersearch.api.UserSearchResultState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.util.Locale

@ContributesBinding(SessionScope::class)
class MatrixUserRepository(
    private val client: MatrixClient,
    private val dataSource: UserListDataSource,
    private val proseioSession: ProseioSession,
) : UserRepository {
    override fun search(query: String): Flow<UserSearchResultState> = flow {
        val shouldQueryProfile = MatrixPatterns.isUserId(query) && !client.isMe(UserId(query))
        val shouldFetchSearchResults = query.length >= MINIMUM_SEARCH_LENGTH
        // If the search term is a MXID that's not ours, we'll show a 'fake' result for that user, then update it when we get search results.
        val fakeSearchResult = if (shouldQueryProfile) {
            UserSearchResult(MatrixUser(UserId(query)))
        } else {
            null
        }
        if (shouldQueryProfile || shouldFetchSearchResults) {
            emit(UserSearchResultState(isSearching = shouldFetchSearchResults, results = listOfNotNull(fakeSearchResult)))
        }
        if (shouldFetchSearchResults) {
            val results = fetchSearchResults(query, shouldQueryProfile)
            emit(results)
        }
    }

    private suspend fun fetchSearchResults(query: String, shouldQueryProfile: Boolean): UserSearchResultState = coroutineScope {
        // Debounce
        delay(DEBOUNCE_TIME_MILLIS)
        // Proseio: exact @handle lookup next to the directory search. The match goes first.
        val proseioMatch = async { proseioLookUp(query) }
        val directoryResults = dataSource
            .search(query, MAXIMUM_SEARCH_RESULTS)
            .filter { !client.isMe(it.userId) }
            .map { UserSearchResult(it) }
        val handleUser = proseioMatch.await()
        val results = (listOfNotNull(handleUser?.let { UserSearchResult(it) }) + directoryResults.filter { it.matrixUser.userId != handleUser?.userId })
            .toMutableList()

        // If the query is another user's MXID and the result doesn't contain that user ID, query the profile information explicitly
        if (shouldQueryProfile && results.none { it.matrixUser.userId.value == query }) {
            results.add(
                0,
                dataSource.getProfile(UserId(query))
                    ?.let { UserSearchResult(it) }
                    ?: UserSearchResult(MatrixUser(UserId(query)), isUnresolved = true)
            )
        }

        UserSearchResultState(results = results, isSearching = false)
    }

    /** Proseio: the user who holds the handle in [query], or null (not a handle, nobody, the user's own, or a failed lookup). */
    private suspend fun proseioLookUp(query: String): MatrixUser? {
        val handle = ProseioHandle.sanitize(query).lowercase(Locale.ROOT)
        if (ProseioHandle.localProblem(handle) != null) return null
        val userId = try {
            proseioSession.handleResolver.userId(forHandle = handle)
        } catch (error: ProseioApiException) {
            Timber.e(error, "Proseio handle lookup failed")
            null
        } ?: return null
        if (client.isMe(UserId(userId))) return null
        return dataSource.getProfile(UserId(userId)) ?: MatrixUser(UserId(userId))
    }

    companion object {
        private const val DEBOUNCE_TIME_MILLIS = 250L
        private const val MINIMUM_SEARCH_LENGTH = 3
        private const val MAXIMUM_SEARCH_RESULTS = 10L
    }
}
