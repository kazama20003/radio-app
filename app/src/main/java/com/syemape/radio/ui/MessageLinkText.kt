package com.syemape.radio.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.syemape.radio.ui.theme.Outfit

private val CHAT_URL_REGEX = Regex("(?i)(https?://\\S+|www\\.\\S+)")
private val TRAILING_URL_PUNCTUATION = ".,!?;:)]}»”’"

/** Muestra las URL del mensaje como enlaces que se abren fuera de la app. */
@Composable
fun MessageLinkText(
    text: String,
    color: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val annotated = buildAnnotatedString {
        append(text)
        CHAT_URL_REGEX.findAll(text).forEach { match ->
            val visibleUrl = match.value.trimEnd(*TRAILING_URL_PUNCTUATION.toCharArray())
            if (visibleUrl.isNotEmpty()) {
                val start = match.range.first
                val end = start + visibleUrl.length
                val target = if (visibleUrl.startsWith("www.", ignoreCase = true)) "https://$visibleUrl" else visibleUrl
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold),
                    start,
                    end,
                )
                addStringAnnotation("CHAT_URL", target, start, end)
            }
        }
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(color = color, fontFamily = Outfit, fontSize = 15.sp),
        onClick = { offset ->
            annotated.getStringAnnotations("CHAT_URL", offset, offset).firstOrNull()?.item?.let { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        },
    )
}
