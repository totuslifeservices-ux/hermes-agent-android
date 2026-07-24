package com.nousresearch.hermes.agent.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TypingIndicator — Animated three-dot typing indicator.
 *
 * Shows three dots that fade in a staggered wave pattern
 * to indicate the agent is generating a response.
 * Uses Compose's built-in infiniteTransition with staggered,
 * individually-latched animations.
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    dotColor: Color = MaterialTheme.colorScheme.primary,
) {
    // Three separate transitions, each with a delay via different label
    // timing. Since infiniteRepeatable doesn't support initialStartOffset
    // in this version, we use staggered animation specs.
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )

    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )

    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TypingDot(alpha = alpha1, size = dotSize, color = dotColor)
        Spacer(modifier = Modifier.width(4.dp))
        TypingDot(alpha = alpha2, size = dotSize, color = dotColor)
        Spacer(modifier = Modifier.width(4.dp))
        TypingDot(alpha = alpha3, size = dotSize, color = dotColor)
    }
}

@Composable
private fun TypingDot(
    alpha: Float,
    size: Dp,
    color: Color,
) {
    Surface(
        modifier = Modifier
            .size(size)
            .alpha(alpha),
        shape = CircleShape,
        color = color,
    ) {}
}
