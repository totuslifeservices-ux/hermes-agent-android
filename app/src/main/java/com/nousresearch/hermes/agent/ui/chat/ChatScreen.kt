package com.nousresearch.hermes.agent.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nousresearch.hermes.agent.core.MessageRole
import com.nousresearch.hermes.agent.ui.components.EmptyState
import com.nousresearch.hermes.agent.ui.components.ErrorBanner
import com.nousresearch.hermes.agent.ui.components.MarkdownText
import com.nousresearch.hermes.agent.ui.components.ToolCallCard
import com.nousresearch.hermes.agent.ui.components.TypingIndicator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ChatScreen — Main chat composable with streaming messages and composer.
 *
 * Features:
 * - Message list with auto-scroll and animateItemPlacement
 * - Role badges (User/Hermes/Tool) with styled content
 * - Streaming assistant message bubbles
 * - Tool call cards (expandable)
 * - Composer with send + mic buttons
 * - Smart scroll behavior
 * - Swipe-to-delete
 * - Pull-to-refresh (re-generate)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val error by viewModel.error.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val currentToolCalls by viewModel.currentToolCalls.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Auto-scroll to bottom on new messages (unless user scrolled up)
    val shouldAutoScroll by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem == null ||
                lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(messages.size, streamingContent) {
        if (shouldAutoScroll && messages.isNotEmpty()) {
            listState.animateScrollToItem(
                index = messages.size +
                    if (streamingContent != null || currentToolCalls.isNotEmpty()) 1 else 0 - 1,
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hermes Agent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (sessionId != null) {
                        TextButton(onClick = onNewSession) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "New Session",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            ChatComposer(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                onMicClick = {
                    // TODO: Voice input integration
                },
                isStreaming = isStreaming,
                onCancelStream = { viewModel.cancelStream() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Error banner
            ErrorBanner(
                message = error ?: "",
                visible = error != null,
                onDismiss = { viewModel.clearError() },
            )

            // Message list or empty state
            if (messages.isEmpty() && !isStreaming) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = "Start a conversation",
                        subtitle = "Send a message to begin chatting with Hermes Agent",
                    )
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isStreaming,
                    onRefresh = { viewModel.regenerateLastResponse() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Regular messages
                        items(
                            items = messages,
                            key = { it.id },
                        ) { message ->
                            SwipeableMessage(
                                message = message,
                                onDelete = { viewModel.deleteMessage(message.id) },
                            ) {
                                MessageBubble(message = message)
                            }
                        }

                        // Streaming content
                        if (streamingContent != null || currentToolCalls.isNotEmpty()) {
                            item(key = "streaming") {
                                StreamingMessage(
                                    content = streamingContent,
                                    toolCalls = currentToolCalls,
                                    isStreaming = isStreaming,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Message bubble — Renders a single chat message with role styling.
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.User
    val isAssistant = message.role == MessageRole.Assistant
    val isTool = message.role == MessageRole.Tool

    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        isAssistant -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isTool -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalAlignment = alignment,
    ) {
        // Role badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = when {
                    isUser -> Icons.Outlined.Person
                    isTool -> Icons.Outlined.SmartToy
                    else -> Icons.Outlined.SmartToy
                },
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    isAssistant -> Color(0xFF7CB8FF)
                    isTool -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = when {
                    isUser -> "You"
                    isTool -> "Tool"
                    else -> "Hermes"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    isAssistant -> Color(0xFF7CB8FF)
                    isTool -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // Content bubble
        Surface(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 1f),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp,
            ),
            color = bubbleColor,
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (message.content != null) {
                    MarkdownText(
                        text = message.content,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (message.toolCallsSummary != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.toolCallsSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Timestamp
        Text(
            text = formatTimestamp(message.timestamp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/**
 * Streaming message — Shows in-progress assistant response.
 */
@Composable
private fun StreamingMessage(
    content: String?,
    toolCalls: List<ToolCallState>,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Role badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color(0xFF7CB8FF),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Hermes",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color(0xFF7CB8FF),
            )
        }

        // Streaming content
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = 4.dp,
                bottomEnd = 12.dp,
            ),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (!content.isNullOrBlank()) {
                    MarkdownText(
                        text = content,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (toolCalls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    toolCalls.forEach { toolCall ->
                        ToolCallCard(
                            toolName = toolCall.toolName,
                            arguments = toolCall.arguments,
                            result = toolCall.result,
                            isError = toolCall.isError,
                            isRunning = toolCall.isRunning,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                if (content.isNullOrBlank() && toolCalls.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(4.dp),
                    ) {
                        Text(
                            text = "Thinking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TypingIndicator(
                            dotSize = 6.dp,
                            dotColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (isStreaming && content != null) {
                    TypingIndicator(
                        dotSize = 6.dp,
                        dotColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * ChatComposer — Bottom input bar with text field, send, mic, and cancel buttons.
 */
@Composable
private fun ChatComposer(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    isStreaming: Boolean,
    onCancelStream: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cancel / Stop button when streaming
            if (isStreaming) {
                IconButton(
                    onClick = onCancelStream,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Text input
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .height(
                        with(LocalDensity.current) {
                            (48 * LocalConfiguration.current.fontScale).toDp()
                        }
                    ),
                placeholder = {
                    Text(
                        "Message Hermes \u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isStreaming) onSend() }),
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Mic or Send button
            if (inputText.isBlank() && !isStreaming) {
                IconButton(
                    onClick = onMicClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice Input",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = inputText.isNotBlank() && !isStreaming,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (inputText.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank())
                            MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

/**
 * SwipeableMessage — Wrapper that enables swipe-to-delete on messages.
 */
@Composable
private fun SwipeableMessage(
    message: ChatMessage,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val threshold = -150f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < threshold) {
                            onDelete()
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(-200f, 0f)
                    },
                )
            },
    ) {
        // Delete hint background
        AnimatedVisibility(
            visible = offsetX < -30f,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp),
            )
        }

        // Content with offset
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .animateContentSize(),
        ) {
            content()
        }
    }
}

// ── Utility ────────────────────────────────────────────────────────

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}
