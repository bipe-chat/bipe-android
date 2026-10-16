/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.settings

import io.element.android.libraries.bipe.api.BipeAccountStatus

/**
 * The ways back into the account that this phone can see (ADR 0112, Sua conta; paths in bipe/docs/security.md).
 * Linked Apple and Google accounts (#307) and guardians (ADR 0044) are not built on Android, so they never count here.
 */
enum class BipeWayBack {
    /** A confirmed recovery email. A pending address does not count. */
    EMAIL,

    /** The Matrix recovery key is set up. It also brings old messages back (ADR 0073). */
    RECOVERY_KEY,

    /** The server has a recovery token and this phone keeps it in the synced store (Block Store, ADR 0059). */
    SYNCED_TOKEN,
    ;

    companion object {
        fun of(status: BipeAccountStatus, recoveryKeyEnabled: Boolean, syncedTokenPresent: Boolean): List<BipeWayBack> = buildList {
            if (status.email != null) add(EMAIL)
            if (recoveryKeyEnabled) add(RECOVERY_KEY)
            if (status.recoveryTokenSet && syncedTokenPresent) add(SYNCED_TOKEN)
        }
    }
}
