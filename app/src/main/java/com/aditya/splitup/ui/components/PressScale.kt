package com.aditya.splitup.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * Applies the app's standard Expressive press-down feedback (scale to [targetScale])
 * to a clickable card/row, driven by the given [interactionSource].
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.96f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "PressScale"
    )
    return this.scale(scale)
}

/** Remembers a [MutableInteractionSource] for use with [pressScale] and a clickable modifier. */
@Composable
fun rememberPressInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }
