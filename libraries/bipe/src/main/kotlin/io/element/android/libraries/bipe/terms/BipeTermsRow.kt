/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.terms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.bipe.BipeLegalLinks
import io.element.android.libraries.bipe.R
import io.element.android.libraries.designsystem.theme.components.Checkbox
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Clickwrap (ADR 0113): the box and the sentence with the Terms of use and Privacy policy links.
 * The links open the pages; a tap anywhere else on the row toggles the box. Sign-up and the re-accept screen use it.
 */
@Composable
fun BipeTermsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val termsText = stringResource(R.string.bipe_signup_terms_link)
    val privacyText = stringResource(R.string.bipe_signup_privacy_link)
    val sentence = stringResource(R.string.bipe_signup_terms_accept, termsText, privacyText)
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Bold,
            color = ElementTheme.colors.textPrimary,
        ),
    )
    val text = buildAnnotatedString {
        append(sentence)
        listOf(termsText to BipeLegalLinks.termsUrl(), privacyText to BipeLegalLinks.privacyUrl()).forEach { (linkText, url) ->
            val start = sentence.indexOf(linkText)
            if (start >= 0) addLink(LinkAnnotation.Url(url = url, styles = linkStyles), start = start, end = start + linkText.length)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}
