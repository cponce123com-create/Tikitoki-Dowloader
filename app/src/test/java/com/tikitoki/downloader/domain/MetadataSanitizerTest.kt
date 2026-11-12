package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer
import kotlin.random.Random

class MetadataSanitizerTest {
    @Test fun normalizesToNfcAndPreservesPlainText() {
        assertEquals("Café", MetadataSanitizer.title("Cafe\u0301"))
        assertEquals("Video sin título", MetadataSanitizer.title(null))
        assertEquals("", MetadataSanitizer.description(null))
        assertEquals("<b>texto</b>", MetadataSanitizer.description("<b>texto</b>"))
    }

    @Test fun removesControlsAndBidiFormatCharacters() {
        val malicious = "A\u0000\n\r\t\u202e\u202d\u2066\u2067\u2068\u2069\u200bB"
        assertEquals("AB", MetadataSanitizer.title(malicious))
        assertEquals("AB", MetadataSanitizer.description(malicious))
        assertEquals("AB", MetadataSanitizer.error(malicious))
    }

    @Test fun enforcesLimitsByUnicodeCodePointNotUtf16Unit() {
        assertEquals(180, MetadataSanitizer.title("x".repeat(500)).length)
        assertEquals(2000, MetadataSanitizer.description("x".repeat(3000)).length)
        assertEquals(350, MetadataSanitizer.error("x".repeat(500)).length)
        assertEquals(180, MetadataSanitizer.title("😀".repeat(300)).codePointCount(0, MetadataSanitizer.title("😀".repeat(300)).length))
    }

    @Test fun fuzzesThousandInputsWithFixedSeed() {
        val random = Random(20261112)
        val alphabet = intArrayOf(0, 9, 10, 13, 65, 90, 0x0301, 0x00e9, 0x202e, 0x2066, 0x200b, 0x1f600)
        repeat(1000) {
            val raw = buildString {
                repeat(random.nextInt(0, 300)) {
                    appendCodePoint(alphabet[random.nextInt(alphabet.size)])
                }
            }
            val sanitized = MetadataSanitizer.description(raw)
            assertTrue(sanitized.codePointCount(0, sanitized.length) <= 2000)
            assertTrue(Normalizer.isNormalized(sanitized, Normalizer.Form.NFC))
            sanitized.codePoints().forEach { cp ->
                assertFalse(Character.isISOControl(cp))
                assertFalse(Character.getType(cp) == Character.FORMAT.toInt())
                assertFalse(cp in 0x202A..0x202E || cp in 0x2066..0x2069)
            }
        }
    }
}
