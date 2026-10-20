/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import extension.setupDependencyInjection

// Bipe: the Play Install Referrer for the deferred invite (bipe-chat/bipe#197). gplay flavor only (app/build.gradle.kts).
// The F-Droid build has no Play Store: the start screen offers the clipboard instead.
plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.bipeinstallreferrer"
}

setupDependencyInjection()

dependencies {
    implementation(libs.coroutines.core)
    implementation(projects.libraries.bipe)
    implementation(projects.libraries.di)
    implementation(libs.google.installreferrer)
}
