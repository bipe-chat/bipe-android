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
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import io.element.android.libraries.bipe.api.BipeApiClient
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
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .build()
        return BipeApiClient.create(
            callFactory = { request -> okHttpClient.newCall(request) },
            json = jsonProvider(),
            baseUrl = BipeConfig.API_BASE_URL,
        )
    }
}
