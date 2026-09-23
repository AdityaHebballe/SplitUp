package com.aditya.splitup.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import com.aditya.splitup.domain.CurrencyUtils
import java.util.Locale

/**
 * An M3 Expressive per-digit odometer ticker for monetary amounts.
 *
 * Features:
 * 1. Independent per-digit columns: only changing digits slide vertically.
 * 2. Stable decimal point and currency symbol that never shift or flicker.
 * 3. Directional sliding: slides upward when amount increases, downward when it decreases.
 * 4. Tab-switch refresh via [cycle]: smoothly rolls digits into place when switching tabs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedAmount(
    amount: Double,
    currencyCode: String,
    modifier: Modifier = Modifier,
    prefix: String = "",
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    showStyledSymbol: Boolean = false,
    symbolColor: Color = Color.Unspecified,
    cycle: Int = 0
) {
    val symbol = remember(currencyCode) { CurrencyUtils.getSymbol(currencyCode) }
    val motion = MaterialTheme.motionScheme

    // Computed synchronously during composition (not inside a cancellable LaunchedEffect
    // coroutine) so two rapid amount changes can't have the first update's direction-write
    // cancelled before it applies, which used to make a digit occasionally slide the wrong way.
    var prevAmount by remember { mutableDoubleStateOf(amount) }
    val directionUp = amount >= prevAmount
    SideEffect { prevAmount = amount }

    val formattedString = remember(amount) { String.format(Locale.US, "%.2f", amount) }
    val dotIndex = formattedString.indexOf('.')
    val intPart = if (dotIndex >= 0) formattedString.substring(0, dotIndex) else formattedString
    val fracPart = if (dotIndex >= 0) formattedString.substring(dotIndex + 1) else ""

    val spatialSpec = motion.defaultSpatialSpec<IntOffset>()
    val effectsSpec = motion.defaultEffectsSpec<Float>()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Optional prefix like '+' or '-'
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                style = style,
                color = color,
                fontWeight = fontWeight
            )
        }

        // Stable currency symbol
        if (showStyledSymbol) {
            Text(
                text = symbol,
                style = style.copy(fontSize = style.fontSize * 0.65f),
                color = if (symbolColor != Color.Unspecified) symbolColor else color.copy(alpha = 0.7f),
                fontWeight = FontWeight.Normal
            )
        } else {
            Text(
                text = symbol,
                style = style,
                color = color,
                fontWeight = fontWeight
            )
        }

        // Integer digits (keyed by power from the decimal point for stability)
        intPart.forEachIndexed { index, char ->
            val power = intPart.length - 1 - index
            key("int_$power") {
                AnimatedDigit(
                    char = char,
                    directionUp = directionUp,
                    cycle = cycle,
                    spatialSpec = spatialSpec,
                    effectsSpec = effectsSpec,
                    style = style,
                    color = color,
                    fontWeight = fontWeight
                )
            }
        }

        // Static decimal separator (never moves or slides)
        if (dotIndex >= 0) {
            Text(
                text = ".",
                style = style,
                color = color,
                fontWeight = fontWeight
            )
        }

        // Fractional digits (tenths and hundredths)
        fracPart.forEachIndexed { index, char ->
            key("frac_$index") {
                AnimatedDigit(
                    char = char,
                    directionUp = directionUp,
                    cycle = cycle,
                    spatialSpec = spatialSpec,
                    effectsSpec = effectsSpec,
                    style = style,
                    color = color,
                    fontWeight = fontWeight
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedDigit(
    char: Char,
    directionUp: Boolean,
    cycle: Int,
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    effectsSpec: FiniteAnimationSpec<Float>,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight?
) {
    AnimatedContent(
        targetState = Pair(char, cycle),
        transitionSpec = {
            if (directionUp) {
                (slideInVertically(animationSpec = spatialSpec) { height -> height } +
                        fadeIn(animationSpec = effectsSpec)) togetherWith
                        (slideOutVertically(animationSpec = spatialSpec) { height -> -height } +
                                fadeOut(animationSpec = effectsSpec))
            } else {
                (slideInVertically(animationSpec = spatialSpec) { height -> -height } +
                        fadeIn(animationSpec = effectsSpec)) togetherWith
                        (slideOutVertically(animationSpec = spatialSpec) { height -> height } +
                                fadeOut(animationSpec = effectsSpec))
            }.using(SizeTransform(clip = false))
        },
        label = "DigitTicker"
    ) { (targetChar, _) ->
        Text(
            text = targetChar.toString(),
            style = style,
            color = color,
            fontWeight = fontWeight
        )
    }
}
