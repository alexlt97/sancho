package com.unplugged.app

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun formatsCommonInlineMarkdown() {
        val result = "**bold** *italic* `code`".toMarkdownAnnotatedString()

        assertEquals("bold italic code", result.text)
        assertEquals(3, result.spanStyles.size)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(result.spanStyles.any { it.item.fontFamily != null })
    }

    @Test
    fun formatsHeadingsAndLists() {
        val result = "# Title\n* first\n2. second".toMarkdownAnnotatedString()

        assertEquals("Title\n• first\n2. second", result.text)
        assertEquals(1, result.spanStyles.count { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun formatsInlineMath() {
        val result = "The value is ${'$'}5${'$'} meters.".toMarkdownAnnotatedString()

        assertEquals("The value is 5 meters.", result.text)
        assertTrue(result.spanStyles.any { it.item.fontFamily == FontFamily.Serif })
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun normalizesCommonLatexMathCommands() {
        val result = "${'$'}\\sum_{k=0}^{n} \\frac{n!}{k!(n-k)!}${'$'}".toMarkdownAnnotatedString()

        assertTrue(result.text.contains("∑"))
        assertTrue(result.text.contains("(n!)/(k!(n-k)!)"))
        assertTrue(result.spanStyles.any { it.item.baselineShift != null })
    }

    @Test
    fun normalizesNestedMathAndMultilineDisplayBlocks() {
        val result = """
            The quadratic formula is:
            $$
            x = \frac{-b \pm \sqrt{b^2-4ac}}{2a}
            $$
        """.trimIndent().toMarkdownAnnotatedString()

        assertTrue(result.text.contains("x = (-b ± √(b2-4ac))/(2a)"))
    }

    @Test
    fun removesCommonLatexTextWrappers() {
        val result = "${'$'}\\text{where} x \\in \\mathbb{R}${'$'}".toMarkdownAnnotatedString()

        assertTrue(result.text.contains("where x ∈ ℝ"))
    }

    @Test
    fun formatsCalculusAndMatrixCommands() {
        val result = """
            $$
            \int_0^\infty e^{-x^2} dx = \sqrt{\pi}
            \begin{pmatrix} a & b \\ c & d \end{pmatrix}
            $$
        """.trimIndent().toMarkdownAnnotatedString()

        assertTrue(result.text.contains("∫"))
        assertTrue(result.text.contains("∞"))
        assertTrue(result.text.contains("√(π)"))
        assertTrue(result.text.contains("a"))
        assertTrue(result.text.contains("b"))
        assertTrue(result.text.contains("c"))
        assertTrue(result.text.contains("d"))
        assertTrue(result.text.contains(")"))
        assertTrue(result.text.none { it == '\\' })
    }

    @Test
    fun formatsFourierTransformAndSeriesNotation() {
        val result = """
            $$
            \hat{f}(\xi) = \int_{-\infty}^{\infty} f(x)e^{-2\pi i x \xi}\,dx
            $$
            ${'$'}${'$'}f(x) = a_0 + \sum_{n=1}^{\infty}(a_n\cos(nx)+b_n\sin(nx))${'$'}${'$'}
        """.trimIndent().toMarkdownAnnotatedString()

        assertTrue(result.text.contains("f̂(ξ)"))
        assertTrue(result.text.contains("∫"))
        assertTrue(result.text.contains("-∞"))
        assertTrue(result.text.contains("2π"))
        assertTrue(result.text.contains("cos"))
        assertTrue(result.text.contains("sin"))
        assertTrue(result.text.none { it == '\\' })
    }

    @Test
    fun formatsBoldMathAndMatrices() {
        val result = """
            $$
            \begin{bmatrix}
            \boldsymbol{a} & b \\
            c & d
            \end{bmatrix}
            $$
        """.trimIndent().toMarkdownAnnotatedString()

        assertTrue(result.text.contains("a"))
        assertTrue(result.text.contains("b"))
        assertTrue(result.text.contains("c"))
        assertTrue(result.text.contains("d"))
        assertTrue(result.text.none { it == '\\' })
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun parsesMarkdownTablesAsBlocks() {
        val blocks = parseMarkdownBlocks(
            "| A | B |\n| --- | --- |\n| 1 | 2 |",
        )

        val table = blocks.single() as MarkdownBlock.Table
        assertEquals(listOf("A", "B"), table.headers)
        assertEquals(listOf(listOf("1", "2")), table.rows)
    }

    @Test
    fun formatsResponseTimeInMillisecondsAndSeconds() {
        assertEquals("850 ms", formatResponseTime(850L))
        assertEquals("1.25 s", formatResponseTime(1_250L))
    }
}
