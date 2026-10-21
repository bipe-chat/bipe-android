/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.account

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InviterToRememberTest {
    @Test
    fun `an invite from another person keeps its inviter`() {
        assertThat(inviterToRemember("@ana:bipe.net.br", setOf("@bia:bipe.net.br"))).isEqualTo("@ana:bipe.net.br")
    }

    @Test
    fun `an invite from an account signed in on this phone keeps no inviter`() {
        assertThat(inviterToRemember("@ana:bipe.net.br", setOf("@ana:bipe.net.br", "@bia:bipe.net.br"))).isNull()
    }

    @Test
    fun `a sign-up without an inviter keeps none`() {
        assertThat(inviterToRemember(null, emptySet())).isNull()
    }
}
