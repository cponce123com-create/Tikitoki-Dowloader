package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MetadataSanitizer].
 * Validates text cleaning, length limits, and NFC normalization.
 */
class MetadataSanitizerTest {

    @Test
    fun `title trims and returns default for null`() {
        val result = MetadataSanitizer.title(null)
        assertEquals("Video sin título", result)
    }

    @Test
    fun `title trims and returns default for blank`() {
        val result = MetadataSanitizer.title("   ")
        assertEquals("Video sin título", result)
    }

    @Test
    fun `title truncates to limit`() {
        val longTitle = "a".repeat(200)
        val result = MetadataSanitizer.title(longTitle)
        assertEquals(180, result.length)
    }

    @Test
    fun `title removes control characters`() {
        val input = "Title\u0000with\u0001control\u001Fchars"
        val result = MetadataSanitizer.title(input)
        assertEquals("Titlewithcontrolchars", result)
    }

    @Test
    fun `title removes bidi override characters`() {
        val input = "Title‮reversed"
        val result = MetadataSanitizer.title(input)
        assertEquals("Titlereversed", result)
    }

    @Test
    fun `title removes format characters`() {
        val input = "Title⁦isolated"
        val result = MetadataSanitizer.title(input)
        assertEquals("Titleisolated", result)
    }

    @Test
    fun `title removes line/paragraph separators`() {
        val input = "Line1 Line2 Paragraph"
        val result = MetadataSanitizer.title(input)
        assertEquals("Line1Line2Paragraph", result)
    }

    @Test
    fun `title removes surrogate characters`() {
        // High surrogate without low surrogate
        val input = "Title\uD800"
        val result = MetadataSanitizer.title(input)
        assertEquals("Title", result)
    }

    @Test
    fun `title normalizes to NFC`() {
        // e with combining acute accent (NFC vs NFD)
        val input = "é" // NFD form
        val result = MetadataSanitizer.title(input)
        // Should be normalized to NFC (é as single code point)
        assertEquals("é", result)
    }

    @Test
    fun `description allows longer text`() {
        val longDesc = "a".repeat(2500)
        val result = MetadataSanitizer.description(longDesc)
        assertEquals(2000, result.length)
    }

    @Test
    fun `description returns empty for null`() {
        val result = MetadataSanitizer.description(null)
        assertEquals("", result)
    }

    @Test
    fun `error truncates to limit`() {
        val longError = "Error: ".repeat(100)
        val result = MetadataSanitizer.error(longError)
        assertEquals(350, result.length)
    }

    @Test
    fun `error returns empty for null`() {
        val result = MetadataSanitizer.error(null)
        assertEquals("", result)
    }

    @Test
    fun `preserves valid Unicode characters`() {
        val input = "Título en español 🎉 日本語 한국어"
        val result = MetadataSanitizer.title(input)
        assertEquals(input, result)
    }

    @Test
    fun `removes invisible format characters`() {
        val input = "A​B‌C‍D" // Zero-width spaces
        val result = MetadataSanitizer.title(input)
        assertEquals("ABCD", result)
    }

    @Test
    fun `trims whitespace`() {
        val input = "  Title with spaces  "
        val result = MetadataSanitizer.title(input)
        assertEquals("Title with spaces", result)
    }

    @Test
    fun `handles combining marks correctly`() {
        // Base character + combining mark should be preserved
        val input = "café" // cafe + combining acute = café
        val result = MetadataSanitizer.title(input)
        assertEquals("café", result) // NFC normalization
    }

    @Test
    fun `removes directional isolates`() {
        val input = "Text⁦isolate⁩end"
        val result = MetadataSanitizer.title(input)
        assertEquals("Textisolateend", result)
    }

    @Test
    fun `description handles multiline text`() {
        val input = "Line 1\nLine 2\nLine 3"
        val result = MetadataSanitizer.description(input)
        assertEquals("Line 1Line 2Line 3", result) // Line separators removed
    }

    @Test
    fun `title respects character count not byte count`() {
        // Emoji counts as 2 code units but 1 code point
        val input = "🎉".repeat(100) // 100 code points, 200 code units
        val result = MetadataSanitizer.title(input)
        assertEquals(100, result.length) // Should count code points
    }
}