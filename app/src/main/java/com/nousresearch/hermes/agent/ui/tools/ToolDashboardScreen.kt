package com.nousresearch.hermes.agent.ui.tools

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nousresearch.hermes.agent.ui.components.EmptyState

/**
 * ToolDashboardScreen — Grid of available tools with enable/disable toggles.
 *
 * Tapping a tool opens a detail sheet showing description,
 * required permissions, and confirmation flag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDashboardScreen(
    viewModel: ToolDashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val tools by viewModel.tools.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedToolName by viewModel.selectedTool.collectAsState()
    var showDetailSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            tools.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.Extension,
                    title = "No tools registered",
                    subtitle = "Tools appear here when they are registered by the agent",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = tools,
                        key = { it.name },
                    ) { tool ->
                        ToolCard(
                            tool = tool,
                            onClick = {
                                viewModel.selectTool(tool.name)
                                showDetailSheet = true
                            },
                            onToggle = { viewModel.toggleTool(tool.name) },
                        )
                    }
                }
            }
        }
    }

    // ── Tool detail bottom sheet ────────────────────────────────────
    if (showDetailSheet && selectedToolName != null) {
        val detail = viewModel.getSelectedToolDetail()

        ModalBottomSheet(
            onDismissRequest = {
                showDetailSheet = false
                viewModel.selectTool(null)
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            if (detail != null) {
                ToolDetailSheet(tool = detail)
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: ToolDashboardViewModel.ToolUiState,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolIcon = getToolIcon(tool.name)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tool.isEnabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (tool.isEnabled)
                            Color(0xFF7CB8FF).copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = toolIcon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (tool.isEnabled)
                        Color(0xFF7CB8FF)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Name
            Text(
                text = tool.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                color = if (tool.isEnabled)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Toggle switch
            Switch(
                checked = tool.isEnabled,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun ToolDetailSheet(
    tool: ToolDashboardViewModel.ToolUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7CB8FF).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = getToolIcon(tool.name),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF7CB8FF),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (tool.isEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tool.isEnabled)
                        Color(0xFF4CAF50)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = "Description",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tool.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Requires Confirmation
        DetailRow(
            icon = Icons.Filled.Shield,
            label = "Requires Confirmation",
            value = if (tool.requiresConfirmation) "Yes" else "No",
            valueColor = if (tool.requiresConfirmation)
                Color(0xFFFFD93D)
            else
                Color(0xFF4CAF50),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Permissions
        if (tool.requiresPermissions.isNotEmpty()) {
            Text(
                text = "Required Permissions",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))

            tool.requiresPermissions.forEach { permission ->
                val granted = tool.permissionGranted[permission] ?: false
                DetailRow(
                    icon = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                    label = permission.substringAfterLast('.'),
                    value = if (granted) "Granted" else "Not Granted",
                    valueColor = if (granted) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                )
            }
        } else {
            DetailRow(
                icon = Icons.Filled.CheckCircle,
                label = "Permissions",
                value = "None required",
                valueColor = Color(0xFF4CAF50),
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = valueColor,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
        )
    }
}

/**
 * Map tool names to appropriate icons.
 */
private fun getToolIcon(toolName: String): ImageVector {
    return when {
        toolName.contains("sms", ignoreCase = true) ||
            toolName.contains("message", ignoreCase = true) ||
            toolName.contains("send", ignoreCase = true) -> Icons.Filled.Person

        toolName.contains("location", ignoreCase = true) ||
            toolName.contains("gps", ignoreCase = true) -> Icons.Filled.Sensors

        toolName.contains("camera", ignoreCase = true) ||
            toolName.contains("photo", ignoreCase = true) -> Icons.Filled.Settings

        toolName.contains("contact", ignoreCase = true) -> Icons.Filled.Person
        toolName.contains("calendar", ignoreCase = true) -> Icons.Filled.Settings
        toolName.contains("search", ignoreCase = true) ||
            toolName.contains("web", ignoreCase = true) -> Icons.Filled.Sensors

        toolName.contains("file", ignoreCase = true) ||
            toolName.contains("storage", ignoreCase = true) -> Icons.Filled.Settings

        toolName.contains("shell", ignoreCase = true) ||
            toolName.contains("terminal", ignoreCase = true) ||
            toolName.contains("exec", ignoreCase = true) -> Icons.Filled.Build

        toolName.contains("notification", ignoreCase = true) -> Icons.Filled.Warning

        toolName.contains("clipboard", ignoreCase = true) -> Icons.Filled.Settings

        else -> Icons.Filled.Extension
    }
}
