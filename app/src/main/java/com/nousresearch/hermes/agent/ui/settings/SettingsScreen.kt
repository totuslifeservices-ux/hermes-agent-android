package com.nousresearch.hermes.agent.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.agent.core.ProviderType

/**
 * SettingsScreen — Settings page for model/provider selection, API key,
 * temperature, max tokens, theme, text size, and about section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val provider by viewModel.provider.collectAsState()
    val model by viewModel.model.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val textSize by viewModel.textSize.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Model & Provider ────────────────────────────────────
            SettingsSection(title = "Model & Provider") {
                // Provider picker
                ProviderPicker(
                    selectedProvider = provider,
                    onProviderSelected = { viewModel.setProvider(it) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Model name
                OutlinedTextField(
                    value = model,
                    onValueChange = { viewModel.setModel(it) },
                    label = { Text("Model") },
                    placeholder = { Text("e.g. claude-sonnet-4, qwen3:14b") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
            }

            // ── API Key ─────────────────────────────────────────────
            if (provider == ProviderType.OpenRouter) {
                SettingsSection(title = "API Key") {
                    var showKey by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.setApiKey(it) },
                        label = { Text("OpenRouter API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        visualTransformation = if (showKey)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey)
                                        Icons.Filled.VisibilityOff
                                    else
                                        Icons.Filled.Visibility,
                                    contentDescription = if (showKey)
                                        "Hide API key"
                                    else
                                        "Show API key",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }

            // ── Generation Parameters ───────────────────────────────
            SettingsSection(title = "Generation") {
                // Temperature
                Text(
                    text = "Temperature: ${String.format("%.2f", temperature)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Slider(
                    value = temperature,
                    onValueChange = { viewModel.setTemperature(it) },
                    valueRange = 0.0f..2.0f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Max tokens
                Text(
                    text = "Max Tokens: $maxTokens",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { viewModel.setMaxTokens(it.toInt()) },
                    valueRange = 1024f..131072f,
                    steps = 126,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Appearance ──────────────────────────────────────────
            SettingsSection(title = "Appearance") {
                // Theme toggle
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("system", "dark", "light").forEach { mode ->
                        OutlinedButton(
                            onClick = { viewModel.setThemeMode(mode) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = when (mode) {
                                    "system" -> "System"
                                    "dark" -> "Dark"
                                    "light" -> "Light"
                                    else -> mode
                                },
                                fontWeight = if (themeMode == mode)
                                    FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Text size
                Text(
                    text = "Text Size: ${(textSize * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Slider(
                    value = textSize,
                    onValueChange = { viewModel.setTextSize(it) },
                    valueRange = 0.7f..1.5f,
                    steps = 7,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Actions ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.applySettings() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Apply Settings")
                }

                OutlinedButton(
                    onClick = { viewModel.resetToDefaults() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Reset")
                }
            }

            // ── About Section ───────────────────────────────────────
            SettingsSection(title = "About") {
                AboutSection()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProviderPicker(
    selectedProvider: ProviderType,
    onProviderSelected: (ProviderType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val providerLabels = mapOf(
        ProviderType.NousPortal to "Nous Portal",
        ProviderType.OpenRouter to "OpenRouter",
        ProviderType.Ollama to "Ollama (Local)",
        ProviderType.Custom to "Custom",
    )

    Column {
        Text(
            text = "Provider",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = providerLabels[selectedProvider] ?: selectedProvider.name,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Expand",
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            providerLabels.forEach { (provider, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (provider == selectedProvider)
                                FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onProviderSelected(provider)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AboutSection() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Hermes Agent",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Version 0.18.2",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "by Nous Research",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "\"Work willingly at whatever you do, as though you were working for the Lord rather than for people.\"  \u2014 Colossians 3:23 NLT",
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
