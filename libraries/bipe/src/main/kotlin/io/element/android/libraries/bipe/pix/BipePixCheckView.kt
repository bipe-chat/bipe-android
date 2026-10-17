/*
 * Copyright 2026 Bipe.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.bipe.pix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.bipe.PixCheck
import io.element.android.libraries.bipe.R
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** "Conferir no Bipe" phase 1 (bipe ADR 0091): what a shared Pix code or key says, checked on the device. iOS: `BipePixCheckView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BipePixCheckView(
    check: PixCheck,
    onClose: () -> Unit,
    onSendToChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.bipe_pix_check_title),
                navigationIcon = { BackButton(onClick = onClose) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            check.code?.let { CodeSection(it) }
            if (check.keys.isNotEmpty()) {
                SectionTitle(stringResource(R.string.bipe_pix_check_keys_title))
                for (key in check.keys) Field(keyLabel(key.types.toImmutableList()), key.value)
            }
            check.warning?.let { warning ->
                Notice(
                    title = stringResource(R.string.bipe_warning_money_request_title),
                    body = stringResource(warning.messageRes),
                    caution = true,
                )
            }
            Notice(title = stringResource(R.string.bipe_pix_check_bank_name), body = null, caution = false)
            Text(
                text = stringResource(R.string.bipe_pix_check_private),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Button(
                text = stringResource(R.string.bipe_pix_check_send_to_chat),
                onClick = onSendToChat,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CodeSection(code: PixCheck.Code) {
    SectionTitle(stringResource(R.string.bipe_pix_check_code_title))
    when (code.status) {
        PixCheck.Status.MALFORMED -> Notice(title = stringResource(R.string.bipe_pix_check_code_malformed), body = null, caution = true)
        PixCheck.Status.BAD_CRC, PixCheck.Status.VALID -> {
            if (code.status == PixCheck.Status.BAD_CRC) {
                Notice(title = stringResource(R.string.bipe_pix_check_code_bad_crc), body = null, caution = true)
            }
            code.name?.let { Field(stringResource(R.string.bipe_pix_check_field_name), it, stringResource(R.string.bipe_pix_check_name_hint)) }
            Field(
                stringResource(R.string.bipe_pix_check_field_amount),
                code.amount?.let(::formattedAmount) ?: stringResource(R.string.bipe_pix_check_amount_open),
            )
            code.key?.let { Field(keyLabel(persistentListOf(code.keyType ?: PixCheck.KeyType.UNKNOWN)), it) }
            code.urlHost?.let { Field(stringResource(R.string.bipe_pix_check_field_charge_host), it, stringResource(R.string.bipe_pix_check_code_dynamic)) }
            code.city?.let { Field(stringResource(R.string.bipe_pix_check_field_city), it) }
            code.info?.let { Field(stringResource(R.string.bipe_pix_check_field_info), it) }
            code.txid?.let { Field(stringResource(R.string.bipe_pix_check_field_txid), it) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = ElementTheme.typography.fontBodyLgMedium, color = ElementTheme.colors.textPrimary)
}

@Composable
private fun Field(label: String, value: String, footnote: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
        SelectionContainer {
            Text(text = value, style = ElementTheme.typography.fontBodyLgMedium, color = ElementTheme.colors.textPrimary)
        }
        footnote?.let { Text(text = it, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary) }
    }
}

@Composable
private fun Notice(title: String, body: String?, caution: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    val background: Color = if (caution) ElementTheme.colors.bgTrustCautionSubtle else ElementTheme.colors.bgSubtleSecondary
    val border: Color = if (caution) ElementTheme.colors.borderTrustCaution else ElementTheme.colors.borderInteractiveSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(1.dp, border, shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (caution) CompoundIcons.Warning() else CompoundIcons.CheckCircle(),
            contentDescription = null,
            tint = if (caution) ElementTheme.colors.iconTrustCaution else ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = ElementTheme.typography.fontBodyMdMedium, color = ElementTheme.colors.textPrimary)
            body?.let { Text(text = it, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary) }
        }
    }
}

@Composable
private fun keyLabel(types: ImmutableList<PixCheck.KeyType>): String {
    val names = types.map { type ->
        stringResource(
            when (type) {
                PixCheck.KeyType.CPF -> R.string.bipe_pix_check_key_type_cpf
                PixCheck.KeyType.CNPJ -> R.string.bipe_pix_check_key_type_cnpj
                PixCheck.KeyType.PHONE -> R.string.bipe_pix_check_key_type_phone
                PixCheck.KeyType.EMAIL -> R.string.bipe_pix_check_key_type_email
                PixCheck.KeyType.RANDOM -> R.string.bipe_pix_check_key_type_random
                PixCheck.KeyType.UNKNOWN -> R.string.bipe_pix_check_key_type_unknown
            }
        )
    }
    val orFormat = stringResource(R.string.bipe_pix_check_key_types_or)
    return names.drop(1).fold(names.firstOrNull() ?: stringResource(R.string.bipe_pix_check_key_type_unknown)) { acc, name -> orFormat.format(acc, name) }
}

/** "50.00" → "R$ 50,00". The code holds a plain decimal with a dot. */
private fun formattedAmount(amount: String): String =
    amount.toBigDecimalOrNull()?.let { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(it) } ?: amount

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatchingExceptions { BigDecimal(this) }.getOrNull()

internal class PixCheckPreviewParam : PreviewParameterProvider<PixCheck> {
    override val values = sequenceOf(
        PixCheck.check(
            "URGENTE, paga agora: 00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000" +
                "5204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D"
        ),
        PixCheck.check("minha chave pix é 123.456.789-09 ou ana@example.com"),
        PixCheck.check("00020126580014br.gov.bcb.pix0136123e4567"),
    )
}

@PreviewsDayNight
@Composable
internal fun BipePixCheckViewPreview(@PreviewParameter(PixCheckPreviewParam::class) check: PixCheck) = ElementPreview {
    BipePixCheckView(check = check, onClose = {}, onSendToChat = {})
}
