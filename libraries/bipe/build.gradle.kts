/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import extension.setupDependencyInjection
import extension.testCommonDependencies

// Bipe-specific code: the Android mirror of BipeKit on iOS.
// src/main/kotlin/.../generated and src/main/res/values*/bipe_strings.xml come from bipe-shared
// (node tools/gen.mjs). Do not edit them by hand.
plugins {
    id("io.element.android-compose-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.libraries.bipe"
}

setupDependencyInjection()

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.serialization.json)
    implementation(libs.google.tink)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.webkit) // Web sign-in: a WebView profile of its own (ADR 0118)
    implementation(libs.coil.compose)
    implementation(libs.zxing.cpp) // "Conferir no Bipe": QR codes in shared images
    implementation(projects.appconfig)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.core)
    implementation(projects.libraries.cryptography.api)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.di)
    implementation(projects.libraries.featureflag.api)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.mediapickers.api)
    implementation(projects.libraries.network)
    implementation(projects.libraries.qrcode)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.libraries.uiStrings)
    implementation(projects.services.toolbox.api)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(platform(libs.network.retrofit.bom))
    implementation(libs.network.retrofit)
    implementation(libs.network.retrofit.converter.serialization)

    testCommonDependencies(libs)
}
