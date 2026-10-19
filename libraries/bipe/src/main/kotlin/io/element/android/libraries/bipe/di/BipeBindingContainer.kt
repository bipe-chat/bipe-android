/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.BipeConfig
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.bipe.api.BipeApiClient
import io.element.android.libraries.bipe.weblink.BipeMatrixAccountApi
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@BindingContainer
@ContributesTo(AppScope::class)
object BipeBindingContainer {
    /**
     * Own OkHttp client, without Element's DynamicHttpLoggingInterceptor: that one logs full bodies at debug
     * log level, and Bipe requests carry passwords (sign-up) and Matrix access tokens.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providesBipeApiClient(
        userAgentInterceptor: UserAgentInterceptor,
        jsonProvider: JsonProvider,
    ): BipeApiClient {
        val okHttpClient = bipeOkHttpClient(userAgentInterceptor)
        return BipeApiClient.create(
            callFactory = { request -> okHttpClient.newCall(request) },
            json = jsonProvider(),
            baseUrl = BipeConfig.API_BASE_URL,
        )
    }

    /** The Matrix and MAS calls of the web sign-in (ADR 0118). Same client rules: no body or header logs. */
    @Provides
    @SingleIn(AppScope::class)
    fun providesBipeMatrixAccountApi(userAgentInterceptor: UserAgentInterceptor): BipeMatrixAccountApi {
        val okHttpClient = bipeOkHttpClient(userAgentInterceptor)
        return BipeMatrixAccountApi(callFactory = { request -> okHttpClient.newCall(request) })
    }

    private fun bipeOkHttpClient(userAgentInterceptor: UserAgentInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(userAgentInterceptor)
        .build()
}
