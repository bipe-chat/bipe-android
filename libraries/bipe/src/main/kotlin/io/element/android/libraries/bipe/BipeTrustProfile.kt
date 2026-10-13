/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe

/**
 * The "Perfil de confiança" ladder from proseio/docs/trust-levels.md. Only the account owner sees it.
 * Step 2 is computed here from the Matrix profile and the handle; steps 3 to 5 wait for the API.
 * Port of BipeTrustProfile on iOS: same step and item ids.
 */
data class BipeTrustProfile(
    val steps: List<Step>,
    /** Empty when step 2 is done. */
    val missingProfileItems: List<ProfileItem>,
) {
    enum class StepId(val id: String) { ACCOUNT("account"), PROFILE("profile"), EMAIL("email"), PIX("pix"), GOVBR("govbr") }

    enum class Status(val id: String) { DONE("done"), TODO("todo"), COMING_SOON("coming_soon") }

    /** The parts of step 2, in the order the card shows them. */
    enum class ProfileItem(val id: String) { DISPLAY_NAME("display_name"), HANDLE("handle"), AVATAR("avatar") }

    data class Step(val id: StepId, val status: Status)

    val completedCount: Int get() = steps.count { it.status == Status.DONE }
    val totalCount: Int get() = steps.size
    val progress: Float get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount

    companion object {
        fun create(displayName: String?, hasAvatar: Boolean, handle: String?): BipeTrustProfile {
            val missing = buildList {
                if (displayName.isNullOrBlank()) add(ProfileItem.DISPLAY_NAME)
                if (handle.isNullOrEmpty()) add(ProfileItem.HANDLE)
                if (!hasAvatar) add(ProfileItem.AVATAR)
            }
            return BipeTrustProfile(
                steps = listOf(
                    Step(StepId.ACCOUNT, Status.DONE),
                    Step(StepId.PROFILE, if (missing.isEmpty()) Status.DONE else Status.TODO),
                    Step(StepId.EMAIL, Status.COMING_SOON),
                    Step(StepId.PIX, Status.COMING_SOON),
                    Step(StepId.GOVBR, Status.COMING_SOON),
                ),
                missingProfileItems = missing,
            )
        }
    }
}
