package com.nousresearch.hermes.agent.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * SplashScreen — Launch screen for Hermes Agent.
 *
 * Shows the logo, version, and initialization progress.
 * Transitions to main content once initialization is complete.
 *
 * @param onInitialized Called when initialization finishes
 */
@Composable
fun SplashScreen(
    onInitialized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPhase by remember { mutableStateOf(0) }
    val phases = listOf(
        "Loading Policy" to 0.25f,
        "Initializing Engine" to 0.50f,
        "Starting Gateway" to 0.75f,
        "Ready" to 1.0f,
    )
    var progress by remember { mutableFloatStateOf(0f) }
    var phaseText by remember { mutableStateOf("Starting up \u2026") }

    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "splashAlpha",
    )

    // Simulate initialization phases
    LaunchedEffect(Unit) {
        for ((i, phase) in phases.withIndex()) {
            currentPhase = i
            phaseText = phase.first
            val targetProgress = phase.second

            // Animate progress to target
            val startProgress = if (i == 0) 0f else phases[i - 1].second
            val steps = 20
            for (step in 1..steps) {
                progress = startProgress + (targetProgress - startProgress) * (step.toFloat() / steps)
                delay(25)
            }

            if (i < phases.size - 1) {
                delay(100) // Brief pause between phases
            }
        }
        delay(300) // Brief hold on "Ready"
        onInitialized()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Logo area ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF7CB8FF).copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "H",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 56.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = Color(0xFF7CB8FF),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── App name ────────────────────────────────────────────
            Text(
                text = "Hermes Agent",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
                color = Color(0xFF7CB8FF),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Version ─────────────────────────────────────────────
            Text(
                text = "Version 0.18.2",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Tagline ─────────────────────────────────────────────
            Text(
                text = "Colossians 3:23 NLT",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Progress ────────────────────────────────────────────
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(3.dp),
                color = Color(0xFF7CB8FF),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = phaseText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
