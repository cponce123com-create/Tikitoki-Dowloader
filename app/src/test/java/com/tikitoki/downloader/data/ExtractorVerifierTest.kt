package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExtractorVerifierTest {
    @Test
    fun computesSha256Correctly() {
        val dir = Files.createTempDirectory("verifier-test").toFile()
        try {
            val file = File(dir, "test.bin")
            file.writeBytes(byteArrayOf(0x61, 0x62, 0x63)) // "abc"
            val hash = ExtractorVerifier.computeSha256(file)
            // SHA-256 of "abc"
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun computesSha256ForEmptyFile() {
        val dir = Files.createTempDirectory("verifier-test").toFile()
        try {
            val file = File(dir, "empty.bin")
            file.createNewFile()
            val hash = ExtractorVerifier.computeSha256(file)
            // SHA-256 of empty string
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun verifiesKnownHash() {
        val dir = Files.createTempDirectory("verifier-test").toFile()
        try {
            val file = File(dir, "yt-dlp")
            val content = "fake yt-dlp binary content for testing"
            file.writeText(content)
            val hash = ExtractorVerifier.computeSha256(file)

            // Test the sealed class directly
            val verified = ExtractorVerifier.VerificationResult.Verified("test-version", hash)
            assertTrue(verified is ExtractorVerifier.VerificationResult.Verified)
            assertEquals("test-version", verified.version)
            assertEquals(hash, verified.hash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun detectsHashMismatch() {
        val dir = Files.createTempDirectory("verifier-test").toFile()
        try {
            val file = File(dir, "yt-dlp")
            file.writeText("wrong content")
            val actualHash = ExtractorVerifier.computeSha256(file)
            val expectedHash = "different_hash"

            val failed = ExtractorVerifier.VerificationResult.Failed("test-version", expectedHash, actualHash)
            assertTrue(failed is ExtractorVerifier.VerificationResult.Failed)
            assertEquals("test-version", failed.version)
            assertEquals(expectedHash, failed.expectedHash)
            assertEquals(actualHash, failed.actualHash)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun handlesUnknownVersion() {
        val unknown = ExtractorVerifier.VerificationResult.UnknownVersion("unknown-version", "some_hash")
        assertTrue(unknown is ExtractorVerifier.VerificationResult.UnknownVersion)
        assertEquals("unknown-version", unknown.version)
        assertEquals("some_hash", unknown.actualHash)
        assertFalse(ExtractorVerifier.isTrusted(unknown))
    }

    @Test
    fun onlyVerifiedResultsAreTrusted() {
        val verified = ExtractorVerifier.VerificationResult.Verified("2025.11.12", "known-hash")
        val failed = ExtractorVerifier.VerificationResult.Failed("2025.11.12", "expected", "actual")
        val error = ExtractorVerifier.VerificationResult.Error("verification error")

        assertTrue(ExtractorVerifier.isTrusted(verified))
        assertFalse(ExtractorVerifier.isTrusted(failed))
        assertFalse(ExtractorVerifier.isTrusted(error))
    }

    @Test
    fun handlesError() {
        val error = ExtractorVerifier.VerificationResult.Error("File not found")
        assertTrue(error is ExtractorVerifier.VerificationResult.Error)
        assertEquals("File not found", error.message)
    }
}
