package com.tikitoki.downloader.data

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedFileDeletionTest {
    @Test fun routesDocumentsAndMediaStoreSeparately() {
        assertEquals(ContentDeletionKind.DOCUMENT, contentDeletionKind(true, "com.example.documents"))
        assertEquals(ContentDeletionKind.DOCUMENT, contentDeletionKind(true, "com.android.providers.media.documents"))
        assertEquals(ContentDeletionKind.MEDIA_STORE, contentDeletionKind(false, "media"))
        assertEquals(ContentDeletionKind.UNSUPPORTED, contentDeletionKind(false, "unknown"))
    }

    @Test fun distinguishesDeletedMissingAndFailed() {
        assertEquals(DeleteCompletedResult.DELETED, deletionOutcome({ true }, { false }))
        assertEquals(DeleteCompletedResult.FILE_MISSING, deletionOutcome({ false }, { true }))
        assertEquals(DeleteCompletedResult.FILE_MISSING, deletionOutcome({ throw FileNotFoundException() }, { false }))
        assertEquals(DeleteCompletedResult.FAILED, deletionOutcome({ false }, { false }))
        assertEquals(DeleteCompletedResult.FAILED, deletionOutcome({ throw SecurityException() }, { true }))
        assertEquals(DeleteCompletedResult.FAILED, deletionOutcome({ false }, { throw SecurityException() }))
    }
}
