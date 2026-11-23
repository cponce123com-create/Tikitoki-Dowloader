package com.tikitoki.downloader.data

import android.content.Context
import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.MetadataSanitizer
import com.tikitoki.downloader.domain.TikTokLinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Extracts and downloads public TikTok photo-post images without bundling a second extractor runtime. */
class TikTokPhotoSource(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()

    suspend fun inspect(photoPageUrl: String): List<VideoEntry> =
        withContext(Dispatchers.IO) {
            val safePage = DownloadInputPolicy.canonicalUrl(photoPageUrl)
            require(isTikTokPageUrl(safePage)) { "Pega un enlace de TikTok." }
            val page = fetchPage(safePage)
            require(isPhotoPageUrl(page.url)) { "Pega el enlace de una publicación TikTok /photo/." }
            parseTikTokPhotoGallery(page.html, page.url)
        }

    suspend fun inspectIfPhoto(shortLinkUrl: String): List<VideoEntry>? =
        withContext(Dispatchers.IO) {
            val safePage = DownloadInputPolicy.canonicalUrl(shortLinkUrl)
            require(isTikTokPageUrl(safePage)) { "Pega un enlace de TikTok." }
            val page = fetchPage(safePage)
            if (!isPhotoPageUrl(page.url)) return@withContext null
            parseTikTokPhotoGallery(page.html, page.url)
        }

    suspend fun downloadToCache(
        imageUrl: String,
        downloadId: String,
        onProgress: (Int) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            val safeUrl = canonicalImageUrl(imageUrl)
            val safeId = downloadId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            require(safeId.isNotBlank()) { "ID de foto inválido." }

            val folder = safeTemporaryDirectory(appContext.cacheDir)
            if (!folder.exists() && !folder.mkdirs()) throw IllegalStateException("No se pudo crear la carpeta temporal.")
            folder
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.startsWith("$safeId.") }
                .forEach { it.delete() }

            val temporary = File(folder, "$safeId.photo.part")
            val connection = openImageConnection(safeUrl, downloadId)
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw IllegalStateException("El CDN de TikTok respondió HTTP $status.")
                val contentType =
                    connection
                        .getHeaderField("Content-Type")
                        ?.substringBefore(';')
                        ?.trim()
                        ?.lowercase(Locale.ROOT)
                require(contentType == null || contentType.startsWith("image/") || contentType == "application/octet-stream") {
                    "TikTok no devolvió un archivo de imagen."
                }
                val expectedBytes = connection.contentLengthLong
                require(expectedBytes <= MAX_IMAGE_BYTES) { "La imagen supera el límite de tamaño permitido." }

                var received = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(temporary, false).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            require(received <= MAX_IMAGE_BYTES) { "La imagen supera el límite de tamaño permitido." }
                            output.write(buffer, 0, count)
                            if (expectedBytes > 0L) onProgress((received * 100 / expectedBytes).toInt().coerceIn(0, 99))
                        }
                        output.fd.sync()
                    }
                }
                require(received > 0L) { "TikTok devolvió una imagen vacía." }

                val extension = imageExtension(temporary)
                val destination = File(folder, "$safeId.$extension")
                require(temporary.renameTo(destination)) { "No se pudo finalizar el archivo de imagen." }
                onProgress(100)
                destination
            } catch (error: Exception) {
                temporary.delete()
                throw error
            } finally {
                activeConnections.remove(downloadId, connection)
                connection.disconnect()
            }
        }

    fun cancel(downloadId: String) {
        activeConnections.remove(downloadId)?.disconnect()
    }

    private fun fetchPage(startUrl: String): FetchedPage {
        var current = startUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            require(isTikTokPageUrl(current)) { "La redirección salió de TikTok." }
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "es,en-US;q=0.8,en;q=0.6")
            try {
                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    require(redirectCount < MAX_REDIRECTS) { "TikTok redirigió demasiadas veces." }
                    val location =
                        connection.getHeaderField("Location")
                            ?: throw IllegalStateException("TikTok devolvió una redirección inválida.")
                    current = URL(URL(current), location).toExternalForm()
                    require(isTikTokPageUrl(current)) { "La redirección salió de TikTok." }
                } else {
                    require(status in 200..299) {
                        if (status == 403 || status == 429) {
                            "TikTok limitó el acceso a esta publicación. Espera unos minutos y vuelve a intentar."
                        } else {
                            "No se pudo abrir la publicación de fotos (HTTP $status)."
                        }
                    }
                    val type = connection.getHeaderField("Content-Type")?.lowercase(Locale.ROOT).orEmpty()
                    require(type.isBlank() || type.contains("text/html") || type.contains("application/json")) {
                        "TikTok no devolvió la página de la publicación."
                    }
                    val declaredLength = connection.contentLengthLong
                    require(declaredLength <= MAX_PAGE_BYTES) { "La página de TikTok supera el tamaño permitido." }
                    val html = connection.inputStream.use { readBounded(it, MAX_PAGE_BYTES).toString(Charsets.UTF_8) }
                    return FetchedPage(html, current)
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("No se pudo resolver la publicación de TikTok.")
    }

    private fun openImageConnection(
        startUrl: String,
        downloadId: String,
    ): HttpURLConnection {
        var current = startUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            canonicalImageUrl(current)
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            activeConnections[downloadId] = connection
            val status =
                try {
                    connection.responseCode
                } catch (error: Exception) {
                    activeConnections.remove(downloadId, connection)
                    connection.disconnect()
                    throw error
                }
            if (status in REDIRECT_CODES) {
                try {
                    require(redirectCount < MAX_REDIRECTS) { "El CDN redirigió demasiadas veces." }
                    val location =
                        connection.getHeaderField("Location")
                            ?: throw IllegalStateException("El CDN devolvió una redirección inválida.")
                    current = URL(URL(current), location).toExternalForm()
                    canonicalImageUrl(current)
                } finally {
                    activeConnections.remove(downloadId, connection)
                    connection.disconnect()
                }
            } else {
                return connection
            }
        }
        throw IllegalStateException("No se pudo resolver la imagen de TikTok.")
    }

    private fun imageExtension(file: File): String {
        val header = ByteArray(12)
        val count =
            FileInputStream(file).use { input ->
                var offset = 0
                while (offset < header.size) {
                    val read = input.read(header, offset, header.size - offset)
                    if (read < 0) break
                    offset += read
                }
                offset
            }
        return detectImageExtension(header.copyOf(count))
    }

    private data class FetchedPage(
        val html: String,
        val url: String,
    )

    companion object {
        const val PHOTO_ENTRY_PREFIX = "tiktok-photo-"
        const val PHOTO_FORMAT_ID = "tiktok-photo-original"

        private const val MAX_REDIRECTS = 5
        private const val MAX_PAGE_BYTES = 8 * 1024 * 1024L
        private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024L
        private const val COPY_BUFFER_SIZE = 64 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        private val embeddedJson =
            Regex(
                "<script\\b[^>]*\\bid=[\"'](?:__UNIVERSAL_DATA_FOR_REHYDRATION__|SIGI_STATE)[\"'][^>]*>(.*?)</script\\s*>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )

        fun isPhotoEntry(video: VideoEntry): Boolean = video.id.startsWith(PHOTO_ENTRY_PREFIX)

        fun isPhotoFormat(formatId: String): Boolean = formatId == PHOTO_FORMAT_ID

        internal fun detectImageExtension(header: ByteArray): String {
            require(header.size >= 8) { "El archivo recibido no es una imagen compatible." }
            return when {
                header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte() -> "jpg"
                header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> "png"
                header.size >= 12 &&
                    String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
                else -> throw IllegalStateException("TikTok devolvió un formato de imagen no compatible.")
            }
        }

        fun isPhotoPageUrl(raw: String): Boolean = (TikTokLinkParser.parse(raw) as? LinkParseResult.Success)?.link?.isPhoto == true

        fun canonicalImageUrl(raw: String): String {
            val uri =
                runCatching { URI(raw) }.getOrNull()
                    ?: throw IllegalArgumentException("La URL de imagen no es válida.")
            val host =
                uri.host
                    ?.lowercase(Locale.ROOT)
                    ?.trimEnd('.')
                    .orEmpty()
            require(
                uri.scheme.equals("https", ignoreCase = true) &&
                    uri.userInfo == null &&
                    uri.port == -1 &&
                    isAllowedImageHost(host),
            ) { "La URL de imagen no pertenece a un CDN autorizado de TikTok." }
            return uri.toASCIIString()
        }

        internal fun parseTikTokPhotoGallery(
            html: String,
            photoPageUrl: String,
        ): List<VideoEntry> {
            require(html.length <= MAX_PAGE_BYTES) { "La página de TikTok supera el tamaño permitido." }
            val link =
                (TikTokLinkParser.parse(photoPageUrl) as? LinkParseResult.Success)?.link
                    ?: throw IllegalArgumentException("El enlace de la publicación no es válido.")
            val postId = link.videoId
            require(link.isPhoto && !postId.isNullOrBlank()) { "Pega el enlace de una publicación TikTok /photo/." }

            val json =
                embeddedJson.findAll(html).firstNotNullOfOrNull { match ->
                    runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
                }
                    ?: throw IllegalStateException(
                        "TikTok no devolvió los datos del carrusel. La publicación puede ser privada o requerir acceso.",
                    )
            val post =
                findPhotoPost(json, postId!!)
                    ?: throw IllegalStateException("No se encontró el carrusel en la respuesta de TikTok.")
            val images =
                post.optJSONObject("imagePost")?.optJSONArray("images")
                    ?: throw IllegalStateException("El enlace no corresponde a una publicación de fotos.")
            require(images.length() in 1..MAX_PHOTOS_PER_POST) { "El carrusel no contiene una cantidad de fotos compatible." }

            val baseTitle = MetadataSanitizer.title(post.optString("desc").ifBlank { "TikTok $postId" })
            val author = post.optJSONObject("author")?.optString("uniqueId")?.takeIf { it.isNotBlank() }
            return buildList {
                for (index in 0 until images.length()) {
                    val image = images.optJSONObject(index) ?: continue
                    val urls = image.optJSONObject("imageURL")?.optJSONArray("urlList") ?: continue
                    val imageUrl =
                        (0 until urls.length())
                            .mapNotNull { position -> urls.optString(position).takeIf { it.isNotBlank() } }
                            .firstNotNullOfOrNull { candidate -> runCatching { canonicalImageUrl(candidate) }.getOrNull() }
                            ?: continue
                    val number = index + 1
                    add(
                        VideoEntry(
                            id = "$PHOTO_ENTRY_PREFIX$postId-$number",
                            url = link.url,
                            title = MetadataSanitizer.title("$baseTitle · foto ${number.toString().padStart(2, '0')}"),
                            thumbnail = imageUrl,
                            uploader = author,
                        ),
                    )
                }
            }.also { entries ->
                require(entries.isNotEmpty()) { "TikTok no devolvió fotos descargables para esta publicación." }
            }
        }

        private fun findPhotoPost(
            root: JSONObject,
            expectedId: String,
        ): JSONObject? {
            val pending = ArrayDeque<Any>()
            pending.add(root)
            var visited = 0
            while (pending.isNotEmpty() && visited < MAX_JSON_NODES) {
                when (val node = pending.removeLast()) {
                    is JSONObject -> {
                        visited++
                        if (node.optString("id") == expectedId && node.optJSONObject("imagePost") != null) return node
                        val keys = node.keys()
                        while (keys.hasNext()) {
                            when (val child = node.opt(keys.next())) {
                                is JSONObject, is JSONArray -> pending.add(child)
                            }
                        }
                    }
                    is JSONArray -> {
                        visited++
                        for (index in 0 until node.length()) {
                            when (val child = node.opt(index)) {
                                is JSONObject, is JSONArray -> pending.add(child)
                            }
                        }
                    }
                }
            }
            return null
        }

        private fun isAllowedImageHost(host: String): Boolean =
            listOf("tiktokcdn.com", "tiktokcdn-us.com", "tiktokcdn-eu.com", "ibytedtos.com", "byteoversea.com")
                .any { host == it || host.endsWith(".$it") }

        private fun isTikTokPageUrl(raw: String): Boolean =
            runCatching {
                val uri = URI(raw)
                val host =
                    uri.host
                        ?.lowercase(Locale.ROOT)
                        ?.trimEnd('.')
                        .orEmpty()
                uri.scheme.equals("https", ignoreCase = true) &&
                    uri.userInfo == null &&
                    uri.port == -1 &&
                    (host == "tiktok.com" || host.endsWith(".tiktok.com"))
            }.getOrDefault(false)

        private fun readBounded(
            input: java.io.InputStream,
            limit: Long,
        ): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "La página de TikTok supera el tamaño permitido." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        private const val MAX_PHOTOS_PER_POST = 100
        private const val MAX_JSON_NODES = 100_000
    }
}
