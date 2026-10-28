package com.unplugged.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownAndroidTest {
    @Test
    fun formatsLatexFractionsWithoutAndroidRegexCrash() {
        val result = "${'$'}\\frac{1}{2}${'$'}".toMarkdownAnnotatedString()

        assertTrue(result.text.contains("(1)/(2)"))
    }

    @Test
    fun formatsFourierTransformOnAndroid() {
        val result = """
            ${'$'}${'$'}\hat{f}(\xi) = \int_{-\infty}^{\infty} f(x)e^{-2\pi i x \xi}\,dx${'$'}${'$'}
        """.trimIndent().toMarkdownAnnotatedString()

        assertTrue(result.text.contains("f̂(ξ)"))
        assertTrue(result.text.contains("∫"))
        assertTrue(result.text.contains("∞"))
        assertTrue(result.text.none { it == '\\' })
    }

    @Test
    fun formatsBoldMatrixOnAndroid() {
        val result = """
            ${'$'}${'$'}\begin{bmatrix} \boldsymbol{a} & b \\ c & d \end{bmatrix}${'$'}${'$'}
        """.trimIndent().toMarkdownAnnotatedString()

        assertTrue(result.text.contains("a"))
        assertTrue(result.text.contains("b"))
        assertTrue(result.text.contains("c"))
        assertTrue(result.text.contains("d"))
        assertTrue(result.text.none { it == '\\' })
    }
}
