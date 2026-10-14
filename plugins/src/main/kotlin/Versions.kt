/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Bipe (ADR 0088): the version name and the build number come from bipe-shared/product/version.json,
 * through the generated BipeVersion.kt. iOS uses the same build number. Element's CalVer version code
 * and its release script are not used.
 *
 * Max versionCode allowed by the PlayStore: 2_100_000_000.
 * The versionCode is multiplied by 10 in app/build.gradle.kts:
 * ```
 * output.versionCode.set((output.versionCode.orNull ?: 0) * 10 + abiCode)
 * ```
 * so build 7 gives 70 for an AAB and 7a for an APK, where `a` stands for the architecture code.
 */
object Versions {
    /**
     * Base version code that will be set in the Android Manifest: the shared build number.
     * The value will be modified at build time to add the ABI code when APK are build.
     * AAB will have a ABI code of 0.
     */
    const val VERSION_CODE = BipeVersion.BUILD
    val VERSION_NAME = BipeVersion.NAME

    /**
     * Compile SDK version. Must be updated when a new Android version is released.
     * When updating COMPILE_SDK, please also update BUILD_TOOLS_VERSION.
     */
    const val COMPILE_SDK = 37

    /**
     * Build tools version. Must be kept in sync with COMPILE_SDK.
     * The value is used by the release script.
     */
    @Suppress("unused")
    private const val BUILD_TOOLS_VERSION = "37.0.0"

    /**
     * Target SDK version. Should be kept up to date with COMPILE_SDK.
     */
    const val TARGET_SDK = 37

    /**
     * Minimum SDK version for FOSS builds.
     */
    private const val MIN_SDK_FOSS = 24

    /**
     * Minimum SDK version for Enterprise builds.
     */
    private const val MIN_SDK_ENTERPRISE = 33

    /**
     * minSdkVersion that will be set in the Android Manifest.
     */
    val minSdk = if (isEnterpriseBuild) MIN_SDK_ENTERPRISE else MIN_SDK_FOSS

    /**
     * Java version used for compilation.
     * Update this value when you want to use a newer Java version.
     */
    private const val JAVA_VERSION = 21

    val javaVersion: JavaVersion = JavaVersion.toVersion(JAVA_VERSION)
    val javaLanguageVersion: JavaLanguageVersion = JavaLanguageVersion.of(JAVA_VERSION)

    // Perform some checks on the values to avoid releasing with bad values
    init {
        // Bipe: the Play Store maximum is 2_100_000_000, and app/build.gradle.kts multiplies the code by 10.
        require(VERSION_CODE in 1..210_000_000) { "VERSION_CODE must be in [1,210000000]" }
        require(BUILD_TOOLS_VERSION.startsWith(COMPILE_SDK.toString())) { "When updating COMPILE_SDK, please also update BUILD_TOOLS_VERSION" }
    }
}
