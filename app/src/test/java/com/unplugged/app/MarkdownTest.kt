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
    fun formatsResponseTimeInMillisecondsAndSeconds() {
        assertEquals("850 ms", formatResponseTime(850L))
        assertEquals("1.25 s", formatResponseTime(1_250L))
    }
}
