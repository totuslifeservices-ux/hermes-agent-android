package com.nousresearch.hermes.agent.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TwoWayRepeater
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TypingIndicator — Animated three-dot typing indicator.
 *
 * Shows three dots that fade and scale in a staggered wave pattern
 * to indicate the agent is generating a response.
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    dotColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    // Staggered delays for each dot (0, 150, 300 ms)
    val animSpec = tween<Float>(durationMillis = 800)

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = animSpec,
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = androidx.compose.animation.core.startOffset(0),
        ),
        label = "dot1",
    )

    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = animSpec,
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = androidx.compose.animation.core.startOffset(150),
        ),
        label = "dot2",
    )

    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = animSpec,
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = androidx.compose.animation.core.startOffset(300),
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
    color: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = Modifier
            .size(size)
            .alpha(alpha),
        shape = CircleShape,
        color = color,
    ) {}
}
