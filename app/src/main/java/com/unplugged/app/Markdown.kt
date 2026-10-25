package com.unplugged.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = markdown.toMarkdownAnnotatedString(),
        modifier = modifier,
    )
}

internal fun String.toMarkdownAnnotatedString(): AnnotatedString = buildAnnotatedString {
    val lines = split('\n')
    lines.forEachIndexed { index, line ->
        appendMarkdownLine(line)
        if (index < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendMarkdownLine(line: String) {
    val heading = HEADING_PATTERN.matchEntire(line)
    val unorderedItem = UNORDERED_ITEM_PATTERN.matchEntire(line)
    val orderedItem = ORDERED_ITEM_PATTERN.matchEntire(line)

    when {
        heading != null -> withStyle(
            SpanStyle(
                fontSize = when (heading.groupValues[1].length) {
                    1 -> 20.sp
                    2 -> 18.sp
                    else -> 16.sp
                },
                fontWeight = FontWeight.Bold,
            ),
        ) {
            appendInlineMarkdown(heading.groupValues[2])
        }

        unorderedItem != null -> {
            append("• ")
            appendInlineMarkdown(unorderedItem.groupValues[1])
        }

        orderedItem != null -> {
            append("${orderedItem.groupValues[1]}. ")
            appendInlineMarkdown(orderedItem.groupValues[2])
        }

        else -> appendInlineMarkdown(line)
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(value: String) {
    var cursor = 0
    INLINE_PATTERN.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        when {
            match.groupValues[2].isNotEmpty() -> withStyle(BOLD_STYLE) {
                append(match.groupValues[2])
            }

            match.groupValues[3].isNotEmpty() -> withStyle(BOLD_STYLE) {
                append(match.groupValues[3])
            }

            match.groupValues[4].isNotEmpty() -> withStyle(ITALIC_STYLE) {
                append(match.groupValues[4])
            }

            match.groupValues[5].isNotEmpty() -> withStyle(ITALIC_STYLE) {
                append(match.groupValues[5])
            }

            match.groupValues[6].isNotEmpty() -> withStyle(CODE_STYLE) {
                append(match.groupValues[6])
            }

            match.groupValues[7].isNotEmpty() -> withStyle(MATH_STYLE) {
                append(match.groupValues[7])
            }

            else -> append(match.value)
        }
        cursor = match.range.last + 1
    }
    append(value.substring(cursor))
}

internal fun formatResponseTime(milliseconds: Long): String =
    if (milliseconds < 1_000L) {
        "$milliseconds ms"
    } else {
        "%.2f s".format(java.util.Locale.US, milliseconds / 1_000.0)
    }

private val BOLD_STYLE = SpanStyle(fontWeight = FontWeight.Bold)
private val ITALIC_STYLE = SpanStyle(fontStyle = FontStyle.Italic)
private val CODE_STYLE = SpanStyle(fontFamily = FontFamily.Monospace)
private val MATH_STYLE = SpanStyle(
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    fontSize = 17.sp,
)

private val HEADING_PATTERN = Regex("""^(#{1,6})\s+(.+)$""")
private val UNORDERED_ITEM_PATTERN = Regex("""^\s*[-*+]\s+(.+)$""")
private val ORDERED_ITEM_PATTERN = Regex("""^\s*(\d+)\.\s+(.+)$""")
private val INLINE_PATTERN = Regex(
    """(\*\*(.+?)\*\*|__(.+?)__|(?<!\*)\*([^*\n]+)\*(?!\*)|(?<!_)_([^_\n]+)_(?!_)|`([^`\n]+)`|\$([^$\n]+)\$)""",
)
