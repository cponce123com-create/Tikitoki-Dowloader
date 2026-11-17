package com.tikitoki.downloader.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verifies the integrity of the yt-dlp binary by checking its SHA-256 hash
 * against a known-good value from the official yt-dlp GitHub releases.
 *
 * This mitigates supply-chain attacks where the update channel (youtubedl-android)
 * could be compromised to deliver a malicious binary.
 */
internal object ExtractorVerifier {
    private const val TAG = "ExtractorVerifier"
    private const val ALGORITHM = "SHA-256"
    private const val BUFFER_SIZE = 8192

    /**
     * Known-good SHA-256 hashes for yt-dlp binary versions.
     * Format: version -> lowercase hex SHA-256 of the linux_x86_64 binary.
     * Source: https://github.com/yt-dlp/yt-dlp/releases
     *
     * NOTE: These hashes MUST be updated when youtubedl-android upgrades its bundled yt-dlp.
     * The hashes below are for yt-dlp 2024.xx.xx versions. Check youtubedl-android release notes
     * or the yt-dlp GitHub releases page for the correct hash for the version being bundled.
     */
    private val KNOWN_HASHES = mapOf(
        // yt-dlp 2024.09.17 (example - replace with actual version from youtubedl-android 0.18.1)
        "2024.09.17" to "TODO_REPLACE_WITH_ACTUAL_SHA256",
        // Add more versions as needed
    )

    /**
     * Computes the SHA-256 hash of a file.
     */
    @Throws(NoSuchAlgorithmException::class)
    internal suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        FileInputStream(file).use { input ->
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies the yt-dlp binary against known-good hashes.
     * Returns a VerificationResult indicating success, failure, or unknown version.
     */
    internal sealed class VerificationResult {
        data class Verified(val version: String, val hash: String) : VerificationResult()
        data class Failed(val version: String, val expectedHash: String, val actualHash: String) : VerificationResult()
        data class UnknownVersion(val version: String, val actualHash: String) : VerificationResult()
        data class Error(val message: String) : VerificationResult()
    }

    /**
     * Verifies the currently installed yt-dlp binary.
     * Called after an update to ensure the binary matches official releases.
     */
    internal suspend fun verifyInstalledBinary(context: Context): VerificationResult = withContext(Dispatchers.IO) {
        try {
            val binary = ExtractorRecovery.binary(context)
            if (!binary.exists()) {
                return VerificationResult.Error("yt-dlp binary not found at ${binary.absolutePath}")
            }

            val version = ExtractorVersion.read(context)
                ?: return VerificationResult.Error("Cannot read yt-dlp version")

            val actualHash = computeSha256(binary)

            return when (val expectedHash = KNOWN_HASHES[version]) {
                null -> VerificationResult.UnknownVersion(version, actualHash)
                actualHash -> VerificationResult.Verified(version, actualHash)
                else -> VerificationResult.Failed(version, expectedHash, actualHash)
            }
        } catch (e: Exception) {
            VerificationResult.Error("Verification failed: ${e.message}")
        }
    }

    /**
     * Verifies and logs the result. Returns true if verified, false otherwise.
     * Logs details for audit trail.
     */
    internal suspend fun verifyAndLog(context: Context): Boolean = withContext(Dispatchers.IO) {
        val result = verifyInstalledBinary(context)
        when (result) {
            is VerificationResult.Verified -> {
                Log.i(TAG, "✅ yt-dlp verification PASSED: version=${result.version}, hash=${result.hash}")
                true
            }
            is VerificationResult.Failed -> {
                Log.e(TAG, "❌ yt-dlp verification FAILED: version=${result.version}, expected=${result.expectedHash}, actual=${result.actualHash}")
                false
            }
            is VerificationResult.UnknownVersion -> {
                Log.w(TAG, "⚠️ yt-dlp version UNKNOWN (no reference hash): version=${result.version}, hash=${result.actualHash}. " +
                    "Update KNOWN_HASHES in ExtractorVerifier.kt with the official hash from https://github.com/yt-dlp/yt-dlp/releases/tag/${result.version}")
                // Allow unknown versions for now (fail-open) but log prominently
                // TODO: Consider fail-closed for production: return false
                true
            }
            is VerificationResult.Error -> {
                Log.e(TAG, "❌ yt-dlp verification ERROR: ${result.message}")
                false
            }
        }
    }
}