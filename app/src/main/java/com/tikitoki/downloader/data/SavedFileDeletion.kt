package com.tikitoki.downloader.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

enum class DeleteCompletedResult { DELETED, FILE_MISSING, FAILED }

internal enum class ContentDeletionKind { DOCUMENT, MEDIA_STORE, UNSUPPORTED }

internal fun contentDeletionKind(isDocument: Boolean, authority: String?): ContentDeletionKind = when {
    isDocument -> ContentDeletionKind.DOCUMENT
    authority == "media" -> ContentDeletionKind.MEDIA_STORE
    else -> ContentDeletionKind.UNSUPPORTED
}

/** Interpret a provider's result without treating permission failures as a missing file. */
internal fun deletionOutcome(delete: () -> Boolean, isMissing: () -> Boolean, onError: (Exception) -> Unit = {}): DeleteCompletedResult =
    try {
        if (delete()) DeleteCompletedResult.DELETED
        else if (isMissing()) DeleteCompletedResult.FILE_MISSING else DeleteCompletedResult.FAILED
    } catch (_: FileNotFoundException) {
        DeleteCompletedResult.FILE_MISSING
    } catch (e: SecurityException) {
        onError(e)
        DeleteCompletedResult.FAILED
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        onError(e)
        DeleteCompletedResult.FAILED
    }

internal fun deleteSavedFile(context: Context, uri: Uri?): DeleteCompletedResult {
    if (uri == null) return DeleteCompletedResult.FILE_MISSING
    return when (uri.scheme) {
        "content" -> {
            val resolver = context.contentResolver
            when (contentDeletionKind(DocumentsContract.isDocumentUri(context, uri), uri.authority)) {
                ContentDeletionKind.DOCUMENT -> deletionOutcome(
                    delete = { DocumentsContract.deleteDocument(resolver, uri) },
                    isMissing = {
                        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                            ?.use { !it.moveToFirst() } ?: false
                    },
                    onError = { DiagnosticHistory.record(context, it.message) },
                )
                ContentDeletionKind.MEDIA_STORE -> deletionOutcome(
                    delete = { resolver.delete(uri, null, null) > 0 },
                    isMissing = { true }, // MediaStore reports zero rows for a missing item.
                    onError = { DiagnosticHistory.record(context, it.message) },
                )
                ContentDeletionKind.UNSUPPORTED -> DeleteCompletedResult.FAILED
            }
        }
        "file" -> {
            @Suppress("DEPRECATION")
            val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TikitokiDownloader").canonicalFile
            val target = runCatching { File(requireNotNull(uri.path)).canonicalFile }.getOrNull()
                ?: return DeleteCompletedResult.FAILED
            if (!target.path.startsWith(root.path + File.separator)) return DeleteCompletedResult.FAILED
            deletionOutcome(delete = { target.delete() }, isMissing = { !target.exists() }, onError = { DiagnosticHistory.record(context, it.message) })
        }
        else -> DeleteCompletedResult.FAILED
    }
}
