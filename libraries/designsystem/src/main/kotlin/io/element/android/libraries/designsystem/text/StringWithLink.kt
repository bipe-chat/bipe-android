/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun stringWithLink(
    @StringRes textRes: Int,
    url: String?,
    onLinkClick: (String) -> Unit,
    @StringRes linkTextRes: Int = CommonStrings.action_learn_more,
) = buildAnnotatedString {
    // Bipe: without a help page, drop the link text and keep the sentence (bipe#205).
    if (url == null) {
        append(stringWithoutLink(stringResource(textRes, BIPE_LINK_PLACEHOLDER)))
        return@buildAnnotatedString
    }
    val learnMoreStr = stringResource(linkTextRes)
    val fullText = stringResource(textRes, learnMoreStr)
    append(fullText)
    val learnMoreStartIndex = fullText.lastIndexOf(learnMoreStr)
    addStyle(
        style = SpanStyle(
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Bold,
            color = ElementTheme.colors.textPrimary
        ),
        start = learnMoreStartIndex,
        end = learnMoreStartIndex + learnMoreStr.length,
    )
    addLink(
        url = LinkAnnotation.Url(
            url = url,
            linkInteractionListener = {
                onLinkClick(url)
            }
        ),
        start = learnMoreStartIndex,
        end = learnMoreStartIndex + learnMoreStr.length,
    )
}

// Bipe: stands in for the link text when there is no help page (bipe#205).
const val BIPE_LINK_PLACEHOLDER = "\u0000"

/**
 * Bipe: removes [BIPE_LINK_PLACEHOLDER] and the spaces before it, and a "." after it when the sentence already
 * ended before the link ("… identity. Learn more." must not become "… identity. ."). Same rule as iOS
 * `AttributedString.bipeReplaceLink`.
 */
fun stringWithoutLink(text: String): String {
    val index = text.indexOf(BIPE_LINK_PLACEHOLDER)
    if (index < 0) return text
    var start = index
    while (start > 0 && text[start - 1].isWhitespace()) start--
    var end = index + BIPE_LINK_PLACEHOLDER.length
    if (end < text.length && text[end] == '.' && start > 0 && text[start - 1] == '.') end++
    return text.removeRange(start, end)
}
