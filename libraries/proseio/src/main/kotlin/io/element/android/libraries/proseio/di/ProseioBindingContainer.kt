/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.proseio.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.ProseioConfig
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import io.element.android.libraries.proseio.api.ProseioApiClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@BindingContainer
@ContributesTo(AppScope::class)
object ProseioBindingContainer {
    /**
     * Own OkHttp client, without Element's DynamicHttpLoggingInterceptor: that one logs full bodies at debug
     * log level, and Proseio requests carry passwords (sign-up) and Matrix access tokens.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providesProseioApiClient(
        userAgentInterceptor: UserAgentInterceptor,
        jsonProvider: JsonProvider,
    ): ProseioApiClient {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .build()
        return ProseioApiClient.create(
            callFactory = { request -> okHttpClient.newCall(request) },
            json = jsonProvider(),
            baseUrl = ProseioConfig.API_BASE_URL,
        )
    }
}
