/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import extension.setupDependencyInjection

// Bipe: Block Store for the recovery token (ADR 0057, ADR 0059). gplay flavor only (app/build.gradle.kts).
// The F-Droid build has no Google Play services: the token stays on the device (BipeRecoveryTokenStore).
plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.bipeblockstore"
}

setupDependencyInjection()

dependencies {
    implementation(libs.coroutines.core)
    implementation(projects.libraries.bipe)
    implementation(projects.libraries.di)
    implementation(libs.google.blockstore)
}
