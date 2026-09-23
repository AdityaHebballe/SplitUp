package com.aditya.splitup.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import com.aditya.splitup.domain.CurrencyUtils
import java.util.Locale

/**
 * An M3 Expressive true mechanical odometer ticker for monetary amounts.
 *
 * Architecture matching Google Pay / Google Wallet:
 * 1. Fixed-width Tabular Numerals (tnum): avoids horizontal jitter while digits roll.
 * 2. Slot Aperture Clipping: digits emerge from and exit into an aperture window (clipToBounds).
 * 3. Pure mechanical roll: no alpha fading; digits slide vertically like a cylindrical drum.
 * 4. Stable separators: decimal point and currency symbol are completely stationary.
 * 5. Directional: rolls upward when increasing, downward when decreasing.
 * 6. Supports [cycle] for tab-switch entrance roll.
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

    var prevAmount by remember { mutableDoubleStateOf(amount) }
    val directionUp = amount >= prevAmount
    SideEffect { prevAmount = amount }

    // Use tabular figures (tnum) so all digits have identical character width
    val tabularStyle = remember(style) {
        style.copy(fontFeatureSettings = "tnum")
    }

    val formattedString = remember(amount) { String.format(Locale.US, "%.2f", amount) }
    val dotIndex = formattedString.indexOf('.')
    val intPart = if (dotIndex >= 0) formattedString.substring(0, dotIndex) else formattedString
    val fracPart = if (dotIndex >= 0) formattedString.substring(dotIndex + 1) else ""

    val spatialSpec = motion.defaultSpatialSpec<IntOffset>()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Optional prefix like '+' or '-'
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                style = tabularStyle,
                color = color,
                fontWeight = fontWeight
            )
        }

        // Stable currency symbol
        if (showStyledSymbol) {
            Text(
                text = symbol,
                style = tabularStyle.copy(fontSize = tabularStyle.fontSize * 0.65f),
                color = if (symbolColor != Color.Unspecified) symbolColor else color.copy(alpha = 0.7f),
                fontWeight = FontWeight.Normal
            )
        } else {
            Text(
                text = symbol,
                style = tabularStyle,
                color = color,
                fontWeight = fontWeight
            )
        }

        // Integer digits (keyed by distance from the decimal point for structural stability)
        intPart.forEachIndexed { index, char ->
            val power = intPart.length - 1 - index
            key("int_$power") {
                AnimatedOdometerDigit(
                    char = char,
                    directionUp = directionUp,
                    cycle = cycle,
                    spatialSpec = spatialSpec,
                    style = tabularStyle,
                    color = color,
                    fontWeight = fontWeight
                )
            }
        }

        // Static decimal separator (strictly fixed in place)
        if (dotIndex >= 0) {
            Text(
                text = ".",
                style = tabularStyle,
                color = color,
                fontWeight = fontWeight
            )
        }

        // Fractional digits (tenths & hundredths)
        fracPart.forEachIndexed { index, char ->
            key("frac_$index") {
                AnimatedOdometerDigit(
                    char = char,
                    directionUp = directionUp,
                    cycle = cycle,
                    spatialSpec = spatialSpec,
                    style = tabularStyle,
                    color = color,
                    fontWeight = fontWeight
                )
            }
        }
    }
}

/**
 * A single mechanical odometer digit reel moving through a clipped aperture slot.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedOdometerDigit(
    char: Char,
    directionUp: Boolean,
    cycle: Int = 0,
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight?
) {
    Box(
        modifier = Modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = char to cycle,
            transitionSpec = {
                if (directionUp) {
                    slideInVertically(animationSpec = spatialSpec) { height -> height } togetherWith
                            slideOutVertically(animationSpec = spatialSpec) { height -> -height }
                } else {
                    slideInVertically(animationSpec = spatialSpec) { height -> -height } togetherWith
                            slideOutVertically(animationSpec = spatialSpec) { height -> height }
                }.using(SizeTransform(clip = true))
            },
            label = "OdometerDigit"
        ) { (targetChar, _) ->
            Text(
                text = targetChar.toString(),
                style = style,
                color = color,
                fontWeight = fontWeight
            )
        }
    }
}
