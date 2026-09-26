package com.kmmm_engineering.chargeclock.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.Text
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.data.ClockTheme

object ClockFonts {
    val Dseg7: FontFamily = FontFamily(Font(R.font.dseg7_classic_mini_bold_italic_file))
    val Dseg14: FontFamily = FontFamily(Font(R.font.dseg14_classic_mini_italic_file))

    /** Digits and ':' use 7-seg; everything else (letters, '/', '%', space, …) uses 14-seg. */
    fun isClassicSevenSegChar(ch: Char): Boolean = ch.isDigit() || ch == ':'

    fun classicAnnotated(text: String): AnnotatedString = buildAnnotatedString {
        text.forEach { ch ->
            val family = if (isClassicSevenSegChar(ch)) Dseg7 else Dseg14
            withStyle(SpanStyle(fontFamily = family)) {
                append(ch)
            }
        }
    }
}

/**
 * Clock-face text that applies [ClockTheme] fonts.
 * DEFAULT keeps system font + optional [fontWeight]; digital themes use DSEG
 * (Classic mixes 7/14 per character class).
 */
@Composable
fun ThemedClockText(
    text: String,
    theme: ClockTheme,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    val style = TextStyle(
        color = color,
        fontSize = fontSize,
        textAlign = textAlign ?: TextAlign.Unspecified,
        letterSpacing = letterSpacing,
    )
    when (theme) {
        ClockTheme.DEFAULT -> Text(
            text = text,
            modifier = modifier,
            style = style.copy(fontWeight = fontWeight),
            maxLines = maxLines,
            softWrap = softWrap,
        )
        ClockTheme.SEG14_DIGITAL -> Text(
            text = text,
            modifier = modifier,
            style = style.copy(fontFamily = ClockFonts.Dseg14),
            maxLines = maxLines,
            softWrap = softWrap,
        )
        ClockTheme.CLASSIC_DIGITAL -> Text(
            text = ClockFonts.classicAnnotated(text),
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            softWrap = softWrap,
        )
    }
}
