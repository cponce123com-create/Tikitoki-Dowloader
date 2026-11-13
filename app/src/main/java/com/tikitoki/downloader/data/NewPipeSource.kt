package com.tikitoki.downloader.data

import com.tikitoki.downloader.domain.DownloadInputPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

/**
 * Independent metadata extractor for supported NewPipe services.
 * Does not provide access to private, DRM or subscriber-only media.
 */
class NewPipeSource {
    private val http = object : Downloader() {
        override fun execute(request: Request): Response {
            val url = URI(request.url())
            require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null) {
                "Only HTTPS media metadata endpoints are permitted"
            }
            val host = url.host.lowercase()
            require(host != "localhost" && !host.endsWith(".local") &&
                !host.endsWith(".internal") && !host.matches(Regex("[0-9.]+"))) {
                "Unsafe extractor endpoint"
            }
            val connection = URL(request.url()).openConnection() as HttpURLConnection
            connection.connectTimeout = 12000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = false
            connection.requestMethod = request.httpMethod()
            request.headers().forEach { (key, values) ->
                if (!key.equals("host", true) && !key.equals("cookie", true))
                    values.forEach { connection.addRequestProperty(key, it) }
            }
            try {
                val data = request.dataToSend()
                if (data != null) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(data) }
                }
                val code = connection.responseCode
                val bytes = (if (code < 400) connection.inputStream else connection.errorStream)
                    ?.use { it.readNBytes(4 * 1024 * 1024 + 1) } ?: byteArrayOf()
                require(bytes.size <= 4 * 1024 * 1024) { "Extractor response too large" }
                return Response(code, connection.responseMessage ?: "",
                    connection.headerFields.filterKeys { it != null },
                    bytes.toString(Charsets.UTF_8), connection.url.toString())
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun inspectVideo(url: String): VideoEntry = withContext(Dispatchers.IO) {
        val safe = DownloadInputPolicy.canonicalUrl(url)
        synchronized(NewPipeSource::class.java) { NewPipe.init(http) }
        val info = StreamInfo.getInfo(safe)
        require(info.id.isNotBlank()) { "NewPipe did not return a video ID" }
        VideoEntry(
            id = "newpipe:${info.id}",
            url = safe,
            title = info.name,
            durationSeconds = info.duration.toInt().coerceAtLeast(0)
        )
    }
}
