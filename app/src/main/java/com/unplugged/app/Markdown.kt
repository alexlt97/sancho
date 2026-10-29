package com.unplugged.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        parseMarkdownBlocks(markdown).forEach { block ->
            when (block) {
                is MarkdownBlock.Text -> Text(block.value.toMarkdownAnnotatedString())
                is MarkdownBlock.Table -> MarkdownTable(block)
            }
        }
    }
}

internal sealed interface MarkdownBlock {
    data class Text(val value: String) : MarkdownBlock

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : MarkdownBlock
}

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val textLines = mutableListOf<String>()

    fun flushText() {
        if (textLines.isNotEmpty()) {
            blocks += MarkdownBlock.Text(textLines.joinToString("\n"))
            textLines.clear()
        }
    }

    var index = 0
    while (index < lines.size) {
        if (index + 1 < lines.size && isTableSeparator(lines[index + 1])) {
            val headers = parseTableRow(lines[index])
            if (headers.size >= 2) {
                flushText()
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].contains('|')) {
                    rows += parseTableRow(lines[index])
                    index++
                }
                blocks += MarkdownBlock.Table(headers, rows)
                continue
            }
        }
        textLines += lines[index]
        index++
    }
    flushText()
    return blocks
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table) {
    val columnCount = maxOf(
        table.headers.size,
        table.rows.maxOfOrNull { it.size } ?: 0,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        MarkdownTableRow(table.headers, columnCount, isHeader = true)
        table.rows.forEach { row -> MarkdownTableRow(row, columnCount, isHeader = false) }
    }
}

@Composable
private fun MarkdownTableRow(cells: List<String>, columnCount: Int, isHeader: Boolean) {
    val tableTextSize = when {
        columnCount >= 6 -> 11.sp
        columnCount >= 4 -> 12.sp
        else -> 14.sp
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(columnCount) { index ->
            Surface(
                modifier = Modifier.weight(1f),
                color = if (isHeader) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                },
            ) {
                Text(
                    text = cells.getOrNull(index).orEmpty().toMarkdownAnnotatedString(),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = tableTextSize,
                        fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

private fun isTableSeparator(line: String): Boolean {
    val cells = parseTableRow(line)
    return cells.size >= 2 && cells.all { it.matches(Regex(":?-{3,}:?")) }
}

private fun parseTableRow(line: String): List<String> {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    return trimmed.split(Regex("(?<!\\\\)\\|"))
        .map { it.trim().replace("\\|", "|") }
}

internal fun String.toMarkdownAnnotatedString(): AnnotatedString = buildAnnotatedString {
    val lines = split('\n')
    var index = 0
    while (index < lines.size) {
        if (index > 0) append('\n')
        val delimiter = displayMathDelimiter(lines[index])
        if (delimiter != null) {
            val closingIndex = lines
                .drop(index + 1)
                .indexOfFirst { it.trim() == delimiter }
                .let { if (it == -1) -1 else index + 1 + it }
            if (closingIndex != -1) {
                appendMath(lines.subList(index + 1, closingIndex).joinToString("\n"))
                index = closingIndex + 1
                continue
            }
        }
        appendMarkdownLine(lines[index])
        index++
    }
}

private fun displayMathDelimiter(line: String): String? = when (line.trim()) {
    "$$" -> "$$"
    "\\[" -> "\\]"
    else -> null
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

            match.groupValues[7].isNotEmpty() -> appendMath(match.groupValues[7])
            match.groupValues[8].isNotEmpty() -> appendMath(match.groupValues[8])
            match.groupValues[9].isNotEmpty() -> appendMath(match.groupValues[9])
            match.groupValues[10].isNotEmpty() -> appendMath(match.groupValues[10])

            else -> append(match.value)
        }
        cursor = match.range.last + 1
    }
    append(value.substring(cursor))
}

private fun AnnotatedString.Builder.appendMath(value: String) {
    val normalized = normalizeMath(value)
    appendMathSegments(normalized)
}

private fun AnnotatedString.Builder.appendMathSegments(value: String) {
    var segmentStart = 0
    while (segmentStart < value.length) {
        val boldStart = value.indexOf(BOLD_MATH_START, segmentStart)
        if (boldStart == -1) {
            appendMathText(value.substring(segmentStart), bold = false)
            return
        }
        appendMathText(value.substring(segmentStart, boldStart), bold = false)
        val contentStart = boldStart + BOLD_MATH_START.length
        val boldEnd = value.indexOf(BOLD_MATH_END, contentStart)
        if (boldEnd == -1) {
            appendMathText(value.substring(contentStart), bold = true)
            return
        }
        appendMathText(value.substring(contentStart, boldEnd), bold = true)
        segmentStart = boldEnd + BOLD_MATH_END.length
    }
}

private fun AnnotatedString.Builder.appendMathText(value: String, bold: Boolean) {
    var cursor = 0
    val scriptPattern = Regex("""([_^])(\{[^{}]*\}|[^\s])""")
    scriptPattern.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        val script = match.groupValues[2].removePrefix("{").removeSuffix("}")
        withStyle(
            MATH_STYLE.copy(
                fontSize = 12.sp,
                fontWeight = if (bold) FontWeight.Bold else null,
                baselineShift = if (match.groupValues[1] == "^") {
                    BaselineShift.Superscript
                } else {
                    BaselineShift.Subscript
                },
            ),
        ) {
            append(script)
        }
        cursor = match.range.last + 1
    }
    withStyle(MATH_STYLE.copy(fontWeight = if (bold) FontWeight.Bold else null)) {
        append(value.substring(cursor))
    }
}

private fun normalizeMath(value: String): String {
    var normalized = value
    repeat(3) {
        mapOf(
            "\\boldsymbol" to BOLD_MATH_START,
            "\\mathbf" to BOLD_MATH_START,
            "\\bold" to BOLD_MATH_START,
            "\\bm" to BOLD_MATH_START,
            "\\textbf" to BOLD_MATH_START,
        ).forEach { (command, marker) ->
            normalized = replaceMathCommand(normalized, command, 1) { args ->
                "$marker${args[0]}$BOLD_MATH_END"
            }
        }
        listOf(
            MathEnvironment("pmatrix", "(", ")"),
            MathEnvironment("bmatrix", "[", "]"),
            MathEnvironment("Bmatrix", "⟦", "⟧"),
            MathEnvironment("vmatrix", "|", "|"),
            MathEnvironment("Vmatrix", "‖", "‖"),
            MathEnvironment("matrix", "[", "]"),
            MathEnvironment("smallmatrix", "[", "]"),
            MathEnvironment("cases", "⎧", "⎫"),
            MathEnvironment("aligned", "", ""),
            MathEnvironment("align", "", ""),
            MathEnvironment("gathered", "", ""),
            MathEnvironment("array", "[", "]", consumeColumnSpec = true),
        ).forEach { environment ->
            normalized = replaceMathEnvironment(normalized, environment)
        }
        normalized = replaceMathCommand(normalized, "\\frac", 2) { args ->
            "(${args[0]})/(${args[1]})"
        }
        normalized = replaceMathCommand(normalized, "\\binom", 2) { args ->
            "C(${args[0]},${args[1]})"
        }
        normalized = replaceMathCommand(normalized, "\\sqrt", 1) { args ->
            "√(${args[0]})"
        }
        normalized = replaceMathCommand(normalized, "\\mathbb", 1) { args ->
            when (args[0]) {
                "R" -> "ℝ"
                "N" -> "ℕ"
                "Z" -> "ℤ"
                "Q" -> "ℚ"
                else -> args[0]
            }
        }
        mapOf(
            "\\hat" to "\u0302",
            "\\widehat" to "\u0302",
            "\\tilde" to "\u0303",
            "\\widetilde" to "\u0303",
            "\\bar" to "\u0304",
            "\\overline" to "\u0304",
            "\\dot" to "\u0307",
            "\\ddot" to "\u0308",
            "\\vec" to "\u20d7",
        ).forEach { (command, combiningMark) ->
            normalized = replaceMathCommand(normalized, command, 1) { args ->
                args[0] + combiningMark
            }
        }
        listOf(
            "\\text",
            "\\mathrm",
            "\\mathit",
            "\\mathcal",
            "\\operatorname",
            "\\underline",
        ).forEach { command ->
            normalized = replaceMathCommand(normalized, command, 1) { args -> args[0] }
        }
    }
    val symbols = mapOf(
        "\\notin" to "∉",
        "\\subseteq" to "⊆",
        "\\supseteq" to "⊇",
        "\\leqslant" to "⩽",
        "\\geqslant" to "⩾",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\emptyset" to "∅",
        "\\limits" to "",
        "\\nolimits" to "",
        "\\begin{pmatrix}" to "(",
        "\\end{pmatrix}" to ")",
        "\\begin{bmatrix}" to "[",
        "\\end{bmatrix}" to "]",
        "\\begin{matrix}" to "[",
        "\\end{matrix}" to "]",
        "\\begin{aligned}" to "",
        "\\end{aligned}" to "",
        "\\begin{cases}" to "{",
        "\\end{cases}" to "",
        "\\displaystyle" to "",
        "\\textstyle" to "",
        "\\quad" to " ",
        "\\qquad" to "  ",
        "\\;" to " ",
        "\\:" to " ",
        "\\!" to "",
        "\\," to " ",
        "\\ldots" to "…",
        "\\cdots" to "⋯",
        "\\int" to "∫",
        "\\iint" to "∬",
        "\\iiint" to "∭",
        "\\oint" to "∮",
        "\\lim" to "lim",
        "\\sin" to "sin",
        "\\cos" to "cos",
        "\\tan" to "tan",
        "\\log" to "log",
        "\\ln" to "ln",
        "\\exp" to "exp",
        "\\det" to "det",
        "\\max" to "max",
        "\\min" to "min",
        "\\angle" to "∠",
        "\\degree" to "°",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\sqrt" to "√",
        "\\cdot" to "·",
        "\\times" to "×",
        "\\leq" to "≤",
        "\\geq" to "≥",
        "\\ne" to "≠",
        "\\infty" to "∞",
        "\\in" to "∈",
        "\\to" to "→",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\theta" to "θ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\pi" to "π",
        "\\sigma" to "σ",
        "\\phi" to "φ",
        "\\omega" to "ω",
        "\\xi" to "ξ",
        "\\eta" to "η",
        "\\epsilon" to "ε",
        "\\varepsilon" to "ϵ",
        "\\zeta" to "ζ",
        "\\kappa" to "κ",
        "\\nu" to "ν",
        "\\rho" to "ρ",
        "\\tau" to "τ",
        "\\upsilon" to "υ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\varphi" to "ϕ",
        "\\vartheta" to "ϑ",
        "\\Gamma" to "Γ",
        "\\Delta" to "Δ",
        "\\Theta" to "Θ",
        "\\Lambda" to "Λ",
        "\\Xi" to "Ξ",
        "\\Pi" to "Π",
        "\\Sigma" to "Σ",
        "\\Phi" to "Φ",
        "\\Psi" to "Ψ",
        "\\Omega" to "Ω",
    )
    symbols.forEach { (latex, symbol) -> normalized = normalized.replace(latex, symbol) }
    return normalized
        .replace("\\\\", "\n")
        .replace("&", "  ")
        .replace("\\left", "")
        .replace("\\right", "")
        .replace("{", "")
        .replace("}", "")
}

private const val BOLD_MATH_START = "\uE000"
private const val BOLD_MATH_END = "\uE001"

private data class MathEnvironment(
    val name: String,
    val opening: String,
    val closing: String,
    val consumeColumnSpec: Boolean = false,
)

private fun replaceMathEnvironment(value: String, environment: MathEnvironment): String {
    val begin = "\\begin{${environment.name}}"
    val end = "\\end{${environment.name}}"
    val output = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        when {
            value.startsWith(begin, index) -> {
                var next = index + begin.length
                if (environment.consumeColumnSpec) {
                    while (next < value.length && value[next].isWhitespace()) next++
                    readMathArgument(value, next)?.let { next = it.second }
                }
                output.append(environment.opening)
                index = next
            }

            value.startsWith(end, index) -> {
                output.append(environment.closing)
                index += end.length
            }

            else -> {
                output.append(value[index])
                index++
            }
        }
    }
    return output.toString()
}

private fun replaceMathCommand(
    value: String,
    command: String,
    argumentCount: Int,
    replacement: (List<String>) -> String,
): String {
    val output = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (!value.startsWith(command, index)) {
            output.append(value[index])
            index++
            continue
        }

        var argumentIndex = index + command.length
        val arguments = mutableListOf<String>()
        repeat(argumentCount) {
            while (argumentIndex < value.length && value[argumentIndex].isWhitespace()) argumentIndex++
            val argument = readMathArgument(value, argumentIndex) ?: return@repeat
            arguments += argument.first
            argumentIndex = argument.second
        }
        if (arguments.size != argumentCount) {
            output.append(value[index])
            index++
        } else {
            output.append(replacement(arguments))
            index = argumentIndex
        }
    }
    return output.toString()
}

private fun readMathArgument(value: String, start: Int): Pair<String, Int>? {
    if (value.getOrNull(start) != '{') return null
    val content = StringBuilder()
    var depth = 0
    for (index in start until value.length) {
        when (value[index]) {
            '{' -> {
                depth++
                if (depth > 1) content.append('{')
            }

            '}' -> {
                depth--
                if (depth == 0) return content.toString() to (index + 1)
                content.append('}')
            }

            else -> content.append(value[index])
        }
    }
    return null
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
    """(\*\*(.+?)\*\*|__(.+?)__|(?<!\*)\*([^*\n]+)\*(?!\*)|(?<!_)_([^_\n]+)_(?!_)|`([^`\n]+)`|\$\$([^$\n]+)\$\$|\$([^$\n]+)\$|\\\(([^)\n]+)\\\)|\\\[([^]]+)\\\])""",
)
