package com.nousresearch.hermes.agent.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nousresearch.hermes.agent.core.ProviderType

/**
 * ModelBadge — Small chip showing current model name with provider color.
 *
 * Provides visual identification of which model/provider is active.
 */
@Composable
fun ModelBadge(
    modelName: String,
    provider: ProviderType? = null,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor) = providerColors(provider)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Provider dot
            Surface(
                modifier = Modifier.size(6.dp),
                shape = RoundedCornerShape(3.dp),
                color = textColor,
            ) {}
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun providerColors(provider: ProviderType?): Pair<Color, Color> {
    return when (provider) {
        ProviderType.NousPortal -> Color(0xFF7CB8FF) to Color(0xFF003258)
        ProviderType.OpenRouter -> Color(0xFFFF6B6B) to Color(0xFF3A0000)
        ProviderType.Ollama -> Color(0xFF4CAF50) to Color(0xFF003300)
        ProviderType.Custom -> Color(0xFFB2C9E1) to Color(0xFF1C3146)
        null -> Color(0xFF3A4047) to Color(0xFFE1E2E8)
    }
}
