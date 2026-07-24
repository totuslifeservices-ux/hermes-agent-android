package com.nousresearch.hermes.agent.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MarkdownText — Renders simplified Markdown as an AnnotatedString.
 *
 * Supports: **bold**, *italic*, `inline code`, ```code blocks```,
 * [links](url), and numbered/bullet lists.
 *
 * For full Markdown rendering, this can be replaced with a library
 * like Markwon or compose-richtext in production.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 14.sp,
    maxLines: Int = Int.MAX_VALUE,
) {
    val annotatedString = remember(text, color) {
        renderMarkdown(text, color)
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        maxLines = maxLines,
        onClick = { offset ->
            annotatedString.getStringAnnotations("url", offset, offset)
                .firstOrNull()?.let { annotation ->
                    // Open URL — production would use an Intent
                    android.util.Log.d("MarkdownText", "Link: ${annotation.item}")
                }
        },
    )
}

/**
 * Render Markdown text into an AnnotatedString with styling.
 */
private fun renderMarkdown(text: String, baseColor: Color): AnnotatedString {
    val primaryColor = Color(0xFF7CB8FF)
    val codeBgColor = Color(0xFF1E2329)
    val surfaceVariant = Color(0xFFC1C7CF)

    return buildAnnotatedString {
        var remaining = text

        while (remaining.isNotEmpty()) {
            // Check for code blocks first (```code```)
            val codeBlockMatch = CODE_BLOCK_REGEX.find(remaining)
            if (codeBlockMatch != null && codeBlockMatch.range.first == 0) {
                // Text before code block
                if (codeBlockMatch.range.first > 0) {
                    append(remaining.substring(0, codeBlockMatch.range.first))
                }
                // Code block content
                withStyle(
                    SpanStyle(
                        color = primaryColor,
                        background = Color(0xFF1E2329).copy(alpha = 0.3f),
                        fontSize = 13.sp,
                    )
                ) {
                    append(codeBlockMatch.groupValues[2])
                }
                remaining = remaining.substring(codeBlockMatch.range.last + 1)
                continue
            } else if (codeBlockMatch != null && codeBlockMatch.range.first > 0) {
                // Process up to the code block
                renderInline(remaining.substring(0, codeBlockMatch.range.first), baseColor, primaryColor)
                withStyle(
                    SpanStyle(
                        color = primaryColor,
                        background = Color(0xFF1E2329).copy(alpha = 0.3f),
                        fontSize = 13.sp,
                    )
                ) {
                    append(codeBlockMatch.groupValues[2])
                }
                remaining = remaining.substring(codeBlockMatch.range.last + 1)
                continue
            }

            // No more code blocks, render the rest as inline
            renderInline(remaining, baseColor, primaryColor)
            break
        }
    }
}

/**
 * Render inline markdown (bold, italic, inline code, links).
 */
private fun AnnotatedString.Builder.renderInline(
    text: String,
    baseColor: Color,
    primaryColor: Color,
) {
    var remaining = text

    while (remaining.isNotEmpty()) {
        // Bold (**text**)
        val boldMatch = BOLD_REGEX.find(remaining)
        if (boldMatch != null && boldMatch.range.first == 0) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(boldMatch.groupValues[1])
            }
            remaining = remaining.substring(boldMatch.range.last + 1)
            continue
        }

        // Italic (*text*)
        val italicMatch = ITALIC_REGEX.find(remaining)
        if (italicMatch != null && italicMatch.range.first == 0) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(italicMatch.groupValues[1])
            }
            remaining = remaining.substring(italicMatch.range.last + 1)
            continue
        }

        // Inline code (`code`)
        val codeMatch = INLINE_CODE_REGEX.find(remaining)
        if (codeMatch != null && codeMatch.range.first == 0) {
            withStyle(
                SpanStyle(
                    color = primaryColor,
                    background = Color(0xFF1E2329).copy(alpha = 0.3f),
                )
            ) {
                append(codeMatch.groupValues[1])
            }
            remaining = remaining.substring(codeMatch.range.last + 1)
            continue
        }

        // Link [text](url)
        val linkMatch = LINK_REGEX.find(remaining)
        if (linkMatch != null && linkMatch.range.first == 0) {
            val linkText = linkMatch.groupValues[1]
            val url = linkMatch.groupValues[2]
            pushStringAnnotation("url", url)
            withStyle(
                SpanStyle(
                    color = primaryColor,
                    textDecoration = TextDecoration.Underline,
                )
            ) {
                append(linkText)
            }
            pop()
            remaining = remaining.substring(linkMatch.range.last + 1)
            continue
        }

        // Heading (# text)
        val headingMatch = HEADING_REGEX.find(remaining)
        if (headingMatch != null && headingMatch.range.first == 0) {
            val headingText = headingMatch.groupValues[2]
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            ) {
                append("$headingText\n")
            }
            remaining = remaining.substring(headingMatch.range.last + 1)
            continue
        }

        // Bullet list (- item or * item)
        val bulletMatch = BULLET_REGEX.find(remaining)
        if (bulletMatch != null && bulletMatch.range.first == 0) {
            append("  \u2022  ${bulletMatch.groupValues[1]}")
            remaining = remaining.substring(bulletMatch.range.last + 1)
            continue
        }

        // Numbered list (1. item)
        val numberedMatch = NUMBERED_REGEX.find(remaining)
        if (numberedMatch != null && numberedMatch.range.first == 0) {
            append("  ${numberedMatch.groupValues[1]}. ${numberedMatch.groupValues[2]}")
            remaining = remaining.substring(numberedMatch.range.last + 1)
            continue
        }

        // Horizontal rule (---)
        val hrMatch = HR_REGEX.find(remaining)
        if (hrMatch != null && hrMatch.range.first == 0) {
            append("\n─────────────────\n")
            remaining = remaining.substring(hrMatch.range.last + 1)
            continue
        }

        // No match at start, take one character and continue
        val nextChar = remaining.first()
        append(nextChar)
        remaining = remaining.drop(1)
    }
}

// ── Regex patterns ─────────────────────────────────────────────────────────

private val CODE_BLOCK_REGEX = Regex("```(\\w*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
private val BOLD_REGEX = Regex("^\\*\\*(.+?)\\*\\*")
private val ITALIC_REGEX = Regex("^\\*(.+?)\\*")
private val INLINE_CODE_REGEX = Regex("^`([^`]+)`")
private val LINK_REGEX = Regex("^\\[([^]]+)]\\(([^)]+)\\)")
private val HEADING_REGEX = Regex("^(#{1,3})\\s+(.+)$", RegexOption.MULTILINE)
private val BULLET_REGEX = Regex("^[-*]\\s+(.+)$", RegexOption.MULTILINE)
private val NUMBERED_REGEX = Regex("^(\\d+)\\.\\s+(.+)$", RegexOption.MULTILINE)
private val HR_REGEX = Regex("^---+\\s*$", RegexOption.MULTILINE)
