package com.tikitoki.downloader.data

import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.tikitoki.downloader.domain.MetadataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.URI
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Independent metadata extractor for supported NewPipe services.
 * Does not provide access to private, DRM or subscriber-only media.
 */
class NewPipeSource {
    private val client = OkHttpClient.Builder()
        .dns(SafePublicDns())
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val http = object : Downloader() {
        override fun execute(request: Request): Response {
            val url = URI(request.url())
            require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null) {
                "Only HTTPS media metadata endpoints are permitted"
            }
            requireSafeEndpointHost(url.host)
            val builder = okhttp3.Request.Builder().url(request.url())
            request.headers().forEach { (key, values) ->
                if (!key.equals("host", true) && !key.equals("cookie", true) && !key.equals("content-length", true))
                    values.forEach { builder.addHeader(key, it) }
            }
            val method = request.httpMethod()
            val data = request.dataToSend()
            val body = if (data != null) data.toRequestBody()
                else if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody()
                else null
            return client.newCall(builder.method(method, body).build()).execute().use { response ->
                val bytes = response.body?.byteStream()?.use { it.readNBytes(4 * 1024 * 1024 + 1) } ?: byteArrayOf()
                require(bytes.size <= 4 * 1024 * 1024) { "Extractor response too large" }
                Response(response.code, response.message, response.headers.toMultimap(),
                    bytes.toString(Charsets.UTF_8), response.request.url.toString())
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
            title = MetadataSanitizer.title(info.name),
            durationSeconds = info.duration.toInt().coerceAtLeast(0)
        )
    }
}


