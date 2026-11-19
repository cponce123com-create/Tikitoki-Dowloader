package com.tikitoki.downloader.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

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
     * These hashes are pinned to the exact binary shipped by the dependency version in the
     * version catalog. Update this map in the same change that upgrades youtubedl-android.
     * An unknown version is rejected; it must never silently become trusted.
     */
    private val KNOWN_HASHES =
        mapOf(
            // youtubedl-android 0.18.1 / library AAR res/raw/ytdlp
            "2025.11.12" to "89a0d9058ea9018e380b7771898ff46e393a1986dcd13fef331693c87ce1fca4",
        )

    /**
     * Computes the SHA-256 hash of a file.
     */
    @Throws(NoSuchAlgorithmException::class)
    internal fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        FileInputStream(file).use { input ->
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies the yt-dlp binary against known-good hashes.
     * Returns a VerificationResult indicating success, failure, or unknown version.
     */
    internal sealed class VerificationResult {
        data class Verified(
            val version: String,
            val hash: String,
        ) : VerificationResult()

        data class Failed(
            val version: String,
            val expectedHash: String,
            val actualHash: String,
        ) : VerificationResult()

        data class UnknownVersion(
            val version: String,
            val actualHash: String,
        ) : VerificationResult()

        data class Error(
            val message: String,
        ) : VerificationResult()
    }

    internal fun isTrusted(result: VerificationResult): Boolean = result is VerificationResult.Verified

    /**
     * Verifies the currently installed yt-dlp binary.
     * Called after an update to ensure the binary matches official releases.
     */
    internal fun verifyInstalledBinary(context: Context): VerificationResult {
        try {
            val binary = ExtractorRecovery.binary(context)
            if (!binary.exists()) {
                return VerificationResult.Error("yt-dlp binary not found at ${binary.absolutePath}")
            }

            val version =
                ExtractorVersion.read(context)
                    ?: return VerificationResult.Error("Cannot read yt-dlp version")

            val actualHash = computeSha256(binary)

            return when (val expectedHash = KNOWN_HASHES[version]) {
                null -> VerificationResult.UnknownVersion(version, actualHash)
                actualHash -> VerificationResult.Verified(version, actualHash)
                else -> VerificationResult.Failed(version, expectedHash, actualHash)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return VerificationResult.Error("Verification failed: ${e.message}")
        }
    }

    /**
     * Verifies and logs the result. Returns true if verified, false otherwise.
     * Logs details for audit trail.
     */
    internal fun verifyAndLog(context: Context): Boolean {
        val result = verifyInstalledBinary(context)
        return when (result) {
            is VerificationResult.Verified -> {
                Log.i(TAG, "✅ yt-dlp verification PASSED: version=${result.version}, hash=${result.hash}")
                true
            }
            is VerificationResult.Failed -> {
                Log.e(
                    TAG,
                    "❌ yt-dlp verification FAILED: version=${result.version}, expected=${result.expectedHash}, actual=${result.actualHash}",
                )
                false
            }
            is VerificationResult.UnknownVersion -> {
                Log.w(
                    TAG,
                    "⚠️ yt-dlp version UNKNOWN (no reference hash): version=${result.version}, hash=${result.actualHash}. " +
                        "Update KNOWN_HASHES in ExtractorVerifier.kt with the official hash from https://github.com/yt-dlp/yt-dlp/releases/tag/${result.version}",
                )
                // Fail closed: a new extractor must be explicitly reviewed and pinned
                // before an update can replace the known-good binary.
                false
            }
            is VerificationResult.Error -> {
                Log.e(TAG, "❌ yt-dlp verification ERROR: ${result.message}")
                false
            }
        }
    }
}
