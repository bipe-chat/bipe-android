/*
 * Copyright 2026 Proseio.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import extension.testCommonDependencies

// Proseio-specific code: the Android mirror of ProseioKit on iOS.
// src/main/kotlin/.../generated and src/main/res/values*/proseio_strings.xml come from proseio-shared
// (node tools/gen.mjs). Do not edit them by hand.
plugins {
    id("io.element.android-compose-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.libraries.proseio"
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.google.tink)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.network)
    implementation(projects.services.toolbox.api)
    implementation(platform(libs.network.retrofit.bom))
    implementation(libs.network.retrofit)

    testCommonDependencies(libs)
}
